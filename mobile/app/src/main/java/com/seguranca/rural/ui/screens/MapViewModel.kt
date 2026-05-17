package com.seguranca.rural.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.seguranca.rural.data.db.createAppDatabase
import com.seguranca.rural.data.model.TelemetryRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn

class MapViewModel(application: Application) : AndroidViewModel(application) {

    private val db = createAppDatabase(application)
    private val dao = db.telemetryDao()

    val timeFilter = MutableStateFlow(MapTimeFilter.TODAY)

    @OptIn(ExperimentalCoroutinesApi::class)
    val routeHistory: StateFlow<List<TelemetryRecord>> = timeFilter
        .flatMapLatest { filter ->
            val sinceMs = calculateSinceMs(filter)
            dao.observeRouteHistory(sinceMs)
        }
        .flowOn(Dispatchers.IO)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private fun calculateSinceMs(filter: MapTimeFilter): Long {
        val now = System.currentTimeMillis()
        val oneHourMs = 60 * 60 * 1000L
        return when (filter) {
            MapTimeFilter.TODAY -> {
                // Approximate "today" as last 12 hours for a working shift
                now - (12 * oneHourMs)
            }
            MapTimeFilter.LAST_24H -> {
                now - (24 * oneHourMs)
            }
            MapTimeFilter.WEEKLY -> {
                now - (7 * 24 * oneHourMs)
            }
        }
    }
}
