package com.segurancarural.gpstracker.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.segurancarural.gpstracker.data.db.createAppDatabase
import com.segurancarural.gpstracker.data.model.TelemetryRecord
import com.segurancarural.gpstracker.data.repository.FamilyPositionsRepository
import com.segurancarural.gpstracker.ui.model.DeviceMapStyle
import com.segurancarural.gpstracker.ui.model.FamilyDeviceMarker
import com.segurancarural.gpstracker.ui.model.FamilyRefreshStatus
import com.segurancarural.gpstracker.ui.model.MapDisplayData
import com.segurancarural.gpstracker.ui.model.MapMarkerDisplay
import com.segurancarural.gpstracker.ui.model.MapPointLimit
import com.segurancarural.gpstracker.util.PREF_DEVICE_LABEL
import com.segurancarural.gpstracker.util.PREF_DEVICE_MARKER_COLOR
import com.segurancarural.gpstracker.util.TRACKING_PREFS_NAME
import com.segurancarural.gpstracker.util.DEFAULT_MARKER_COLOR_ARGB
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MapViewModel(application: Application) : AndroidViewModel(application) {

    private val db = createAppDatabase(application)
    private val dao = db.telemetryDao()
    private val prefs = application.getSharedPreferences(TRACKING_PREFS_NAME, Application.MODE_PRIVATE)
    private val familyRepository = FamilyPositionsRepository()

    private val myRouteHoursMs = 24L * 60 * 60 * 1000

    val pointLimit = MutableStateFlow(MapPointLimit.LAST_100)

    private val _findFamilyEnabled = MutableStateFlow(false)
    val findFamilyEnabled: StateFlow<Boolean> = _findFamilyEnabled.asStateFlow()

    private val _familyRefreshStatus = MutableStateFlow(FamilyRefreshStatus.Idle)
    val familyRefreshStatus: StateFlow<FamilyRefreshStatus> = _familyRefreshStatus.asStateFlow()

    private val _familyMarkers = MutableStateFlow<List<FamilyDeviceMarker>>(emptyList())
    val familyMarkers: StateFlow<List<FamilyDeviceMarker>> = _familyMarkers.asStateFlow()

    private val deviceStyle = MutableStateFlow(loadDeviceStyle())

    private val rawMyRoute: StateFlow<List<TelemetryRecord>> = dao
        .observeRouteHistory(System.currentTimeMillis() - myRouteHoursMs)
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val myRouteHistory: StateFlow<List<TelemetryRecord>> = combine(
        rawMyRoute,
        pointLimit,
    ) { history, limit ->
        when (limit.maxPoints) {
            null -> history
            else -> history.takeLast(limit.maxPoints)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val mapDisplay: StateFlow<MapDisplayData> = combine(
        findFamilyEnabled,
        myRouteHistory,
        familyMarkers,
        deviceStyle,
    ) { familyMode, myRoute, family, style ->
        if (familyMode) {
            MapDisplayData(
                routePoints = emptyList(),
                primaryMarker = null,
                familyMarkers = family,
                isFamilyMode = true,
            )
        } else {
            val latest = myRoute.lastOrNull()
            MapDisplayData(
                routePoints = myRoute,
                primaryMarker = latest?.let {
                    MapMarkerDisplay(
                        lat = it.lat,
                        lng = it.lng,
                        letter = style.markerLetter,
                        colorHex = style.markerColorHex,
                        emergencyState = it.emergencyState,
                    )
                },
                familyMarkers = emptyList(),
                isFamilyMode = false,
            )
        }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        MapDisplayData(),
    )

    val mapStyle: StateFlow<DeviceMapStyle> = deviceStyle

    fun refreshDeviceStyle() {
        deviceStyle.value = loadDeviceStyle()
    }

    fun setFindFamilyEnabled(enabled: Boolean) {
        _findFamilyEnabled.value = enabled
        if (enabled && _familyMarkers.value.isEmpty()) {
            refreshFamilyPositions()
        }
    }

    fun refreshFamilyPositions() {
        if (_familyRefreshStatus.value == FamilyRefreshStatus.Loading) return
        viewModelScope.launch {
            _familyRefreshStatus.value = FamilyRefreshStatus.Loading
            val result = familyRepository.fetchLastPositions()
            result.fold(
                onSuccess = { markers ->
                    _familyMarkers.value = markers
                    _familyRefreshStatus.value = FamilyRefreshStatus.Success
                    delay(1_000)
                    if (_familyRefreshStatus.value == FamilyRefreshStatus.Success) {
                        _familyRefreshStatus.value = FamilyRefreshStatus.Idle
                    }
                },
                onFailure = {
                    _familyRefreshStatus.value = FamilyRefreshStatus.Error
                    delay(5_000)
                    if (_familyRefreshStatus.value == FamilyRefreshStatus.Error) {
                        _familyRefreshStatus.value = FamilyRefreshStatus.Idle
                    }
                },
            )
        }
    }

    private fun loadDeviceStyle(): DeviceMapStyle {
        val label = prefs.getString(PREF_DEVICE_LABEL, "Dispositivo") ?: "Dispositivo"
        val color = prefs.getInt(PREF_DEVICE_MARKER_COLOR, DEFAULT_MARKER_COLOR_ARGB)
        return DeviceMapStyle(label = label, markerColorArgb = color)
    }
}
