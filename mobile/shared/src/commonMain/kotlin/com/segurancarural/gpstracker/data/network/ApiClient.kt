package com.segurancarural.gpstracker.data.network

import com.segurancarural.gpstracker.Platform
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

    /**
     * Lightweight HTTP client for telemetry/location submissions.
     * Uses ONLY the device API secret — no JWT, no session, no refresh.
     * This guarantees location updates never fail due to auth expiration.
     */
    val telemetryClient: HttpClient by lazy {
        HttpClient {
            install(Auth) {
                bearer {
                    loadTokens {
                        BearerTokens(SharedConfig.DEVICE_API_SECRET, "")
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
                            KmpLogger.d("KtorTelemetry", message)
                        }
                    }
                    level = LogLevel.HEADERS
                }
            }
        }
    }

    val httpClient: HttpClient by lazy {
        HttpClient {
            install(Auth) {
                bearer {
                    loadTokens {
                        val session = SupabaseClient.client.auth.currentSessionOrNull()
                        val platformJwt = try { Platform.dependencies.getSupabaseJwt() } catch (e: Exception) { null }
                        val userJwt = session?.accessToken ?: supabaseJwt ?: platformJwt
                        val token = userJwt ?: SharedConfig.DEVICE_API_SECRET
                        BearerTokens(token, session?.refreshToken ?: "")
                    }
                    refreshTokens {
                        try {
                            val session = SupabaseClient.client.auth.currentSessionOrNull()
                            val platformJwt = try { Platform.dependencies.getSupabaseJwt() } catch (e: Exception) { null }
                            if (session?.refreshToken != null && session.refreshToken.isNotEmpty()) {
                                SupabaseClient.client.auth.refreshCurrentSession()
                                val newSession = SupabaseClient.client.auth.currentSessionOrNull()
                                val userJwt = newSession?.accessToken ?: supabaseJwt ?: platformJwt
                                val token = userJwt ?: SharedConfig.DEVICE_API_SECRET
                                if (newSession?.accessToken != null) {
                                    supabaseJwt = newSession.accessToken
                                    try { Platform.dependencies.setSupabaseJwt(newSession.accessToken) } catch (e: Exception) {}
                                }
                                BearerTokens(token, newSession?.refreshToken ?: "")
                            } else {
                                val userJwt = supabaseJwt ?: platformJwt
                                if (userJwt != null) {
                                    BearerTokens(userJwt, "")
                                } else {
                                    null
                                }
                            }
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
