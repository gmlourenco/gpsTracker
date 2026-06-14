package com.segurancarural.gpstracker.util

import platform.Foundation.NSDate
import platform.Foundation.timeIntervalSince1970

/**
 * iOS actual — uses NSDate epoch conversion.
 */
actual fun currentTimeMillis(): Long =
    (NSDate().timeIntervalSince1970 * 1000).toLong()
