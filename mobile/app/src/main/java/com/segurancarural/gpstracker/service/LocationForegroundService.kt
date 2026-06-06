package com.segurancarural.gpstracker.service

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
import com.segurancarural.gpstracker.BuildConfig
import com.segurancarural.gpstracker.data.db.createAppDatabase
import com.segurancarural.gpstracker.data.model.TelemetryRecord
import com.segurancarural.gpstracker.data.repository.FamilyPositionsRepository
import com.segurancarural.gpstracker.data.repository.TelemetryRepository
import com.segurancarural.gpstracker.data.repository.TrackingStateRepository
import com.segurancarural.gpstracker.domain.usecase.SubmitLocationUseCase
import com.segurancarural.gpstracker.receiver.HeartbeatReceiver
import com.segurancarural.gpstracker.service.LocationForegroundService.Companion.ACTION_SOS_ACTIVATE
import com.segurancarural.gpstracker.service.LocationForegroundService.Companion.ACTION_SOS_DEACTIVATE
import com.segurancarural.gpstracker.ui.activities.MainActivity
import com.segurancarural.gpstracker.ui.model.FamilyDeviceMarker
import com.segurancarural.gpstracker.util.ensureSerialNumber
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Instant

private const val TAG = "LocationFgService"
private const val NOTIFICATION_CHANNEL_ID = "gps_tracking_channel"
private const val SOS_ALERT_CHANNEL_ID = "sos_alert_channel"
private const val NOTIFICATION_ID = 1001

/**
 * LocationForegroundService — persistent GPS tracking service.
 *
 * Runs as a Foreground Service to survive Android's low-memory killer.
 * Uses [FusedLocationProviderClient] with time-based polling and app-level submit rules:
 *
 *   Normal:    submit every [interval] OR when moved [distance threshold] (whichever first)
 *   EMERGENCY: 15s continuous pure GPS (SOS activated)
 *   Heartbeat: 30 min AlarmManager fallback (Doze safety net)
 *
 * Every GPS fix is stored in the local Room database (offline queue).
 * The [SyncWorker] is responsible for flushing the queue when online.
 *
 * Start/stop the service from [MainActivity] or [BootReceiver].
 * Toggle SOS mode via [ACTION_SOS_ACTIVATE] / [ACTION_SOS_DEACTIVATE] intents.
 */
class LocationForegroundService : Service() {

    companion object {
        const val ACTION_START = "com.segurancarural.gpstracker.action.START_TRACKING"
        const val ACTION_STOP = "com.segurancarural.gpstracker.action.STOP_TRACKING"
        const val ACTION_SOS_ACTIVATE = "com.segurancarural.gpstracker.action.SOS_ACTIVATE"
        const val ACTION_SOS_DEACTIVATE = "com.segurancarural.gpstracker.action.SOS_DEACTIVATE"
        const val ACTION_HEARTBEAT = "com.segurancarural.gpstracker.action.HEARTBEAT"
        const val ACTION_RELOAD_CONFIG = "com.segurancarural.gpstracker.action.RELOAD_CONFIG"
        const val ACTION_ACCIDENT_CANCEL = "com.segurancarural.gpstracker.action.ACCIDENT_CANCEL"
        const val ACTION_ACCIDENT_TRIGGER = "com.segurancarural.gpstracker.action.ACCIDENT_TRIGGER"



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

    /** Distance threshold: also submit when moved at least this far (even before the interval). */
    private val distanceThresholdM get() = prefs().getFloat("tracking_distance_m", 200f)

    // ── State ──────────────────────────────────────────────────────────────

    private var wakeLock: PowerManager.WakeLock? = null
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var lastLowAccuracyRecord: TelemetryRecord? = null
    private var lastLowAccuracyTime: Long = 0L
    /** Last fix used for heartbeat fallback. */
    private var lastKnownLocation: android.location.Location? = null
    /** Last fix actually sent to the server — used for interval / distance OR policy. */
    private var lastSubmittedLocation: android.location.Location? = null
    private var lastSubmittedTimeMs: Long = 0L
    private var pollingJob: kotlinx.coroutines.Job? = null

    // ── Accident Detection Countdown State ───────────────────────────────
    private var accidentDetector: com.segurancarural.gpstracker.sensor.AccidentDetector? = null
    private var countdownJob: kotlinx.coroutines.Job? = null
    private var ringtone: android.media.Ringtone? = null

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        createNotificationChannel()
        applicationContext.ensureSerialNumber()
        purgeStaleRecords()
        Log.i(TAG, "Service created")
    }

