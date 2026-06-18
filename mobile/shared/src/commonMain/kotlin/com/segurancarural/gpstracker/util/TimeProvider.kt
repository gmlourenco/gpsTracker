package com.segurancarural.gpstracker.util

/**
 * KMP-safe epoch milliseconds provider.
 *
 * Replaces direct `System.currentTimeMillis()` in commonMain,
 * which is a JVM-only API that blocks iOS compilation.
 */
expect fun currentTimeMillis(): Long
