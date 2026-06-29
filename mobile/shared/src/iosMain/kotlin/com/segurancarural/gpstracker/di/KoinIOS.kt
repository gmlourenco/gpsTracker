package com.segurancarural.gpstracker.di

import com.segurancarural.gpstracker.IosPlatformDependencies
import com.segurancarural.gpstracker.Platform
import com.segurancarural.gpstracker.data.db.AppDatabase
import com.segurancarural.gpstracker.data.db.createAppDatabase
import com.segurancarural.gpstracker.data.network.SharedConfig
import com.segurancarural.gpstracker.data.repository.FamilyPositionsRepository
import com.segurancarural.gpstracker.data.repository.OfflineRequestManager
import com.segurancarural.gpstracker.data.repository.TelemetryRepository
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.context.startKoin
import org.koin.dsl.module

val iosModule = module {
    single<AppDatabase> { createAppDatabase() }
    single { get<AppDatabase>().telemetryDao() }
    single { TelemetryRepository(get()) }
}


object KoinHelper : KoinComponent {
    fun getTelemetryRepository(): TelemetryRepository = get()
    fun getFamilyPositionsRepository(): FamilyPositionsRepository = get()
}

fun getTelemetryRepository(): TelemetryRepository {
    return KoinHelper.getTelemetryRepository()
}

fun getFamilyPositionsRepository(): FamilyPositionsRepository {
    return KoinHelper.getFamilyPositionsRepository()
}

fun getOfflineRequestManager(): OfflineRequestManager {
    return OfflineRequestManager
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
