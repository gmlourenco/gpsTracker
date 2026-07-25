package com.segurancarural.gpstracker.data.repository

import com.segurancarural.gpstracker.Platform
import com.segurancarural.gpstracker.data.network.ApiClient
import com.segurancarural.gpstracker.data.network.ApiRoutes
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

class FarmRepository() {
    
    val currentFarmId: String? get() = Platform.dependencies.getFarmId()

    init {
        // Load on init
        ApiClient.supabaseJwt = Platform.dependencies.getSupabaseJwt()
        ApiClient.farmId = Platform.dependencies.getFarmId()
    }

    @Throws(Exception::class)
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

    @Throws(Exception::class)
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
        return Platform.dependencies.getFarmId() != null
    }

    private fun saveAuth(jwt: String, farmId: String?) {
        ApiClient.supabaseJwt = jwt
        ApiClient.farmId = farmId
        Platform.dependencies.setSupabaseJwt(jwt)
        Platform.dependencies.setFarmId(farmId)
    }

    @Throws(Exception::class)
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

    @Throws(Exception::class)
    suspend fun getDeviceConfig(serialNumber: String): Result<com.segurancarural.gpstracker.data.dto.DeviceConfigResponseDto> {
        return try {
            val token = com.segurancarural.gpstracker.data.network.SupabaseClient.client.auth.currentAccessTokenOrNull()
            if (token != null) {
                ApiClient.supabaseJwt = token.toString()
            }

            val response = ApiClient.httpClient.get("${ApiRoutes.DEVICE_CONFIG}?serialNumber=$serialNumber")
            val data = response.body<com.segurancarural.gpstracker.data.dto.DeviceConfigResponseDto>()
            if (data.success) {
                Result.success(data)
            } else {
                Result.failure(Exception(data.error ?: "Failed to fetch device config"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    @Throws(Exception::class)
    suspend fun syncCurrentDeviceToFarm(farmId: String?): Result<DeviceSyncResponse> {
        val deviceId = Platform.dependencies.ensureSerialNumber()
        val label = Platform.dependencies.getDeviceLabel()
        val markerColor = Platform.dependencies.getDeviceMarkerColorHex() ?: "#0000FF"
        val trackingEnabled = true
        val appVersion = Platform.dependencies.getAppVersion()

        ApiClient.farmId = farmId
        Platform.dependencies.setFarmId(farmId)

        return syncDevices(deviceId, label, markerColor, trackingEnabled, appVersion, farmId)
    }


    @Throws(Exception::class)
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

    @Throws(Exception::class)
    suspend fun manageMember(farmId: String, targetUserId: String, action: String): Result<MemberActionResponse> {
        return try {
            val token = com.segurancarural.gpstracker.data.network.SupabaseClient.client.auth.currentAccessTokenOrNull()
            if (token != null) ApiClient.supabaseJwt = token.toString()

            val response = ApiClient.httpClient.post("${ApiRoutes.BASE}/api/farms/members") {
                contentType(ContentType.Application.Json)
                setBody(MemberActionRequest(farmId, targetUserId, action))
            }
            val data = response.body<MemberActionResponse>()
            if (data.success) Result.success(data)
            else Result.failure(Exception(data.error ?: "Unknown error"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    @Throws(Exception::class)
    suspend fun generateInvite(farmId: String): Result<GenerateInviteResponse> {
        return try {
            val token = com.segurancarural.gpstracker.data.network.SupabaseClient.client.auth.currentAccessTokenOrNull()
            if (token != null) ApiClient.supabaseJwt = token.toString()

            val response = ApiClient.httpClient.post("${ApiRoutes.BASE}/api/farms/invite") {
                contentType(ContentType.Application.Json)
                setBody(GenerateInviteRequest(farmId))
            }
            val data = response.body<GenerateInviteResponse>()
            if (data.success) Result.success(data)
            else Result.failure(Exception(data.error ?: "Unknown error"))
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
    val devices: List<com.segurancarural.gpstracker.data.dto.DeviceDto> = emptyList(),
    val error: String? = null
)

@Serializable
data class FarmMemberDto(
    val userId: String = "",
    val displayName: String? = null,
    val isCreator: Boolean = false,
    val isMasterAdmin: Boolean = false,
    val isAdmin: Boolean = false,
    val isAuthenticated: Boolean = true,
    // Legacy compat — old API returns this, new API also includes it
    val role: String = "viewer",
    // Old field name compat (backend may still send snake_case)
    @kotlinx.serialization.SerialName("user_id")
    val userIdLegacy: String? = null,
) {
    val resolvedUserId: String get() = userId.ifEmpty { userIdLegacy ?: "" }
}

@Serializable
data class MyTagsDto(
    val isCreator: Boolean = false,
    val isMasterAdmin: Boolean = false,
    val isAdmin: Boolean = false,
    val isAuthenticated: Boolean = true,
)

@Serializable
data class FarmDto(
    val farmId: String,
    val farmName: String,
    val userRole: String = "viewer", // Legacy compat
    val myTags: MyTagsDto = MyTagsDto(),
    val inviteCode: String? = null,
    val inviteExpiresAt: String? = null,
    val inviteUsesRemaining: Int? = null,
    val members: List<FarmMemberDto> = emptyList(),
) {
    val canInvite: Boolean get() = myTags.isAdmin || myTags.isMasterAdmin || myTags.isCreator
    val canKick: Boolean get() = myTags.isAdmin || myTags.isMasterAdmin
    val canPromote: Boolean get() = myTags.isAdmin || myTags.isMasterAdmin
    val canPromoteMaster: Boolean get() = myTags.isMasterAdmin
}

@Serializable
data class FarmDetailsResponse(
    val success: Boolean,
    val isAnonymous: Boolean = false,
    val currentUserId: String? = null,
    val farms: List<FarmDto> = emptyList(),
    val error: String? = null,
)

// Request DTOs for member management
@Serializable
data class MemberActionRequest(
    val farmId: String,
    val targetUserId: String,
    val action: String, // "kick", "promote_admin", "demote_admin", "promote_master_admin", "demote_master_admin"
)

@Serializable
data class MemberActionResponse(
    val success: Boolean,
    val message: String? = null,
    val error: String? = null,
)

@Serializable
data class GenerateInviteRequest(
    val farmId: String
)

@Serializable
data class GenerateInviteResponse(
    val success: Boolean,
    val inviteCode: String? = null,
    val expiresAt: String? = null,
    val error: String? = null
)

