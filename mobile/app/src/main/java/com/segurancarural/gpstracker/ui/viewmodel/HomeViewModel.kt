package com.segurancarural.gpstracker.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.segurancarural.gpstracker.data.repository.TrackingStateRepository
import kotlinx.coroutines.flow.StateFlow

class HomeViewModel : ViewModel() {
    
    val isTracking: StateFlow<Boolean> = TrackingStateRepository.isTracking
    val isSosActive: StateFlow<Boolean> = TrackingStateRepository.isSosActive
    val lastAccuracy: StateFlow<Float?> = TrackingStateRepository.lastAccuracy

    // The UI now reads purely from this ViewModel, isolating it completely from the
    // internals of the LocationForegroundService.
}
