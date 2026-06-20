package com.segurancarural.gpstracker.data.network

import com.segurancarural.gpstracker.BuildConfig
import com.segurancarural.gpstracker.util.AppLog
import io.github.jan.supabase.auth.auth
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

object ApiClient {
    // Dynamically set by SettingsViewModel or Auth interceptors
    var supabaseJwt: String? = null
    var farmId: String? = null

    val httpClient: HttpClient by lazy {
        HttpClient(Android) {
            engine {
                connectTimeout = 10_000
                socketTimeout = 15_000
            }
            install(io.ktor.client.plugins.auth.Auth) {
                bearer {
                    loadTokens {
                        val session = com.segurancarural.gpstracker.data.network.SupabaseClient.client.auth.currentSessionOrNull()
                        val defaultToken = supabaseJwt ?: BuildConfig.DEVICE_API_SECRET
                        BearerTokens(session?.accessToken ?: defaultToken, session?.refreshToken ?: "")
                    }
                    refreshTokens {
                        try {
                            com.segurancarural.gpstracker.data.network.SupabaseClient.client.auth.refreshCurrentSession()
                            val newSession = com.segurancarural.gpstracker.data.network.SupabaseClient.client.auth.currentSessionOrNull()
                            val defaultToken = supabaseJwt ?: BuildConfig.DEVICE_API_SECRET
                            BearerTokens(newSession?.accessToken ?: defaultToken, newSession?.refreshToken ?: "")
                        } catch (e: Exception) {
                            com.segurancarural.gpstracker.util.AppLog.e("KtorAuth", "Failed to refresh session: ${e.message}")
                            null
                        }
                    }
                }
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
