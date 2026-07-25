package com.segurancarural.gpstracker.di

import com.segurancarural.gpstracker.IosPlatformDependencies
import com.segurancarural.gpstracker.Platform
import com.segurancarural.gpstracker.data.db.AppDatabase
import com.segurancarural.gpstracker.data.db.createAppDatabase
import com.segurancarural.gpstracker.data.network.ApiRoutes
import com.segurancarural.gpstracker.data.network.SharedConfig
import com.segurancarural.gpstracker.data.repository.FamilyPositionsRepository
import com.segurancarural.gpstracker.data.repository.FarmRepository
import com.segurancarural.gpstracker.data.repository.OfflineRequestManager
import com.segurancarural.gpstracker.data.repository.TelemetryRepository
import com.segurancarural.gpstracker.domain.usecase.SubmitLocationUseCase
import com.segurancarural.gpstracker.sync.SyncEngine
import com.segurancarural.gpstracker.ui.model.FamilyDeviceMarker
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.IDToken
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.context.startKoin
import org.koin.dsl.module

val iosModule = module {
    single<AppDatabase> { createAppDatabase() }
    single { get<AppDatabase>().telemetryDao() }
    single { TelemetryRepository(get()) }
    single {
        SyncEngine(
            dao = get(),
            httpClient = get(),
            locationUrl = ApiRoutes.LOCATION_V2,
            emergencyUrl = ApiRoutes.EMERGENCY,
            farmIdProvider = { get<FarmRepository>().currentFarmId }
        )
    }
}

object KoinHelper : KoinComponent {
    fun getTelemetryRepository(): TelemetryRepository = get()
    fun getFamilyPositionsRepository(): FamilyPositionsRepository = get()
    fun getSyncEngine(): SyncEngine = get()
    fun getFarmRepository(): FarmRepository = get()
    fun getDeviceConfigRepository(): com.segurancarural.gpstracker.data.repository.DeviceConfigRepository = get()
    fun getPushTokenRepository(): com.segurancarural.gpstracker.data.repository.PushTokenRepository = get()
    fun getSubmitLocationUseCase(): SubmitLocationUseCase = get()
}

fun getSubmitLocationUseCase(): SubmitLocationUseCase {
    return KoinHelper.getSubmitLocationUseCase()
}

fun getTelemetryRepository(): TelemetryRepository {
    return KoinHelper.getTelemetryRepository()
}

fun getFamilyPositionsRepository(): FamilyPositionsRepository {
    return KoinHelper.getFamilyPositionsRepository()
}

fun getDeviceConfigRepository(): com.segurancarural.gpstracker.data.repository.DeviceConfigRepository {
    return KoinHelper.getDeviceConfigRepository()
}

fun getPushTokenRepository(): com.segurancarural.gpstracker.data.repository.PushTokenRepository {
    return KoinHelper.getPushTokenRepository()
}

@Throws(Exception::class)
suspend fun fetchFamilyPositions(historyCount: Int = 10): List<FamilyDeviceMarker> {
    return getFamilyPositionsRepository().fetchLastPositions(historyCount).getOrThrow()
}

fun getOfflineRequestManager(): OfflineRequestManager {
    return OfflineRequestManager
}

fun getFarmRepository(): FarmRepository {
    return KoinHelper.getFarmRepository()
}

@Throws(Exception::class)
suspend fun fetchMyFarms(): com.segurancarural.gpstracker.data.repository.FarmDetailsResponse {
    return getFarmRepository().getFarmDetails().getOrThrow()
}

@Throws(Exception::class)
suspend fun syncCurrentDeviceToFarm(): com.segurancarural.gpstracker.data.repository.DeviceSyncResponse {
    return getFarmRepository().syncCurrentDeviceToFarm(getFarmRepository().currentFarmId).getOrThrow()
}

@Throws(Exception::class)
suspend fun fetchDeviceConfig(serialNumber: String): com.segurancarural.gpstracker.data.dto.DeviceConfigResponseDto {
    return getFarmRepository().getDeviceConfig(serialNumber).getOrThrow()
}

@Throws(Exception::class)
suspend fun createFarm(name: String?): com.segurancarural.gpstracker.data.repository.FarmResponse {
    return getFarmRepository().createFarm(name).getOrThrow()
}

@Throws(Exception::class)
suspend fun joinFarm(inviteCode: String): com.segurancarural.gpstracker.data.repository.FarmResponse {
    return getFarmRepository().joinFarm(inviteCode).getOrThrow()
}

@Throws(Exception::class)
suspend fun manageMember(farmId: String, targetUserId: String, action: String): com.segurancarural.gpstracker.data.repository.MemberActionResponse {
    return getFarmRepository().manageMember(farmId, targetUserId, action).getOrThrow()
}

fun getSyncEngine(): SyncEngine {
    return KoinHelper.getSyncEngine()
}

fun initKoin(
    supabaseUrl: String,
    supabaseKey: String,
    deviceApiSecret: String,
    backendBaseUrl: String,
    isDebug: Boolean
) {
    SharedConfig.SUPABASE_URL = supabaseUrl
    SharedConfig.SUPABASE_KEY = supabaseKey
    SharedConfig.DEVICE_API_SECRET = deviceApiSecret
    SharedConfig.BACKEND_BASE_URL = backendBaseUrl
    SharedConfig.IS_DEBUG = isDebug

    Platform.dependencies = IosPlatformDependencies()
    startKoin {
        modules(sharedModule, iosModule)
    }
}

@Throws(Exception::class)
suspend fun signInWithGoogleIdToken(idToken: String, accessToken: String?, nonce: String? = null) {
    try {
        println("🟢 [KoinIOS] Starting Supabase signInWith(IDToken)...")
        com.segurancarural.gpstracker.data.network.SupabaseClient.client.auth.signInWith(IDToken) {
            this.idToken = idToken
            this.provider = Google
            if (nonce != null) {
                this.nonce = nonce
            }
        }
        val token = com.segurancarural.gpstracker.data.network.SupabaseClient.client.auth.currentAccessTokenOrNull()
        if (token != null) {
            println("🟢 [KoinIOS] Supabase auth successful! Saving JWT.")
            com.segurancarural.gpstracker.data.network.ApiClient.supabaseJwt = token.toString()
            Platform.dependencies.setSupabaseJwt(token.toString())
        } else {
            println("⚠️ [KoinIOS] Supabase auth completed but currentAccessTokenOrNull is null.")
        }
    } catch (e: Exception) {
        println("❌ [KoinIOS] Supabase signInWithGoogleIdToken failed: ${e.message}")
        throw e
    }
}

@Throws(Exception::class)
suspend fun signOutFromSupabase() {
    try {
        com.segurancarural.gpstracker.data.network.SupabaseClient.client.auth.signOut()
    } catch (e: Exception) {
        println("⚠️ [KoinIOS] Error during signOutFromSupabase: ${e.message}")
    }
    com.segurancarural.gpstracker.data.network.ApiClient.supabaseJwt = null
    try { Platform.dependencies.setSupabaseJwt(null) } catch (e: Exception) {}
}
