package com.segurancarural.gpstracker.data.network

import com.segurancarural.gpstracker.util.KmpLogger
import io.github.jan.supabase.auth.auth
import io.ktor.client.HttpClient
import io.ktor.client.plugins.auth.Auth
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
        HttpClient {
            install(Auth) {
                bearer {
                    loadTokens {
                        val session = SupabaseClient.client.auth.currentSessionOrNull()
                        val defaultToken = supabaseJwt ?: SharedConfig.DEVICE_API_SECRET
                        BearerTokens(session?.accessToken ?: defaultToken, session?.refreshToken ?: "")
                    }
                    refreshTokens {
                        try {
                            SupabaseClient.client.auth.refreshCurrentSession()
                            val newSession = SupabaseClient.client.auth.currentSessionOrNull()
                            val defaultToken = supabaseJwt ?: SharedConfig.DEVICE_API_SECRET
                            BearerTokens(newSession?.accessToken ?: defaultToken, newSession?.refreshToken ?: "")
                        } catch (e: Exception) {
                            KmpLogger.e("KtorAuth", "Failed to refresh session: ${e.message}", e)
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
            if (SharedConfig.IS_DEBUG) {
                install(Logging) {
                    logger = object : Logger {
                        override fun log(message: String) {
                            KmpLogger.d("Ktor", message)
                        }
                    }
                    level = LogLevel.HEADERS
                }
            }
        }
    }
}
