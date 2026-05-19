package com.seguranca.rural.service

import com.seguranca.rural.BuildConfig
import com.seguranca.rural.receiver.HeartbeatReceiver
import com.seguranca.rural.ui.activities.MainActivity
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.seguranca.rural.data.repository.TelemetryRepository
import com.seguranca.rural.data.repository.TrackingStateRepository
import com.seguranca.rural.domain.usecase.SubmitLocationUseCase
import com.seguranca.rural.data.model.TelemetryRecord
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import android.provider.Settings
import com.seguranca.rural.data.db.createAppDatabase
import java.time.Instant
import java.util.UUID

private const val TAG = "LocationFgService"
private const val NOTIFICATION_CHANNEL_ID = "gps_tracking_channel"
private const val NOTIFICATION_ID = 1001

/**
 * LocationForegroundService — persistent GPS tracking service.
 *
 * Runs as a Foreground Service to survive Android's low-memory killer.
 * Uses [FusedLocationProviderClient] with adaptive sampling intervals:
 *
 *   STATIC mode:    45–60 min intervals (device not moving, speed < 1 km/h)
 *   MOVING mode:    5–15 min intervals  (speed > 3 km/h)
 *   EMERGENCY mode: 15s continuous pure GPS (SOS activated)
 *
 * Every GPS fix is stored in the local Room database (offline queue).
 * The [SyncWorker] is responsible for flushing the queue when online.
 *
 * Start/stop the service from [MainActivity] or [BootReceiver].
 * Toggle SOS mode via [ACTION_SOS_ACTIVATE] / [ACTION_SOS_DEACTIVATE] intents.
 */
class LocationForegroundService : Service() {

    companion object {
        const val ACTION_START = "com.seguranca.rural.action.START_TRACKING"
        const val ACTION_STOP = "com.seguranca.rural.action.STOP_TRACKING"
        const val ACTION_SOS_ACTIVATE = "com.seguranca.rural.action.SOS_ACTIVATE"
        const val ACTION_SOS_DEACTIVATE = "com.seguranca.rural.action.SOS_DEACTIVATE"
        const val ACTION_HEARTBEAT = "com.seguranca.rural.action.HEARTBEAT"
        const val ACTION_RELOAD_CONFIG = "com.seguranca.rural.action.RELOAD_CONFIG"



        // Accuracy filter: discard fixes worse than this unless no better fix arrives
        private const val ACCURACY_THRESHOLD_METERS = 250f
        private const val ACCURACY_REPLACE_WINDOW_MS = 10_000L

        /** Suppress duplicate submissions when getCurrentLocation and the callback fire together. */
        private const val DUPLICATE_FIX_DEBOUNCE_MS = 3_000L
        private const val DUPLICATE_FIX_DISTANCE_M = 20f
    }

    // ── Sampling intervals — read from SharedPreferences with sensible defaults ──

    private fun prefs() = getSharedPreferences("tracking_prefs", MODE_PRIVATE)

    /** EMERGENCY: 15-second pure GPS, maximum precision */
    private val SOS_INTERVAL_MS = 15_000L

    /** MOVING: configurable, default 1 minute */
    private val movingIntervalMs get() = prefs().getLong("tracking_interval_ms", 1 * 60 * 1000L)

    /** HEARTBEAT: 30 minutes static force packet */
    private val HEARTBEAT_INTERVAL_MS = 30 * 60 * 1000L

    /** Distance threshold: configurable, default 200 metres */
    private val distanceThresholdM get() = prefs().getFloat("tracking_distance_m", 200f)

    /** Pause when static: if true, we apply the distance threshold. If false, we ignore distance threshold. */
    private val pauseWhenStatic get() = prefs().getBoolean("pause_when_static", true)

    // ── State ──────────────────────────────────────────────────────────────

