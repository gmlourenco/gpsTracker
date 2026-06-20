package com.segurancarural.gpstracker.data.repository

import android.content.Context
import com.segurancarural.gpstracker.data.network.ApiClient
import com.segurancarural.gpstracker.data.network.ApiRoutes
import com.segurancarural.gpstracker.util.TRACKING_PREFS_NAME
import com.segurancarural.gpstracker.util.ensureSerialNumber
import io.github.jan.supabase.auth.auth
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.Serializable

@Serializable
data class JoinFarmRequest(val inviteCode: String)

@Serializable
data class CreateFarmRequest(val name: String)

@Serializable
data class FarmResponse(
    val success: Boolean,
    val session: SupabaseSession? = null,
    val farmId: String? = null,
    val inviteCode: String? = null,
    val error: String? = null
)

@Serializable
data class SupabaseSession(
    val access_token: String,
    val refresh_token: String,
    val expires_in: Int
)

class FarmRepository(private val context: Context) {
    private val prefs = context.getSharedPreferences(TRACKING_PREFS_NAME, Context.MODE_PRIVATE)

    init {
        // Load on init
        ApiClient.supabaseJwt = prefs.getString("supabase_jwt", null)
        ApiClient.farmId = prefs.getString("farm_id", null)
    }

    suspend fun createFarm(name: String? = null): Result<FarmResponse> {
        return try {
            val token = com.segurancarural.gpstracker.data.network.SupabaseClient.client.auth.currentAccessTokenOrNull()
            if (token != null) {
                ApiClient.supabaseJwt = token.toString()
            }
            
            val response = ApiClient.httpClient.post("${ApiRoutes.BASE}/api/auth/create-farm") {
                if (!name.isNullOrBlank()) {
                    contentType(ContentType.Application.Json)
                    setBody(CreateFarmRequest(name))
                }
            }
            val data = response.body<FarmResponse>()
            if (data.success) {
                val validToken = token?.toString() ?: data.session?.access_token
                if (validToken.isNullOrEmpty()) {
                     return Result.failure(Exception("Falha na extração de credenciais seguras."))
                }
                saveAuth(validToken, data.farmId)
                Result.success(data)
            } else {
                Result.failure(Exception(data.error ?: "Unknown error"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun joinFarm(inviteCode: String): Result<FarmResponse> {
        return try {
            val token = com.segurancarural.gpstracker.data.network.SupabaseClient.client.auth.currentAccessTokenOrNull()
            if (token != null) {
                ApiClient.supabaseJwt = token.toString()
            }

            val response = ApiClient.httpClient.post("${ApiRoutes.BASE}/api/auth/join-farm") {
                contentType(ContentType.Application.Json)
                setBody(JoinFarmRequest(inviteCode))
            }
            val data = response.body<FarmResponse>()
            if (data.success) {
                val validToken = token?.toString() ?: data.session?.access_token
                if (validToken.isNullOrEmpty()) {
                     return Result.failure(Exception("Falha na extração de credenciais seguras."))
                }
                saveAuth(validToken, data.farmId)
                Result.success(data)
            } else {
                Result.failure(Exception(data.error ?: "Unknown error"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun hasFarm(): Boolean {
        return prefs.getString("farm_id", null) != null
    }

    private fun saveAuth(jwt: String, farmId: String?) {
        ApiClient.supabaseJwt = jwt
        ApiClient.farmId = farmId
        prefs.edit()
            .putString("supabase_jwt", jwt)
            .putString("farm_id", farmId)
            .apply()
    }

    suspend fun syncDevices(
        deviceId: String,
        label: String,
        markerColor: String,
        trackingEnabled: Boolean,
        appVersion: String,
        farmId: String?
    ): Result<DeviceSyncResponse> {
        return try {
            val token = com.segurancarural.gpstracker.data.network.SupabaseClient.client.auth.currentAccessTokenOrNull()
            if (token != null) {
                ApiClient.supabaseJwt = token.toString()
            }

            val requestBody = DeviceSyncRequest(
                deviceId = deviceId,
                config = DeviceConfigPayload(
                    label = label,
                    markerColor = markerColor,
                    trackingEnabled = trackingEnabled,
                    appVersion = appVersion,
                    farmId = farmId
                )
            )

            val response = ApiClient.httpClient.post("${ApiRoutes.BASE}/api/devices/sync") {
                contentType(ContentType.Application.Json)
                setBody(requestBody)
            }
            val data = response.body<DeviceSyncResponse>()
            if (data.success) {
                Result.success(data)
            } else {
                Result.failure(Exception(data.error ?: "Failed to sync devices"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun syncCurrentDeviceToFarm(farmId: String?): Result<DeviceSyncResponse> {
        val deviceId = context.ensureSerialNumber()
        val label = prefs.getString("device_label", "Dispositivo") ?: "Dispositivo"
        val markerColorArgb = prefs.getInt(com.segurancarural.gpstracker.util.PREF_DEVICE_MARKER_COLOR, com.segurancarural.gpstracker.util.DEFAULT_MARKER_COLOR_ARGB)
        val markerColor = com.segurancarural.gpstracker.util.argbToMapLibreHex(markerColorArgb)
        val trackingEnabled = true
        val appVersion = com.segurancarural.gpstracker.BuildConfig.VERSION_NAME

        ApiClient.farmId = farmId
        prefs.edit().putString("farm_id", farmId).apply()

        return syncDevices(deviceId, label, markerColor, trackingEnabled, appVersion, farmId)
    }


    suspend fun getFarmDetails(): Result<FarmDetailsResponse> {
        return try {
            val token = com.segurancarural.gpstracker.data.network.SupabaseClient.client.auth.currentAccessTokenOrNull()
            if (token != null) {
                ApiClient.supabaseJwt = token.toString()
            }

            val response = ApiClient.httpClient.get("${ApiRoutes.BASE}/api/farms/details")
            if (response.status.value !in 200..299) {
                return Result.failure(Exception("HTTP Error: ${response.status.value}"))
            }
            val data = response.body<FarmDetailsResponse>()
            if (data.success) {
                Result.success(data)
            } else {
                Result.failure(Exception(data.error ?: "Unknown error"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

@Serializable
data class DeviceSyncRequest(
    val deviceId: String,
    val config: DeviceConfigPayload
)

@Serializable
data class DeviceConfigPayload(
    val label: String,
    val markerColor: String,
    val trackingEnabled: Boolean,
    val appVersion: String,
    val farmId: String? = null
)

@Serializable
data class DeviceSyncResponse(
    val success: Boolean,
    val devices: List<DeviceDto> = emptyList(),
    val error: String? = null
)

@Serializable
data class DeviceDto(
    val id: String,
    val label: String,
    val marker_color: String,
    val tracking_enabled: Boolean,
    val farm_id: String? = null
)

@Serializable
data class FarmDto(
    val farmId: String,
    val farmName: String,
    val userRole: String,
    val inviteCode: String? = null,
    val members: List<FarmMemberDto> = emptyList()
)

@Serializable
data class FarmDetailsResponse(
    val success: Boolean,
    val isAnonymous: Boolean = false,
    val farms: List<FarmDto> = emptyList(),
    val error: String? = null
)

@Serializable
data class FarmMemberDto(
    val user_id: String,
    val role: String
)
