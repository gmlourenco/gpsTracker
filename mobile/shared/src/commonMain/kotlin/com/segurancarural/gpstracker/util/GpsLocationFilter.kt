package com.segurancarural.gpstracker.util

import com.segurancarural.gpstracker.data.model.TelemetryRecord
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

sealed class FilterResult {
    /** Location accepted and smoothed using 2D Kalman filter. */
    data class Accept(val record: TelemetryRecord) : FilterResult()

    /** Redundant location discarded because it's virtually identical to the rolling average centroid while stationary. */
    object DiscardRedundant : FilterResult()

    /** Suspicious jump detected while stationary (e.g. Wi-Fi bounce down the street). Native caller should request a rapid re-check fix. */
    data class SuspiciousJumpRecheck(val originalRecord: TelemetryRecord) : FilterResult()
}

/**
 * GpsLocationFilter — KMP location filter pipeline providing:
 *  1. Stationary Anchor & Wi-Fi Bounce Protection (rejects indoor Wi-Fi/Cell jumps down the street).
 *  2. Immediate Rapid Re-check trigger for suspicious location spikes.
 *  3. Rolling average centroid filter for redundant stationary jitter (< 5m).
 *  4. 2D Kalman Filter & Position Smoothing for active tracks.
 */
class GpsLocationFilter(
    private val minStationaryJitterRadiusM: Double = 5.0,
    private val maxSpeedKmh: Float = 150.0f,
    private val lowAccuracyThresholdM: Float = 20.0f
) {
    private var kalmanLat: Double = 0.0
    private var kalmanLng: Double = 0.0
    private var kalmanVariance: Double = -1.0 // -1 means uninitialized

    private var stationaryAnchorLat: Double = 0.0
    private var stationaryAnchorLng: Double = 0.0
    private var isStationaryLocked: Boolean = false
    private var lastStationaryCheckMs: Long = 0L
    private var lastProcessedTimeMs: Long = 0L

    private val recentLocations = mutableListOf<Pair<Double, Double>>()
    private val maxRecentSize = 5

    /** Resets filter state (e.g. when tracking starts/restarts or SOS toggles). */
    fun reset() {
        kalmanVariance = -1.0
        isStationaryLocked = false
        recentLocations.clear()
        lastProcessedTimeMs = 0L
    }

    fun process(record: TelemetryRecord): FilterResult {
        // SOS emergency mode bypasses filtering to ensure every emergency point is preserved
        if (record.emergencyState) {
            updateKalman(record.lat, record.lng, record.accuracy.toDouble())
            return FilterResult.Accept(record)
        }

        val now = record.createdAtEpochMs

        // 1. Initialize Kalman filter on first fix
        if (kalmanVariance < 0) {
            kalmanLat = record.lat
            kalmanLng = record.lng
            kalmanVariance = (record.accuracy * record.accuracy).toDouble()

            stationaryAnchorLat = record.lat
            stationaryAnchorLng = record.lng
            lastStationaryCheckMs = now
            lastProcessedTimeMs = now

            recentLocations.add(Pair(record.lat, record.lng))
            return FilterResult.Accept(record)
        }

        // Distance from previous Kalman state
        val distFromKalman = haversineDistanceMeters(kalmanLat, kalmanLng, record.lat, record.lng)

        // 2. Stationary Anchor Lock check
        // If speed < 2 km/h or small movements over 60 seconds, lock stationary anchor
        if (!isStationaryLocked) {
            if (distFromKalman < 10.0 && (now - lastStationaryCheckMs) > 60_000L) {
                isStationaryLocked = true
                stationaryAnchorLat = kalmanLat
                stationaryAnchorLng = kalmanLng
            }
        }

        // 3. Wi-Fi / Cell Bounce Spike Rejection while Stationary
        if (isStationaryLocked) {
            val distFromAnchor = haversineDistanceMeters(stationaryAnchorLat, stationaryAnchorLng, record.lat, record.lng)
            val timeSinceLastProcessed = if (lastProcessedTimeMs > 0) now - lastProcessedTimeMs else 0L

            // If user has moved cleanly away for > 30m with good accuracy AND actual movement speed (> 3 km/h), unlock anchor
            // OR if there's a massive jump (>300m) and we haven't seen a fix in a long time (app was suspended while traveling)
            val isValidMovement = (distFromAnchor > 30.0 && record.accuracy <= lowAccuracyThresholdM && record.speed >= 3.0f)
            val isSuspendedTravel = (distFromAnchor > 300.0 && timeSinceLastProcessed > 120_000L)

            if (isValidMovement || isSuspendedTravel) {
                isStationaryLocked = false
                stationaryAnchorLat = record.lat
                stationaryAnchorLng = record.lng
                lastStationaryCheckMs = now
                
                // Snap Kalman filter to the new location to prevent getting stuck
                kalmanLat = record.lat
                kalmanLng = record.lng
                kalmanVariance = (record.accuracy * record.accuracy).toDouble()
            } else {
                // If it's still locked, and we have a jump > 25m, it's a bounce/glitch.
                if (distFromAnchor > 25.0 && (record.accuracy > lowAccuracyThresholdM || record.speed < 3.0f)) {
                    return FilterResult.SuspiciousJumpRecheck(record)
                }
            }
        }

        // 4. Rolling Average Centroid & Redundant Stationary Jitter Filter
        val (avgLat, avgLng) = calculateRollingAverage(record.lat, record.lng)
        val distFromCentroid = haversineDistanceMeters(avgLat, avgLng, record.lat, record.lng)

        val timeSinceLastAccepted = if (lastProcessedTimeMs > 0) now - lastProcessedTimeMs else 0L
        val forceAccept = timeSinceLastAccepted > 120_000L // Force accept after 2 min silence
        if (!forceAccept && distFromCentroid < minStationaryJitterRadiusM && (record.speed < 1.0f || isStationaryLocked)) {
            // Smooth internal state without outputting duplicate stationary points
            updateKalman(record.lat, record.lng, record.accuracy.toDouble())
            return FilterResult.DiscardRedundant
        }

        // 5. Extreme Speed Glitch Guard (v > 150 km/h)
        val timeDiffSeconds = maxOf(1.0, (now - lastProcessedTimeMs) / 1000.0)
        val calculatedSpeedKmh = (distFromKalman / timeDiffSeconds) * 3.6
        if (calculatedSpeedKmh > maxSpeedKmh && distFromKalman > 50.0) {
            return FilterResult.SuspiciousJumpRecheck(record)
        }

        // Snap Kalman if it's lagging too far behind (e.g., after a tunnel or long suspension)
        if (distFromKalman > 100.0 || timeDiffSeconds > 60.0) {
            kalmanLat = record.lat
            kalmanLng = record.lng
            kalmanVariance = (record.accuracy * record.accuracy).toDouble()
        }

        // 6. Apply 2D Kalman Filter Smoothing
        updateKalman(record.lat, record.lng, record.accuracy.toDouble())
        lastProcessedTimeMs = now

        // Add to rolling window
        recentLocations.add(Pair(record.lat, record.lng))
        if (recentLocations.size > maxRecentSize) {
            recentLocations.removeAt(0)
        }

        val smoothedRecord = record.copy(
            lat = kalmanLat,
            lng = kalmanLng
        )

        return FilterResult.Accept(smoothedRecord)
    }

    private fun updateKalman(lat: Double, lng: Double, accuracy: Double) {
        val minAccuracy = maxOf(accuracy, 3.0)
        val processNoise = 1.0
        kalmanVariance += processNoise

        val kalmanGain = kalmanVariance / (kalmanVariance + minAccuracy * minAccuracy)
        kalmanLat += kalmanGain * (lat - kalmanLat)
        kalmanLng += kalmanGain * (lng - kalmanLng)
        kalmanVariance *= (1.0 - kalmanGain)
    }

    private fun calculateRollingAverage(lat: Double, lng: Double): Pair<Double, Double> {
        if (recentLocations.isEmpty()) return Pair(lat, lng)
        var sumLat = lat
        var sumLng = lng
        for (loc in recentLocations) {
            sumLat += loc.first
            sumLng += loc.second
        }
        val count = recentLocations.size + 1
        return Pair(sumLat / count, sumLng / count)
    }

    companion object {
        fun haversineDistanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val r = 6371000.0
            val dLat = (lat2 - lat1) * (PI / 180.0)
            val dLon = (lon2 - lon1) * (PI / 180.0)
            val a = sin(dLat / 2) * sin(dLat / 2) +
                    cos(lat1 * (PI / 180.0)) * cos(lat2 * (PI / 180.0)) *
                    sin(dLon / 2) * sin(dLon / 2)
            val c = 2 * atan2(sqrt(a), sqrt(1 - a))
            return r * c
        }
    }
}
