package com.segurancarural.gpstracker.util

import com.segurancarural.gpstracker.data.model.TelemetryRecord
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GpsLocationFilterTest {

    private fun createDummyRecord(
        lat: Double = 38.71667,
        lng: Double = -9.13333,
        accuracy: Float = 5.0f,
        speed: Float = 0.0f,
        emergencyState: Boolean = false,
        timestampMs: Long = 1000000L
    ): TelemetryRecord {
        return TelemetryRecord(
            id = 0,
            serialNumber = "test-device",
            deviceLabel = "Test Device",
            timestamp = "2026-07-25T16:00:00Z",
            batteryLevel = 100,
            batteryCharging = false,
            lat = lat,
            lng = lng,
            accuracy = accuracy,
            speed = speed,
            heading = 0f,
            emergencyState = emergencyState,
            trackingEnabled = true,
            networkType = "WIFI",
            appVersion = "1.0.0",
            createdAtEpochMs = timestampMs,
            syncState = 0
        )
    }

    @Test
    fun testFirstFixIsAccepted() {
        val filter = GpsLocationFilter()
        val record = createDummyRecord()
        val result = filter.process(record)

        assertTrue(result is FilterResult.Accept)
        val accepted = result as FilterResult.Accept
        assertEquals(record.lat, accepted.record.lat)
        assertEquals(record.lng, accepted.record.lng)
    }

    @Test
    fun testEmergencyBypassesFilter() {
        val filter = GpsLocationFilter()
        val record = createDummyRecord(lat = 40.0, lng = -8.0, accuracy = 500f, emergencyState = true)
        val result = filter.process(record)

        assertTrue(result is FilterResult.Accept)
    }

    @Test
    fun testRedundantStationaryJitterIsDiscarded() {
        val filter = GpsLocationFilter()
        val first = createDummyRecord(lat = 38.71667, lng = -9.13333, timestampMs = 1000L)
        filter.process(first)

        // Point only 1 meter away while stationary
        val second = createDummyRecord(lat = 38.71668, lng = -9.13333, timestampMs = 5000L)
        val result = filter.process(second)

        assertTrue(result is FilterResult.DiscardRedundant)
    }

    @Test
    fun testWiFiBounceTriggersRapidRecheckWhenStationary() {
        val filter = GpsLocationFilter()
        // Lock stationary anchor with initial points over time
        val p1 = createDummyRecord(lat = 38.71667, lng = -9.13333, timestampMs = 1000L)
        filter.process(p1)

        val p2 = createDummyRecord(lat = 38.71667, lng = -9.13333, timestampMs = 65000L)
        filter.process(p2)

        // Wi-Fi bounce 100 meters away with poor accuracy (35m) while stationary
        val bouncePoint = createDummyRecord(lat = 38.71750, lng = -9.13333, accuracy = 35.0f, timestampMs = 70000L)
        val result = filter.process(bouncePoint)

        assertTrue(result is FilterResult.SuspiciousJumpRecheck)
    }
}
