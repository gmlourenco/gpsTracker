package com.segurancarural.gpstracker.update

import com.segurancarural.gpstracker.BuildConfig
import com.segurancarural.gpstracker.data.network.ApiRoutes
import com.segurancarural.gpstracker.data.network.ApiService
import com.segurancarural.gpstracker.data.network.ApiResult
import com.segurancarural.gpstracker.data.dto.AppVersionResponseDto
import com.segurancarural.gpstracker.util.AppLog
import com.segurancarural.gpstracker.util.isRemoteVersionNewer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class AppUpdateOffer(
    val latestVersion: String,
    val downloadUrl: String,
    val releaseNotes: String,
    val forceUpdate: Boolean,
)

object AppUpdateChecker {
    private val apiService = ApiService()

    suspend fun checkForUpdate(currentVersion: String = BuildConfig.VERSION_NAME): AppUpdateOffer? =
        withContext(Dispatchers.IO) {
            val result = apiService.get<AppVersionResponseDto>(ApiRoutes.appVersion(currentVersion))
            when (result) {
                is ApiResult.Success -> {
                    val response = result.data
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
                }
                is ApiResult.HttpError -> {
                    AppLog.e("AppUpdateChecker", "Version check failed: ${result.code}")
                    null
                }
                is ApiResult.NetworkError -> {
                    AppLog.w("AppUpdateChecker", "Version check exception: ${result.exception.message}", result.exception)
                    null
                }
                is ApiResult.Unauthorized -> {
                    AppLog.e("AppUpdateChecker", "Unauthorized version check attempt")
                    null
                }
            }
        }
}
