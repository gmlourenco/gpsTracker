package com.segurancarural.gpstracker.util

import android.content.Context
import android.provider.Settings
import androidx.core.content.edit
import java.util.UUID

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
fun Context.ensureDeviceId(): String {
    val prefs = trackingPrefs()
    val existing = prefs.getString("device_id", null)
    if (existing != null && existing != "unknown-device-id") return existing
    val androidId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
    val id = UUID.nameUUIDFromBytes(androidId.toByteArray()).toString()
    prefs.edit { putString("device_id", id) }
    return id
}
