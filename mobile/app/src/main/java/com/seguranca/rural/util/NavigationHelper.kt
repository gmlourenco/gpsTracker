package com.seguranca.rural.util

import android.content.Context
import android.content.Intent
import android.net.Uri

object NavigationHelper {

    /**
     * Get intent to show route preview to a destination in Google Maps (or browser fallback)
     */
    fun getGoogleMapsRoutePreviewIntent(lat: Double, lng: Double): Intent {
        val uri = Uri.parse("https://www.google.com/maps/dir/?api=1&destination=$lat,$lng")
        return Intent(Intent.ACTION_VIEW, uri).apply {
            setPackage("com.google.android.apps.maps")
        }
    }

    /**
     * Get intent to start immediate turn-by-turn navigation in Google Maps
     */
    fun getGoogleMapsDirectNavigationIntent(lat: Double, lng: Double): Intent {
        val uri = Uri.parse("google.navigation:q=$lat,$lng")
        return Intent(Intent.ACTION_VIEW, uri).apply {
            setPackage("com.google.android.apps.maps")
        }
    }

    /**
     * Get intent to preview a single pin/marker location on the map without routing
     */
    fun getGoogleMapsLocationPreviewIntent(lat: Double, lng: Double, label: String = "Localização"): Intent {
        val encodedLabel = Uri.encode(label)
        val uri = Uri.parse("geo:0,0?q=$lat,$lng($encodedLabel)")
        return Intent(Intent.ACTION_VIEW, uri)
    }

    /**
     * Get intent for Waze navigation
     */
    fun getWazeNavigationIntent(lat: Double, lng: Double): Intent {
        val uri = Uri.parse("waze://?ll=$lat,$lng&navigate=yes")
        return Intent(Intent.ACTION_VIEW, uri)
    }

    /**
     * Helper to safely launch a map intent, falling back to a web browser if the specific app is not available.
     */
    fun launchIntentSafely(context: Context, intent: Intent, fallbackUriStr: String? = null) {
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            if (fallbackUriStr != null) {
                try {
                    val fallbackIntent = Intent(Intent.ACTION_VIEW, Uri.parse(fallbackUriStr))
                    context.startActivity(fallbackIntent)
                } catch (fallbackEx: Exception) {
                    AppLog.e("NavigationHelper", "Failed to launch fallback map intent", fallbackEx)
                }
            } else {
                AppLog.e("NavigationHelper", "Failed to launch map intent", e)
            }
        }
    }
}
