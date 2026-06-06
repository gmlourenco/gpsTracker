package com.segurancarural.gpstracker.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import kotlin.math.sqrt

/**
 * AccidentDetector - Monitors acceleration using TYPE_ACCELEROMETER.
 * Triggers callback if acceleration magnitude exceeds the configured threshold.
 */
class AccidentDetector(
    context: Context,
    private val sensitivity: String,
    private val onAccidentDetected: () -> Unit
) : SensorEventListener {

    companion object {
        private const val TAG = "AccidentDetector"
        private const val DEBOUNCE_MS = 5000L // Prevent duplicate rapid triggers
        private const val REQUIRED_CONSECUTIVE_SAMPLES = 2
    }

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private var isListening = false
    private var lastTriggerTime = 0L
    private var consecutiveOverThresholdCount = 0

    // Map sensitivity to G-force threshold in m/s^2 (G * 9.8)
    // Increased thresholds to avoid false positives on daily activities,
    // combined with a consecutive samples filter. Supports custom_XX values.
    private val threshold: Float = when {
        sensitivity.startsWith("custom_", ignoreCase = true) -> {
            val gVal = sensitivity.substring("custom_".length).toIntOrNull() ?: 7
            gVal.coerceIn(1, 99) * 9.8f
        }
        sensitivity.lowercase() == "high" -> 49.0f
        sensitivity.lowercase() == "low" -> 98.0f
        else -> 73.5f
    }

    fun start() {
        if (accelerometer == null) {
            Log.e(TAG, "Accelerometer not available on this device!")
            return
        }
        if (!isListening) {
            // SENSOR_DELAY_GAME (~20ms sampling interval) is used to ensure we capture the transient
            // peak deceleration of a physical impact, which lasts only a few milliseconds.
            // SENSOR_DELAY_NORMAL (~200ms) would miss these fast spikes entirely.
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME)
            isListening = true
            consecutiveOverThresholdCount = 0
            Log.i(TAG, "Accident sensor started with sensitivity: $sensitivity (threshold: ${threshold} m/s²)")
        }
    }

    fun stop() {
        if (isListening) {
            sensorManager.unregisterListener(this)
            isListening = false
            consecutiveOverThresholdCount = 0
            Log.i(TAG, "Accident sensor stopped")
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || event.sensor.type != Sensor.TYPE_ACCELEROMETER) return

        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]

        // Calculate acceleration vector magnitude M = sqrt(x^2 + y^2 + z^2)
        val magnitude = sqrt((x * x + y * y + z * z).toDouble()).toFloat()

        if (magnitude > threshold) {
            consecutiveOverThresholdCount++
            if (consecutiveOverThresholdCount >= REQUIRED_CONSECUTIVE_SAMPLES) {
                val now = System.currentTimeMillis()
                if (now - lastTriggerTime > DEBOUNCE_MS) {
                    lastTriggerTime = now
                    Log.w(TAG, "🚨 CRITICAL IMPACT DETECTED! Magnitude: $magnitude m/s² (threshold: $threshold) sustained for $consecutiveOverThresholdCount samples")
                    consecutiveOverThresholdCount = 0
                    onAccidentDetected()
                }
            }
        } else {
            consecutiveOverThresholdCount = 0
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // No-op
    }
}