    /** Purges unsynced records saved with placeholder deviceId — runs once per process start. */
    private fun purgeStaleRecords() {
        val dao = createAppDatabase(applicationContext).telemetryDao()
        serviceScope.launch {
            val deleted = dao.deleteUnsyncedBySerialNumber("unknown-device-id")
            if (deleted > 0) Log.w(TAG, "Purged $deleted stale records with 'unknown-device-id'")
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopTracking()
                return START_NOT_STICKY
            }
            ACTION_ACCIDENT_CANCEL -> {
                handleAccidentCancel()
                return START_STICKY
            }
            ACTION_ACCIDENT_TRIGGER -> {
                handleAccidentTrigger()
                return START_STICKY
            }
            ACTION_SOS_ACTIVATE -> {
                TrackingStateRepository.setSosActive(true)
                restartLocationUpdates()
                updateNotification()
                try {
                    Log.d(TAG, "Forcing immediate GPS fix for SOS activation...")
                    fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                        .addOnSuccessListener { location: android.location.Location? ->
                            if (location != null) {
                                Log.i(TAG, "✅ Forced immediate SOS location fix received (${location.accuracy}m)")
                                TrackingStateRepository.setLastAccuracy(location.accuracy)
                                processLocationFix(location)
                            } else {
                                Log.w(TAG, "⚠️ Forced immediate SOS location fix returned null")
                            }
                        }
                } catch (e: SecurityException) {
                    Log.e(TAG, "Location permission not granted for immediate SOS fix: ${e.message}")
                }
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
                    stopAccidentSensor()
                    startAccidentSensor()
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
        startBackgroundEmergencyPolling()
        startAccidentSensor()
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
            lastSubmittedTimeMs = now
            lastSubmittedLocation = lastKnownLocation
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
                            lastSubmittedTimeMs = System.currentTimeMillis()
                            lastSubmittedLocation = location
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
        stopAccidentSensor()
        fusedLocationClient.removeLocationUpdates(locationCallback)
        cancelHeartbeatAlarm()
        pollingJob?.cancel()
        pollingJob = null
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

        // Poll every 30 seconds normally to check distance changes, or every 15 seconds in SOS mode.
        // If distance threshold is <= 0m (disabled), poll at the moving time interval.
        val intervalMs = when {
            isSos -> SOS_INTERVAL_MS
            distanceThresholdM <= 0f -> movingIntervalMs
            else -> 30_000L
        }

        val requestBuilder = LocationRequest.Builder(priority, intervalMs)
            .setMinUpdateIntervalMillis(maxOf(intervalMs / 2, 10_000L))
            .setMaxUpdateDelayMillis(intervalMs)

        Log.d(
            TAG,
            "Location request: polling every ${intervalMs / 1000}s. Submit config: every ${movingIntervalMs / 60_000}min OR ${distanceThresholdM}m movement"
        )

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
            now - lastSubmittedTimeMs < DUPLICATE_FIX_DEBOUNCE_MS &&
            location.distanceTo(previous) < DUPLICATE_FIX_DISTANCE_M
        ) {
            Log.d(TAG, "Skipping near-duplicate fix (${now - lastSubmittedTimeMs}ms, ${location.distanceTo(previous)}m)")
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

        if (!TrackingStateRepository.isSosActive.value && !shouldSubmitNow(location, now)) {
            val sinceMs = now - lastSubmittedTimeMs
            val movedM = lastSubmittedLocation?.distanceTo(location) ?: 0f
            Log.d(
                TAG,
                "Holding fix: ${sinceMs}ms since last submit (need ${movingIntervalMs}ms), " +
                    "${movedM}m moved (need ${distanceThresholdM}m)"
            )
            lastKnownLocation = location
            return
        }

        val record = buildTelemetryRecord(location)
        storeRecord(record)
        lastSubmittedTimeMs = now
        lastSubmittedLocation = location
        lastKnownLocation = location

        // We received a valid location fix. Reset the 30-minute heartbeat clock.
        scheduleNextHeartbeatAlarm()
    }

    /**
     * Submit when the configured interval has elapsed OR the device moved beyond the distance threshold.
     * SOS mode bypasses this (handled before this check is reached).
     */
    private fun shouldSubmitNow(location: android.location.Location, now: Long): Boolean {
        if (lastSubmittedTimeMs == 0L) return true
        if (now - lastSubmittedTimeMs >= movingIntervalMs) return true
        if (distanceThresholdM <= 0f) return false
        val last = lastSubmittedLocation ?: return true
        return location.distanceTo(last) >= distanceThresholdM
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
        val serialNumber = applicationContext.ensureSerialNumber()
        val deviceLabel = prefs.getString("device_label", "Dispositivo") ?: "Dispositivo"

        return TelemetryRecord(
            serialNumber = serialNumber,
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
        val manager = getSystemService(NotificationManager::class.java) ?: return
        
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            "GPS Rastreio Ativo",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Mantém o rastreio GPS ativo em segundo plano"
        }
        manager.createNotificationChannel(channel)

        val sosChannel = NotificationChannel(
            SOS_ALERT_CHANNEL_ID,
            "Alertas de Emergência SOS",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Notifica quando um membro da família ativa o SOS"
            enableVibration(true)
            enableLights(true)
        }
        manager.createNotificationChannel(sosChannel)
    }

    private fun buildNotification(): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        val isPreSos = TrackingStateRepository.isPreSosActive.value
        val isSos = TrackingStateRepository.isSosActive.value

        val channelId = if (isPreSos) SOS_ALERT_CHANNEL_ID else NOTIFICATION_CHANNEL_ID
        val title = when {
            isPreSos -> "🚨 POSSÍVEL ACIDENTE DETECTADO"
            isSos -> "🚨 EMERGÊNCIA ATIVA"
            else -> "📍 Rastreio GPS Ativo"
        }
        val text = when {
            isPreSos -> "SOS será ativado em ${TrackingStateRepository.preSosCountdown.value}s. Toque para cancelar!"
            isSos -> "SOS em transmissão contínua"
            else -> "A recolher localização em segundo plano"
        }

        val builder = NotificationCompat.Builder(this, channelId)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(if (isPreSos) android.R.drawable.stat_notify_error else android.R.drawable.ic_menu_mylocation)
            .setContentIntent(openAppIntent)
            .setOngoing(true)  // Cannot be dismissed by the user

        if (isPreSos) {
            builder.setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setFullScreenIntent(openAppIntent, true)

            // Add notification actions
            val cancelIntent = PendingIntent.getBroadcast(
                this,
                101,
                Intent(this, com.segurancarural.gpstracker.receiver.AccidentReceiver::class.java).apply {
                    action = com.segurancarural.gpstracker.receiver.AccidentReceiver.ACTION_ACCIDENT_CANCEL
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val triggerIntent = PendingIntent.getBroadcast(
                this,
                102,
                Intent(this, com.segurancarural.gpstracker.receiver.AccidentReceiver::class.java).apply {
                    action = com.segurancarural.gpstracker.receiver.AccidentReceiver.ACTION_ACCIDENT_TRIGGER
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, "CANCELAR", cancelIntent)
            builder.addAction(android.R.drawable.ic_menu_send, "ATIVAR AGORA", triggerIntent)
        } else {
            builder.setPriority(NotificationCompat.PRIORITY_LOW)
        }

        return builder.build()
    }

    private fun updateNotification() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification())
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        stopAccidentSensor()
        TrackingStateRepository.setTracking(false)
        TrackingStateRepository.setPreSosActive(false)
        serviceScope.cancel()

        // Safely release WakeLock if still held
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
            wakeLock = null
            Log.i(TAG, "WakeLock released in onDestroy")
        }

        Log.i(TAG, "Service destroyed")
    }

