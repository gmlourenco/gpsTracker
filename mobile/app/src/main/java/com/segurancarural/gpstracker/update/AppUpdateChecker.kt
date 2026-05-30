package com.segurancarural.gpstracker.update

import com.segurancarural.gpstracker.BuildConfig
import com.segurancarural.gpstracker.data.network.ApiClient
import com.segurancarural.gpstracker.util.AppLog
import com.segurancarural.gpstracker.util.isRemoteVersionNewer
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AppVersionResponse(
    val success: Boolean = false,
    @SerialName("latestVersion") val latestVersion: String = "",
    @SerialName("minVersion") val minVersion: String = "",
    @SerialName("downloadUrl") val downloadUrl: String = "",
    @SerialName("releaseNotes") val releaseNotes: String = "",
    @SerialName("updateAvailable") val updateAvailable: Boolean = false,
    @SerialName("forceUpdate") val forceUpdate: Boolean = false,
)

data class AppUpdateOffer(
    val latestVersion: String,
    val downloadUrl: String,
    val releaseNotes: String,
    val forceUpdate: Boolean,
)

object AppUpdateChecker {
    suspend fun checkForUpdate(currentVersion: String = BuildConfig.VERSION_NAME): AppUpdateOffer? =
        withContext(Dispatchers.IO) {
            try {
                val url =
                    "${BuildConfig.BACKEND_BASE_URL}/api/app/version?current=${currentVersion}"
                val response: AppVersionResponse = ApiClient.httpClient.get(url).body()
                if (!response.success) return@withContext null
                if (response.downloadUrl.isBlank()) {
                    AppLog.d("AppUpdateChecker", "No download URL configured — skip update prompt")
                    return@withContext null
                }
                val needsUpdate = (response.latestVersion.isNotBlank() && response.latestVersion != currentVersion) ||
                    response.forceUpdate ||
                    response.updateAvailable
                if (!needsUpdate) return@withContext null

                AppUpdateOffer(
                    latestVersion = response.latestVersion,
                    downloadUrl = response.downloadUrl,
                    releaseNotes = response.releaseNotes,
                    forceUpdate = response.forceUpdate,
                )
            } catch (e: Exception) {
                AppLog.w("AppUpdateChecker", "Version check failed: ${e.message}", e)
                null
            }
        }
}
