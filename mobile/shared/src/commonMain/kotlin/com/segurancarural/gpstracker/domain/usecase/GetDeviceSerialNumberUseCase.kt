package com.segurancarural.gpstracker.domain.usecase

import com.segurancarural.gpstracker.Platform

class GetDeviceSerialNumberUseCase {
    operator fun invoke(): String = Platform.dependencies.ensureSerialNumber()
}
