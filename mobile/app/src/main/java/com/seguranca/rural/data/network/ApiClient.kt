package com.seguranca.rural.data.network

import android.util.Log
import com.seguranca.rural.BuildConfig
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
            install(Logging) {
                logger = object : Logger {
                    override fun log(message: String) {
                        Log.d("Ktor", message)
                    }
                }
                level = LogLevel.ALL
            }
        }
    }
}
