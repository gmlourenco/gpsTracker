package com.segurancarural.gpstracker.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities


/**
 * Returns true when telemetry may be uploaded over the current network.
 * Wi‑Fi and Ethernet always allowed; cellular follows the user preference.
 */
fun shouldUploadOverCurrentNetwork(context: Context): Boolean {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        ?: return false
    val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
    return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
}
