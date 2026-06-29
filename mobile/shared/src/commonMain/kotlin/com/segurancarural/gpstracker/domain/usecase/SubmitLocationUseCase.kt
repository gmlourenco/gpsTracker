package com.segurancarural.gpstracker.domain.usecase

import com.segurancarural.gpstracker.data.model.TelemetryRecord
import com.segurancarural.gpstracker.data.repository.TelemetryRepository

class SubmitLocationUseCase(private val repository: TelemetryRepository) {
    suspend operator fun invoke(record: TelemetryRecord) {
        // Here we could add additional domain business logic, data validation, etc.
        repository.submitLocation(record)
    }
}
