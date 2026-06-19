package com.segurancarural.gpstracker.data.repository

import android.content.Context
import com.segurancarural.gpstracker.data.network.ApiClient
import com.segurancarural.gpstracker.data.network.ApiRoutes
import com.segurancarural.gpstracker.util.TRACKING_PREFS_NAME
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.Serializable

@Serializable
data class JoinFarmRequest(val inviteCode: String)

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

    suspend fun createFarm(): Result<FarmResponse> {
        return try {
            val response = ApiClient.httpClient.post("${ApiRoutes.BASE}/api/auth/create-farm")
            val data = response.body<FarmResponse>()
            if (data.success && data.session != null) {
                saveAuth(data.session.access_token, data.farmId)
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
            val response = ApiClient.httpClient.post("${ApiRoutes.BASE}/api/auth/join-farm") {
                contentType(ContentType.Application.Json)
                setBody(JoinFarmRequest(inviteCode))
            }
            val data = response.body<FarmResponse>()
            if (data.success && data.session != null) {
                saveAuth(data.session.access_token, data.farmId)
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
}
