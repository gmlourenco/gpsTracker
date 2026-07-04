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
    private val onAccidentDetected: (isRollover: Boolean) -> Unit
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
    private var lastGyroTimestamp: Long = 0L
    private var totalRotationRad = 0f
    
    // Speed threshold state
    private var lastLowSpeedTime: Long? = null
    private val speedThresholdMps = 0.55f // approx 2.0 km/h
    private val speedDelayMs = 30000L
    private var isPausedDueToLowSpeed = false

    // Low-Pass Filter variables
    private val alpha = 0.8f
    private val gravity = FloatArray(3)

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
        if (sensitivity.lowercase() == "off") {
            Log.i(TAG, "Sensor de acidente explicitamente desligado nas configurações.")
            return
        }
        if (accelerometer == null) {
            Log.e(TAG, "Accelerometer not available on this device!")
            return
        }
        if (!isListening) {
            handlerThread = HandlerThread("AccidentSensor").apply { start() }
            handler = Handler(handlerThread!!.looper)
            
            isListening = true
            isPausedDueToLowSpeed = false
            lastLowSpeedTime = null
            resumeUpdates()
            
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
            isPausedDueToLowSpeed = false
            consecutiveOverThresholdCount = 0
            gravity.fill(0f)
            Log.i(TAG, "Accident sensor stopped")
        }
    }
    
    private fun pauseUpdates() {
        if (isListening) {
            sensorManager.unregisterListener(this)
            Log.i(TAG, "Paused motion updates due to low speed")
        }
    }

    private fun resumeUpdates() {
        if (isListening && !isPausedDueToLowSpeed) {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL, handler)
            gyroscope?.let {
                sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL, handler)
            }
        }
    }
    
    fun updateSpeed(speedMps: Float) {
        handler?.post {
            if (speedMps < speedThresholdMps) {
                if (lastLowSpeedTime == null) {
                    lastLowSpeedTime = System.currentTimeMillis()
                } else if (System.currentTimeMillis() - lastLowSpeedTime!! >= speedDelayMs) {
                    if (!isPausedDueToLowSpeed) {
                        isPausedDueToLowSpeed = true
                        pauseUpdates()
                    }
                }
            } else {
                lastLowSpeedTime = null
                if (isPausedDueToLowSpeed) {
                    isPausedDueToLowSpeed = false
                    Log.i(TAG, "Resuming motion updates due to increased speed")
                    resumeUpdates()
                }
            }
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
        // Isolate the force of gravity with the low-pass filter to ignore tractor engine vibration
        gravity[0] = alpha * gravity[0] + (1 - alpha) * event.values[0]
        gravity[1] = alpha * gravity[1] + (1 - alpha) * event.values[1]
        gravity[2] = alpha * gravity[2] + (1 - alpha) * event.values[2]

        // Remove the gravity contribution with the high-pass filter
        val x = event.values[0] - gravity[0]
        val y = event.values[1] - gravity[1]
        val z = event.values[2] - gravity[2]

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
        // Accumulate rotation using actual elapsed time
        if (lastGyroTimestamp > 0L) {
            val dtSeconds = (event.timestamp - lastGyroTimestamp) / 1_000_000_000f
            totalRotationRad += lastGyroMagnitude * dtSeconds
        }
        lastGyroTimestamp = event.timestamp
    }

    private fun onImpactConfirmed() {
        // Wait and check for post-impact rollover or stillness
        handler?.postDelayed({
            val rolloverAngleDeg = Math.toDegrees(totalRotationRad.toDouble())
            val isRollover = rolloverAngleDeg > 45
            
            Log.w(TAG, "Post-impact analysis: Rollover detected? $isRollover (Angle: $rolloverAngleDeg)")
            onAccidentDetected(isRollover)
            
            totalRotationRad = 0f // Reset
            lastGyroTimestamp = 0L
        }, POST_IMPACT_ANALYSIS_MS)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // No-op
    }
}
