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
    }

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private var isListening = false
    private var lastTriggerTime = 0L

    // Map sensitivity to G-force threshold in m/s^2 (G * 9.8)
    private val threshold: Float = when (sensitivity.lowercase()) {
        "high" -> 29.4f    // ~3G (High sensitivity - triggers easily on bumps/falls)
        "low" -> 49.0f     // ~5G (Low sensitivity - triggers only on major high-energy crashes)
        else -> 39.2f      // ~4G (Medium sensitivity - standard crash threshold)
    }

    fun start() {
        if (accelerometer == null) {
            Log.e(TAG, "Accelerometer not available on this device!")
            return
        }
        if (!isListening) {
            // SENSOR_DELAY_NORMAL is extremely battery friendly and has a sampling rate of ~200ms,
            // which is perfectly fine for background impact detection.
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL)
            isListening = true
            Log.i(TAG, "Accident sensor started with sensitivity: $sensitivity (threshold: ${threshold} m/s²)")
        }
    }

    fun stop() {
        if (isListening) {
            sensorManager.unregisterListener(this)
            isListening = false
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
            val now = System.currentTimeMillis()
            if (now - lastTriggerTime > DEBOUNCE_MS) {
                lastTriggerTime = now
                Log.w(TAG, "🚨 CRITICAL IMPACT DETECTED! Magnitude: $magnitude m/s² (threshold: $threshold)")
                onAccidentDetected()
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // No-op
    }
}
