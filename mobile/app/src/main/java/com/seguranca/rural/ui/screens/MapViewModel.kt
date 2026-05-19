package com.seguranca.rural.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.seguranca.rural.data.db.createAppDatabase
import com.seguranca.rural.data.model.TelemetryRecord
import com.seguranca.rural.util.PREF_DEVICE_LABEL
import com.seguranca.rural.util.PREF_DEVICE_MARKER_COLOR
import com.seguranca.rural.util.TRACKING_PREFS_NAME
import com.seguranca.rural.util.DEFAULT_MARKER_COLOR_ARGB
import com.seguranca.rural.util.argbToMapLibreHex
import com.seguranca.rural.util.markerInitial
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn

/** How many recent GPS points to draw as the route polyline. */
enum class MapPointLimit(val label: String, val maxPoints: Int?) {
    LAST_10("10", 10),
    LAST_100("100", 100),
    ALL("Tudo", null),
}

data class DeviceMapStyle(
    val label: String = "Dispositivo",
    val markerColorArgb: Int = DEFAULT_MARKER_COLOR_ARGB,
) {
    val markerLetter: String get() = markerInitial(label)
    val routeColorHex: String get() = argbToMapLibreHex(markerColorArgb)
    val markerColorHex: String get() = routeColorHex
}

class MapViewModel(application: Application) : AndroidViewModel(application) {

    private val db = createAppDatabase(application)
    private val dao = db.telemetryDao()
    private val prefs = application.getSharedPreferences(TRACKING_PREFS_NAME, Application.MODE_PRIVATE)

    val timeFilter = MutableStateFlow(MapTimeFilter.TODAY)
    val pointLimit = MutableStateFlow(MapPointLimit.LAST_100)

    private val deviceStyle = MutableStateFlow(loadDeviceStyle())

    fun refreshDeviceStyle() {
        deviceStyle.value = loadDeviceStyle()
    }

    private fun loadDeviceStyle(): DeviceMapStyle {
        val label = prefs.getString(PREF_DEVICE_LABEL, "Dispositivo") ?: "Dispositivo"
        val color = prefs.getInt(PREF_DEVICE_MARKER_COLOR, DEFAULT_MARKER_COLOR_ARGB)
        return DeviceMapStyle(label = label, markerColorArgb = color)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val rawRouteHistory: StateFlow<List<TelemetryRecord>> = timeFilter
        .flatMapLatest { filter ->
            dao.observeRouteHistory(calculateSinceMs(filter))
        }
        .flowOn(Dispatchers.IO)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val routeHistory: StateFlow<List<TelemetryRecord>> = combine(
        rawRouteHistory,
        pointLimit,
    ) { history, limit ->
        when (limit.maxPoints) {
            null -> history
            else -> history.takeLast(limit.maxPoints)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val mapStyle: StateFlow<DeviceMapStyle> = deviceStyle

    private fun calculateSinceMs(filter: MapTimeFilter): Long {
        val now = System.currentTimeMillis()
        val oneHourMs = 60 * 60 * 1000L
        return when (filter) {
            MapTimeFilter.TODAY -> now - (12 * oneHourMs)
            MapTimeFilter.LAST_24H -> now - (24 * oneHourMs)
            MapTimeFilter.WEEKLY -> now - (7 * 24 * oneHourMs)
        }
    }
}