    private fun startBackgroundEmergencyPolling() {
        pollingJob?.cancel()
        pollingJob = serviceScope.launch {
            val familyRepository = FamilyPositionsRepository()
            val notifiedSosDevices = mutableSetOf<String>()
            
            while (isActive) {
                val ourSerialNumber = applicationContext.ensureSerialNumber()
                val result = familyRepository.fetchLastPositions()
                result.fold(
                    onSuccess = { markers ->
                        val emergencyMarkers = markers.filter { it.emergencyState && it.deviceId != ourSerialNumber }
                        for (marker in emergencyMarkers) {
                            if (marker.deviceId !in notifiedSosDevices) {
                                triggerEmergencyNotification(marker)
                                notifiedSosDevices.add(marker.deviceId)
                            }
                        }
                        // Clean up devices that are no longer in SOS
                        val currentSosIds = emergencyMarkers.map { it.deviceId }
                            notifiedSosDevices.retainAll(currentSosIds)
                        },
                        onFailure = { error ->
                            Log.e(TAG, "Failed to poll family positions: ${error.message}")
                        }
                    )
                delay(20_000L) // Poll every 20 seconds
            }
        }
    }

    private fun triggerEmergencyNotification(marker: FamilyDeviceMarker) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notificationId = marker.deviceId.hashCode()
        
