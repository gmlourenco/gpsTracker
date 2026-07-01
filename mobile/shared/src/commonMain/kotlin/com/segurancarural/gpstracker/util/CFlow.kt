package com.segurancarural.gpstracker.util

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * Wrapper to consume Kotlin Flows from Swift.
 * Since we cannot use SKIE due to Kotlin version incompatibilities,
 * this class allows Swift to pass a closure `onEach` to receive emissions.
 */
class CFlow<T>(private val origin: Flow<T>) : Flow<T> by origin {
    fun watch(block: (T) -> Unit): Closeable {
        val job = Job()
        val scope = CoroutineScope(Dispatchers.Main + job)
        onEach {
            block(it)
        }.launchIn(scope)

        return object : Closeable {
            override fun close() {
                job.cancel()
            }
        }
    }
}

interface Closeable {
    fun close()
}

fun <T> Flow<T>.asCFlow(): CFlow<T> = CFlow(this)
