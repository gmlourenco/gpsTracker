package com.segurancarural.gpstracker.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.segurancarural.gpstracker.data.repository.TelemetryRepository
import com.segurancarural.gpstracker.data.repository.TrackingStateRepository
import com.segurancarural.gpstracker.util.NetworkMonitor
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    
    val isTracking: StateFlow<Boolean> = TrackingStateRepository.isTracking
    val isSosActive: StateFlow<Boolean> = TrackingStateRepository.isSosActive
    val isPreSosActive: StateFlow<Boolean> = TrackingStateRepository.isPreSosActive
    val preSosCountdown: StateFlow<Int> = TrackingStateRepository.preSosCountdown
    val lastAccuracy: StateFlow<Float?> = TrackingStateRepository.lastAccuracy

    private val telemetryRepository = TelemetryRepository((application as com.segurancarural.gpstracker.GpsTrackerApplication).database.telemetryDao())
    private val networkMonitor = NetworkMonitor(application)

    val unsyncedCount: StateFlow<Int> = telemetryRepository.getUnsyncedCountFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
        
    val isOnline: StateFlow<Boolean> = networkMonitor.isOnline
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    // The UI now reads purely from this ViewModel, isolating it completely from the
    // internals of the LocationForegroundService.
}
