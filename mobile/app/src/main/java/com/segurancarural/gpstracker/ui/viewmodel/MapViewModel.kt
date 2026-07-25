package com.segurancarural.gpstracker.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.segurancarural.gpstracker.data.model.TelemetryRecord
import com.segurancarural.gpstracker.data.repository.FamilyPositionsRepository
import com.segurancarural.gpstracker.ui.model.DeviceMapStyle
import com.segurancarural.gpstracker.ui.model.FamilyDeviceMarker
import com.segurancarural.gpstracker.ui.model.FamilyRefreshStatus
import com.segurancarural.gpstracker.ui.model.MapDisplayData
import com.segurancarural.gpstracker.ui.model.MapMarkerDisplay
import com.segurancarural.gpstracker.ui.model.MapTimeFilter
import com.segurancarural.gpstracker.util.DEFAULT_MARKER_COLOR_ARGB
import com.segurancarural.gpstracker.util.PREF_DEVICE_LABEL
import com.segurancarural.gpstracker.util.PREF_DEVICE_MARKER_COLOR
import com.segurancarural.gpstracker.util.TRACKING_PREFS_NAME
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MapViewModel(application: Application) : AndroidViewModel(application) {

    private val db = (application as com.segurancarural.gpstracker.GpsTrackerApplication).database
    private val dao = db.telemetryDao()
    private val prefs = application.getSharedPreferences(TRACKING_PREFS_NAME, Application.MODE_PRIVATE)
    private val familyRepository = FamilyPositionsRepository()

    val timeFilter = MutableStateFlow(MapTimeFilter.TODAY)

    private val _findFamilyEnabled = MutableStateFlow(false)
    val findFamilyEnabled: StateFlow<Boolean> = _findFamilyEnabled.asStateFlow()

    private val _familyRefreshStatus = MutableStateFlow(FamilyRefreshStatus.Idle)
    val familyRefreshStatus: StateFlow<FamilyRefreshStatus> = _familyRefreshStatus.asStateFlow()

    private val _familyMarkers = MutableStateFlow<List<FamilyDeviceMarker>>(emptyList())
    val familyMarkers: StateFlow<List<FamilyDeviceMarker>> = _familyMarkers.asStateFlow()

    private val deviceStyle = MutableStateFlow(loadDeviceStyle())

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val rawMyRoute: StateFlow<List<TelemetryRecord>> = timeFilter
        .flatMapLatest { filter ->
            val calendar = java.util.Calendar.getInstance()
            val endMs: Long
            val startMs: Long
            when (filter) {
                MapTimeFilter.TODAY -> {
                    endMs = System.currentTimeMillis()
                    calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
                    calendar.set(java.util.Calendar.MINUTE, 0)
                    calendar.set(java.util.Calendar.SECOND, 0)
                    calendar.set(java.util.Calendar.MILLISECOND, 0)
                    startMs = calendar.timeInMillis
                }
                MapTimeFilter.YESTERDAY -> {
                    calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
                    calendar.set(java.util.Calendar.MINUTE, 0)
                    calendar.set(java.util.Calendar.SECOND, 0)
                    calendar.set(java.util.Calendar.MILLISECOND, 0)
                    endMs = calendar.timeInMillis - 1
                    calendar.add(java.util.Calendar.DAY_OF_YEAR, -1)
                    startMs = calendar.timeInMillis
                }
                MapTimeFilter.THIS_WEEK -> {
                    endMs = System.currentTimeMillis()
                    calendar.set(java.util.Calendar.DAY_OF_WEEK, calendar.firstDayOfWeek)
                    calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
                    calendar.set(java.util.Calendar.MINUTE, 0)
                    calendar.set(java.util.Calendar.SECOND, 0)
                    calendar.set(java.util.Calendar.MILLISECOND, 0)
                    startMs = calendar.timeInMillis
                }
            }
            dao.observeRouteHistoryBounded(startMs, endMs)
        }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val myRouteHistory: StateFlow<List<TelemetryRecord>> = rawMyRoute

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
        if (enabled) {
            _familyMarkers.value = emptyList() // Clear old markers to ensure fresh redraw
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
