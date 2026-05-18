package com.seguranca.rural.domain.usecase

import com.seguranca.rural.data.model.TelemetryRecord
import com.seguranca.rural.data.repository.TelemetryRepository

class SubmitLocationUseCase(private val repository: TelemetryRepository) {
    suspend operator fun invoke(record: TelemetryRecord) {
        // Here we could add additional domain business logic, data validation, etc.
        repository.submitLocation(record)
    }
}
