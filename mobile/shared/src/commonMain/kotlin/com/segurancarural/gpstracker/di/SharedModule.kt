package com.segurancarural.gpstracker.di

import com.segurancarural.gpstracker.data.network.ApiClient
import com.segurancarural.gpstracker.data.repository.FamilyPositionsRepository
import com.segurancarural.gpstracker.data.repository.FarmRepository
import io.ktor.client.HttpClient
import org.koin.dsl.module

val sharedModule = module {
    single<HttpClient> { ApiClient.httpClient }
    single { FarmRepository() }
    single { FamilyPositionsRepository() }
    
    // We can't provide TelemetryDao here purely because it's platform specific (Room)
    // iOS and Android will pass their own DAO to TelemetryRepository in the respective modules.
    
    // So SyncEngine or TelemetryRepository will be partially provided by the platform.
}
