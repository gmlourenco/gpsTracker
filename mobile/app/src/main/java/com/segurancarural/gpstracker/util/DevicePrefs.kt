package com.segurancarural.gpstracker.util

import android.content.Context
import android.provider.Settings
import androidx.core.content.edit
import java.util.UUID
import com.segurancarural.gpstracker.data.dto.DeviceConfigDto

const val TRACKING_PREFS_NAME = "tracking_prefs"
const val PREF_DEVICE_LABEL = "device_label"
const val PREF_DEVICE_MARKER_COLOR = "device_marker_color"

/** Default marker green — matches dashboard active devices. */
const val DEFAULT_MARKER_COLOR_ARGB = 0xFF16A34A.toInt()

fun Context.trackingPrefs() = getSharedPreferences(TRACKING_PREFS_NAME, Context.MODE_PRIVATE)

fun Context.deviceLabel(): String =
    trackingPrefs().getString(PREF_DEVICE_LABEL, "Dispositivo")?.trim().orEmpty()
        .ifEmpty { "Dispositivo" }

fun Context.deviceMarkerColorArgb(): Int =
    trackingPrefs().getInt(PREF_DEVICE_MARKER_COLOR, DEFAULT_MARKER_COLOR_ARGB)

/** First letter shown inside the map marker circle. */
fun markerInitial(label: String): String {
    val trimmed = label.trim()
    return if (trimmed.isEmpty()) "?" else trimmed.first().uppercaseChar().toString()
}

/** `#RRGGBB` for MapLibre style properties. */
fun argbToMapLibreHex(argb: Int): String {
    val rgb = argb and 0xFFFFFF
    return String.format("#%06X", rgb)
}

/** Stable device UUID (v3) — matches [LocationForegroundService.ensureDeviceIdentity]. */
@Deprecated("Use ensureSerialNumber() instead", ReplaceWith("ensureSerialNumber()"))
fun Context.ensureDeviceId(): String {
    val prefs = trackingPrefs()
    val existing = prefs.getString("device_id", null)
    if (existing != null && existing != "unknown-device-id") return existing
    val androidId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
    val id = UUID.nameUUIDFromBytes(androidId.toByteArray()).toString()
    prefs.edit { putString("device_id", id) }
    return id
}

/**
 * Stable device serial number — the raw ANDROID_ID hex string.
 * Persists across app reinstalls (same signing key), resets only on factory reset.
 * Replaces the old UUID-based ensureDeviceId().
 */
fun Context.ensureSerialNumber(): String {
    val prefs = trackingPrefs()
    val existing = prefs.getString("serial_number", null)
    if (!existing.isNullOrBlank()) return existing
    val androidId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
    prefs.edit { putString("serial_number", androidId) }
    return androidId
}

/** Converts MapLibre hex `#RRGGBB` or `#AARRGGBB` back to ARGB Int. */
fun mapLibreHexToArgb(hex: String): Int {
    return try {
        val cleanHex = hex.replace("#", "")
        if (cleanHex.length == 6) {
            (0xFF000000 or cleanHex.toLong(16)).toInt()
        } else if (cleanHex.length == 8) {
            cleanHex.toLong(16).toInt()
        } else {
            DEFAULT_MARKER_COLOR_ARGB
        }
    } catch (e: Exception) {
        AppLog.e("DevicePrefs", "Failed to parse color hex: $hex", e)
        DEFAULT_MARKER_COLOR_ARGB
    }
}

/** Saves configuration fetched from backend directly to SharedPreferences. */
fun Context.saveConfigToPrefs(config: DeviceConfigDto) {
    val prefs = trackingPrefs()
    val localLastUpdated = prefs.getLong("config_last_updated_ms", -1)
    if (config.configUpdatedAt <= localLastUpdated) {
        // Local configuration is newer or same as server. Do not overwrite.
        return
    }

    prefs.edit {
        putString("device_label", config.deviceLabel)
        putInt(PREF_DEVICE_MARKER_COLOR, mapLibreHexToArgb(config.markerColor))
        putString("emergency_contact", config.emergencyContact ?: "")
        putBoolean("sync_on_mobile_data", config.syncOnMobileData)
        putLong("tracking_interval_ms", config.trackingIntervalMs)
        putFloat("tracking_distance_m", config.trackingDistanceM)
        putString("default_map_type", config.defaultMapType)
        putString("accident_sensor_sensitivity", config.accidentSensorSensitivity)
        putLong("config_last_updated_ms", config.configUpdatedAt)
    }
}
