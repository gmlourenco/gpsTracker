package com.segurancarural.gpstracker.data.repository

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object TrackingStateRepository {
    private val _isTracking = MutableStateFlow(false)
    val isTracking: StateFlow<Boolean> = _isTracking.asStateFlow()

    private val _isSosActive = MutableStateFlow(false)
    val isSosActive: StateFlow<Boolean> = _isSosActive.asStateFlow()

    private val _isPreSosActive = MutableStateFlow(false)
    val isPreSosActive: StateFlow<Boolean> = _isPreSosActive.asStateFlow()

    private val _preSosCountdown = MutableStateFlow(15)
    val preSosCountdown: StateFlow<Int> = _preSosCountdown.asStateFlow()

    private val _lastAccuracy = MutableStateFlow<Float?>(null)
    val lastAccuracy: StateFlow<Float?> = _lastAccuracy.asStateFlow()

    fun setTracking(active: Boolean) {
        _isTracking.value = active
    }

    fun setSosActive(active: Boolean) {
        _isSosActive.value = active
    }

    fun setPreSosActive(active: Boolean) {
        _isPreSosActive.value = active
    }

    fun setPreSosCountdown(seconds: Int) {
        _preSosCountdown.value = seconds
    }

    fun setLastAccuracy(accuracy: Float?) {
        _lastAccuracy.value = accuracy
    }
}
