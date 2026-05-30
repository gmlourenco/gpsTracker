package com.segurancarural.gpstracker.util

object SosManager {
    /** Hold duration required to activate SOS mode (0.2 seconds). */
    const val ACTIVATION_HOLD_MS = 200L

    /** Hold duration required to initiate deactivation verification (10 seconds). */
    const val DEACTIVATION_HOLD_MS = 10_000L

    /**
     * Generates a random 6-digit verification code.
     */
    fun generateVerificationCode(): String {
        return (100000..999999).random().toString()
    }
}
