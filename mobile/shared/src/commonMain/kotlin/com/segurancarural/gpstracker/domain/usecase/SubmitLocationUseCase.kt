package com.segurancarural.gpstracker.domain.usecase

import com.segurancarural.gpstracker.data.model.TelemetryRecord
import com.segurancarural.gpstracker.data.repository.TelemetryRepository
import com.segurancarural.gpstracker.util.FilterResult
import com.segurancarural.gpstracker.util.GpsLocationFilter

class SubmitLocationUseCase(
    private val repository: TelemetryRepository,
    val gpsFilter: GpsLocationFilter = GpsLocationFilter()
) {
    suspend operator fun invoke(record: TelemetryRecord): FilterResult {
        val result = gpsFilter.process(record)
        when (result) {
            is FilterResult.Accept -> {
                repository.submitLocation(result.record)
            }
            is FilterResult.DiscardRedundant -> {
                // Redundant stationary jitter; suppressed to keep database and server queues clean
            }
            is FilterResult.SuspiciousJumpRecheck -> {
                // Suspicious jump / Wi-Fi bounce detected; caller can handle rapid re-check
            }
        }
        return result
    }
}
