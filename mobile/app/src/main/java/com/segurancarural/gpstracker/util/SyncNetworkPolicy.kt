package com.segurancarural.gpstracker.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

private const val PREFS_NAME = "tracking_prefs"
private const val KEY_SYNC_ON_MOBILE = "sync_on_mobile_data"

/**
 * Returns true when telemetry may be uploaded over the current network.
 * Wi‑Fi and Ethernet always allowed; cellular follows the user preference.
 */
fun shouldUploadOverCurrentNetwork(context: Context): Boolean {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    if (prefs.getBoolean(KEY_SYNC_ON_MOBILE, true)) return true

    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        ?: return true
    val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false

    return !caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
}
