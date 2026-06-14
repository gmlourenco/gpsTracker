package com.segurancarural.gpstracker.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import kotlin.math.pow
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
        private const val REQUIRED_CONSECUTIVE_SAMPLES = 3 // Increased for stability
        private const val POST_IMPACT_ANALYSIS_MS = 2000L
    }

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val gyroscope: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    
    private var isListening = false
    private var lastTriggerTime = 0L
    private var consecutiveOverThresholdCount = 0

    // Background thread for sensor processing
    private var handlerThread: HandlerThread? = null
    private var handler: Handler? = null
    
    // Gyroscope integration for rollover
    private var lastGyroMagnitude = 0f
    private var totalRotationRad = 0f

    // Map sensitivity to G-force threshold in m/s^2 (G * 9.8)
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
            handlerThread = HandlerThread("AccidentSensor").apply { start() }
            handler = Handler(handlerThread!!.looper)
            
            // SENSOR_DELAY_GAME (~20ms sampling interval)
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME, handler)
            gyroscope?.let {
                sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME, handler)
            }
            isListening = true
            consecutiveOverThresholdCount = 0
            Log.i(TAG, "Accident sensor started with sensitivity: $sensitivity (threshold: ${threshold} m/s²)")
        }
    }

    fun stop() {
        if (isListening) {
            sensorManager.unregisterListener(this)
            handlerThread?.quitSafely()
            handlerThread = null
            handler = null
            isListening = false
            consecutiveOverThresholdCount = 0
            Log.i(TAG, "Accident sensor stopped")
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> processAccelerometer(event)
            Sensor.TYPE_GYROSCOPE -> processGyroscope(event)
        }
    }

    private fun processAccelerometer(event: SensorEvent) {
        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]

        val magnitude = sqrt((x * x + y * y + z * z).toDouble()).toFloat()

        if (magnitude > threshold) {
            consecutiveOverThresholdCount++
            if (consecutiveOverThresholdCount >= REQUIRED_CONSECUTIVE_SAMPLES) {
                val now = System.currentTimeMillis()
                if (now - lastTriggerTime > DEBOUNCE_MS) {
                    lastTriggerTime = now
                    Log.w(TAG, "🚨 CRITICAL IMPACT DETECTED! Magnitude: $magnitude m/s² sustained for $consecutiveOverThresholdCount samples")
                    consecutiveOverThresholdCount = 0
                    onImpactConfirmed()
                }
            }
        } else {
            consecutiveOverThresholdCount = 0
        }
    }

    private fun processGyroscope(event: SensorEvent) {
        lastGyroMagnitude = sqrt(
            (event.values[0].pow(2) + event.values[1].pow(2) + event.values[2].pow(2)).toDouble()
        ).toFloat()
        // Accumulate rotation (~50Hz sampling implies ~0.02s dt)
        totalRotationRad += lastGyroMagnitude * 0.02f
    }

    private fun onImpactConfirmed() {
        // Wait and check for post-impact rollover or stillness
        handler?.postDelayed({
            val rolloverAngleDeg = Math.toDegrees(totalRotationRad.toDouble())
            val isRollover = rolloverAngleDeg > 45
            
            Log.w(TAG, "Post-impact analysis: Rollover detected? $isRollover (Angle: $rolloverAngleDeg)")
            onAccidentDetected()
            
            totalRotationRad = 0f // Reset
        }, POST_IMPACT_ANALYSIS_MS)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // No-op
    }
}