        val openMapIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            notificationId,
            openMapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val title = "🚨 ALERTA SOS: ${marker.label}"
        val text = "Ativou o sinal de emergência! Toca para ver no mapa."
        
        val builder = NotificationCompat.Builder(this, SOS_ALERT_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
        
        notificationManager.notify(notificationId, builder.build())
    }

    private fun startAccidentSensor() {
        val prefs = getSharedPreferences("tracking_prefs", MODE_PRIVATE)
        val sensitivity = prefs.getString("accident_sensor_sensitivity", "medium") ?: "medium"
        accidentDetector = com.segurancarural.gpstracker.sensor.AccidentDetector(
            context = this,
            sensitivity = sensitivity,
            onAccidentDetected = {
                triggerAccidentCountdown()
            }
        )
        accidentDetector?.start()
        Log.i(TAG, "Accident detector initialized.")
    }

    private fun stopAccidentSensor() {
        accidentDetector?.stop()
        accidentDetector = null
        cancelAccidentCountdown()
    }

    private fun triggerAccidentCountdown() {
        if (TrackingStateRepository.isPreSosActive.value || TrackingStateRepository.isSosActive.value) {
            return
        }

        Log.w(TAG, "Accident impact threshold breached! Triggering countdown.")
        TrackingStateRepository.setPreSosActive(true)
        TrackingStateRepository.setPreSosCountdown(15)

        // Play alarm ringtone
        try {
            val alarmUri = android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_ALARM)
                ?: android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_RINGTONE)
            ringtone = android.media.RingtoneManager.getRingtone(applicationContext, alarmUri)
            ringtone?.play()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start audio alarm: ${e.message}")
        }

        updateNotification()

        // Start countdown job
        countdownJob?.cancel()
        countdownJob = serviceScope.launch {
            var seconds = 15
            while (seconds > 0) {
                TrackingStateRepository.setPreSosCountdown(seconds)
                updateNotification()
                delay(1000L)
                seconds--
            }
            Log.i(TAG, "Accident countdown expired. Automatically triggering SOS.")
            handleAccidentTrigger()
        }
    }

    private fun cancelAccidentCountdown() {
        countdownJob?.cancel()
        countdownJob = null

        TrackingStateRepository.setPreSosActive(false)
        TrackingStateRepository.setPreSosCountdown(15)

        try {
            ringtone?.stop()
            ringtone = null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop ringtone: ${e.message}")
        }
    }

    fun handleAccidentCancel() {
        Log.i(TAG, "Accident countdown cancelled by user.")
        cancelAccidentCountdown()
        updateNotification()
    }

    fun handleAccidentTrigger() {
        Log.w(TAG, "Accident SOS triggered immediately.")
        cancelAccidentCountdown()
        
        // Trigger actual SOS action
        val intent = Intent(this, LocationForegroundService::class.java).apply {
            action = ACTION_SOS_ACTIVATE
        }
        startService(intent)
    }
}