    private var wakeLock: PowerManager.WakeLock? = null
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var lastLowAccuracyRecord: TelemetryRecord? = null
    private var lastLowAccuracyTime: Long = 0L
    private var lastKnownLocation: android.location.Location? = null
    private var lastRecordTime: Long = 0L

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        createNotificationChannel()
        ensureDeviceIdentity()
        purgeStaleRecords()
        Log.i(TAG, "Service created")
    }

    /**
     * Generates a stable, deterministic UUID from the device's ANDROID_ID.
     * This UUID is unique per device+app, survives updates, but resets on factory reset.
     */
    private fun ensureDeviceIdentity() {
        val prefs = getSharedPreferences("tracking_prefs", MODE_PRIVATE)
        val existing = prefs.getString("device_id", null)
        if (existing == null || existing == "unknown-device-id") {
            val androidId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
            val deterministicUuid = UUID.nameUUIDFromBytes(androidId.toByteArray()).toString()
            prefs.edit().putString("device_id", deterministicUuid).apply()
            Log.i(TAG, "Device identity established: $deterministicUuid (from ANDROID_ID)")
        }
    }

    /** Purges unsynced records saved with placeholder deviceId — runs once per process start. */
    private fun purgeStaleRecords() {
        val dao = createAppDatabase(applicationContext).telemetryDao()
        serviceScope.launch {
            val deleted = dao.deleteUnsyncedByDeviceId("unknown-device-id")
            if (deleted > 0) Log.w(TAG, "Purged $deleted stale records with 'unknown-device-id'")
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopTracking()
                return START_NOT_STICKY
            }
            ACTION_SOS_ACTIVATE -> {
                TrackingStateRepository.setSosActive(true)
                restartLocationUpdates()
                updateNotification()
                Log.w(TAG, "SOS mode ACTIVATED")
                return START_STICKY
            }
            ACTION_SOS_DEACTIVATE -> {
                TrackingStateRepository.setSosActive(false)
                restartLocationUpdates()
                updateNotification()
                Log.i(TAG, "SOS mode deactivated")
                return START_STICKY
            }
            ACTION_HEARTBEAT -> {
                handleHeartbeatTrigger()
                return START_STICKY
            }
            ACTION_RELOAD_CONFIG -> {
                if (TrackingStateRepository.isTracking.value) {
                    restartLocationUpdates()
                    Log.i(TAG, "Tracking config reloaded from SharedPreferences")
                }
                return START_STICKY
            }
        }

        // Default: start tracking
        startTracking()
        return START_STICKY
    }

    // ── Tracking lifecycle ─────────────────────────────────────────────────

    private fun startTracking() {
        TrackingStateRepository.setTracking(true)

        // Acquire wake lock to keep CPU awake while tracking is active
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SegurancaRural::TrackingWakeLock").apply {
                acquire()
            }
            Log.i(TAG, "WakeLock acquired successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to acquire WakeLock: ${e.message}")
        }

        // Start as foreground service with sticky notification
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        registerLocationCallback()
        requestLocationUpdates()
        
        try {
            Log.d(TAG, "Requesting immediate forced location fix...")
            fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnSuccessListener { location: android.location.Location? ->
                    if (location != null) {
                        Log.i(TAG, "✅ Forced immediate location fix received (${location.accuracy}m)")
                        TrackingStateRepository.setLastAccuracy(location.accuracy)
                        processLocationFix(location)
                    } else {
                        Log.w(TAG, "⚠️ Forced immediate location fix returned null")
                    }
                }
        } catch (e: SecurityException) {
            Log.e(TAG, "Location permission not granted for immediate fix: ${e.message}")
        }

        scheduleNextHeartbeatAlarm()
        Log.i(TAG, "GPS tracking started")
    }

    private fun scheduleNextHeartbeatAlarm() {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, HeartbeatReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Cancel existing alarm
        alarmManager.cancel(pendingIntent)

        // Schedule new exact alarm (Doze bypassing)
        val triggerAtMillis = System.currentTimeMillis() + HEARTBEAT_INTERVAL_MS
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            }
        } else {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        }
        
        Log.d(TAG, "Next heartbeat alarm scheduled for 30 minutes from now")
    }

    private fun cancelHeartbeatAlarm() {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, HeartbeatReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }

    private fun handleHeartbeatTrigger() {
        Log.i(TAG, "Heartbeat triggered by AlarmManager")
        val now = System.currentTimeMillis()
        if (lastKnownLocation != null) {
            Log.d(TAG, "Storing heartbeat record")
            val record = buildTelemetryRecord(lastKnownLocation!!)
            storeRecord(record)
            lastRecordTime = now
        } else {
            Log.w(TAG, "Cannot store heartbeat: lastKnownLocation is null, requesting immediate fix")
            try {
                fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                    .addOnSuccessListener { location: android.location.Location? ->
                        if (location != null) {
                            Log.i(TAG, "✅ Heartbeat immediate location fix received (${location.accuracy}m)")
                            lastKnownLocation = location
                            val record = buildTelemetryRecord(location)
                            storeRecord(record)
                            lastRecordTime = System.currentTimeMillis()
                        } else {
                            Log.w(TAG, "⚠️ Heartbeat immediate location fix returned null")
                        }
                    }
            } catch (e: SecurityException) {
                Log.e(TAG, "Location permission not granted for heartbeat immediate fix: ${e.message}")
            }
        }
        scheduleNextHeartbeatAlarm()
    }

    private fun stopTracking() {
        TrackingStateRepository.setTracking(false)
        TrackingStateRepository.setSosActive(false)
        fusedLocationClient.removeLocationUpdates(locationCallback)
        cancelHeartbeatAlarm()
        serviceScope.cancel()

        // Release WakeLock
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
            wakeLock = null
            Log.i(TAG, "WakeLock released")
        }

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        Log.i(TAG, "GPS tracking stopped")
    }

    private fun restartLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
        requestLocationUpdates()
    }

    // ── FusedLocation setup ───────────────────────────────────────────────

    private fun registerLocationCallback() {
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                for (location in result.locations) {
                    TrackingStateRepository.setLastAccuracy(location.accuracy)
                    processLocationFix(location)
                }
            }
        }
    }

    private fun requestLocationUpdates() {
        val isSos = TrackingStateRepository.isSosActive.value

        // Always use high accuracy (pure GPS) for rural safety reliability to guarantee 15m or better precision
        val priority = Priority.PRIORITY_HIGH_ACCURACY

        val intervalMs = when {
            isSos -> SOS_INTERVAL_MS
            else -> movingIntervalMs
        }

        val requestBuilder = LocationRequest.Builder(priority, intervalMs)
            .setMinUpdateIntervalMillis(intervalMs / 2)
            .setMaxUpdateDelayMillis(intervalMs * 2)

        if (!isSos && pauseWhenStatic) {
            requestBuilder.setMinUpdateDistanceMeters(distanceThresholdM)
            Log.d(TAG, "Location request: interval=${intervalMs/60000}min, distance=${distanceThresholdM}m")
        } else {
            Log.d(TAG, "Location request: interval=${intervalMs/60000}min (distance threshold bypassed/disabled)")
        }

        val request = requestBuilder.build()

        try {
            fusedLocationClient.requestLocationUpdates(
                request,
                locationCallback,
                Looper.getMainLooper()
            )
        } catch (e: SecurityException) {
            Log.e(TAG, "Location permission not granted: ${e.message}")
        }
    }

    // ── GPS fix processing ─────────────────────────────────────────────────

    private fun processLocationFix(location: android.location.Location) {
        val now = System.currentTimeMillis()

        val previous = lastKnownLocation
        if (previous != null &&
            now - lastRecordTime < DUPLICATE_FIX_DEBOUNCE_MS &&
            location.distanceTo(previous) < DUPLICATE_FIX_DISTANCE_M
        ) {
            Log.d(TAG, "Skipping near-duplicate fix (${now - lastRecordTime}ms, ${location.distanceTo(previous)}m)")
            lastKnownLocation = location
            return
        }

        val isLowAccuracy = location.accuracy > ACCURACY_THRESHOLD_METERS

        // Accuracy filter: if we have a recent low-accuracy record and this fix
        // is better, replace it. Otherwise discard the low-accuracy fix.
        if (isLowAccuracy) {
            if (!TrackingStateRepository.isSosActive.value) {
                lastLowAccuracyRecord = buildTelemetryRecord(location)
                lastLowAccuracyTime = now
                Log.d(TAG, "Low accuracy fix (${location.accuracy}m) — holding for ${ACCURACY_REPLACE_WINDOW_MS / 1000}s")
                return
            }
            // In SOS mode, we always store regardless of accuracy
        } else {
            // Good fix: if there was a recent bad fix, discard it
            if (lastLowAccuracyRecord != null &&
                (now - lastLowAccuracyTime) < ACCURACY_REPLACE_WINDOW_MS
            ) {
                Log.d(TAG, "Better fix arrived (${location.accuracy}m) — discarding previous low-accuracy record")
                lastLowAccuracyRecord = null
            }
        }

        val record = buildTelemetryRecord(location)
        storeRecord(record)
        lastRecordTime = now
        lastKnownLocation = location

        // We received a valid location fix. Reset the 30-minute heartbeat clock.
        scheduleNextHeartbeatAlarm()
    }

    private fun buildTelemetryRecord(location: android.location.Location): TelemetryRecord {
        // Use current system time as record timestamp so that each telemetry heartbeat
        // packet (even with stationary coordinates) registers as a fresh live signal on the server.
        val now = System.currentTimeMillis()
        val isoTimestamp = Instant.ofEpochMilli(now).toString()

        val batteryManager = getSystemService(BATTERY_SERVICE) as? android.os.BatteryManager
        val batteryLevel = batteryManager
            ?.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
        val isCharging = batteryManager
            ?.isCharging ?: false

        val connectivityManager = getSystemService(CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
        val networkType = connectivityManager
            ?.getNetworkCapabilities(connectivityManager.activeNetwork)
            ?.let { caps ->
                when {
                    caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) -> "WIFI"
                    caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) -> "CELL"
                    else -> "OTHER"
                }
            } ?: "NONE"

        val prefs = getSharedPreferences("tracking_prefs", MODE_PRIVATE)
        val deviceId = prefs.getString("device_id", null)
            ?: UUID.nameUUIDFromBytes(
                Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID).toByteArray()
            ).toString()
        val deviceLabel = prefs.getString("device_label", "Dispositivo") ?: "Dispositivo"

        return TelemetryRecord(
            deviceId = deviceId,
            deviceLabel = deviceLabel,
            timestamp = isoTimestamp,
            batteryLevel = batteryLevel,
            batteryCharging = isCharging,
            lat = location.latitude,
            lng = location.longitude,
            accuracy = location.accuracy,
            speed = if (location.hasSpeed()) location.speed * 3.6f else 0f, // m/s → km/h
            heading = if (location.hasBearing()) location.bearing else 0f,
            emergencyState = TrackingStateRepository.isSosActive.value,
            trackingEnabled = true,
            networkType = networkType,
            appVersion = BuildConfig.VERSION_NAME,
            createdAtEpochMs = now
        )
    }

    private fun storeRecord(record: TelemetryRecord) {
        val repository = TelemetryRepository(applicationContext)
        val submitLocationUseCase = SubmitLocationUseCase(repository)
        serviceScope.launch {
            submitLocationUseCase(record)
            Log.d(TAG, "Submitted record (emergency=${record.emergencyState}, accuracy=${record.accuracy}m)")
        }
    }

    // ── Notification ──────────────────────────────────────────────────────

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            "GPS Rastreio Ativo",
            NotificationManager.IMPORTANCE_LOW  // LOW = no sound, but always visible
        ).apply {
            description = "Mantém o rastreio GPS ativo em segundo plano"
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (TrackingStateRepository.isSosActive.value) "🚨 EMERGÊNCIA ATIVA" else "📍 Rastreio GPS Ativo"
        val text = if (TrackingStateRepository.isSosActive.value) "SOS em transmissão contínua" else "A recolher localização em segundo plano"

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(openAppIntent)
            .setOngoing(true)  // Cannot be dismissed by the user
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification())
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        TrackingStateRepository.setTracking(false)
        serviceScope.cancel()

        // Safely release WakeLock if still held
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
            wakeLock = null
            Log.i(TAG, "WakeLock released in onDestroy")
        }

        Log.i(TAG, "Service destroyed")
    }
}
