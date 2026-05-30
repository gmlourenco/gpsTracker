package com.segurancarural.gpstracker.data.network

import com.segurancarural.gpstracker.BuildConfig
import com.segurancarural.gpstracker.util.AppLog
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.bearerAuth
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

object ApiClient {
    val httpClient: HttpClient by lazy {
        HttpClient(Android) {
            engine {
                connectTimeout = 10_000
                socketTimeout = 15_000
            }
            defaultRequest {
                bearerAuth(BuildConfig.DEVICE_API_SECRET)
            }
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                })
            }
            if (BuildConfig.DEBUG) {
                install(Logging) {
                    logger = object : Logger {
                        override fun log(message: String) {
                            AppLog.d("Ktor", message)
                        }
                    }
                    level = LogLevel.HEADERS
                }
            }
        }
    }
}
