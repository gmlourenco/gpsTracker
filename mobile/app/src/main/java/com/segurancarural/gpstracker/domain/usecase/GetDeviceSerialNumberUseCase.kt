package com.segurancarural.gpstracker.domain.usecase

import android.content.Context
import com.segurancarural.gpstracker.util.ensureSerialNumber

/**
 * Provides the stable device serial number (ANDROID_ID).
 * Use this use case in every service/repository that needs to identify this device.
 */
class GetDeviceSerialNumberUseCase(private val context: Context) {
    operator fun invoke(): String = context.ensureSerialNumber()
}
