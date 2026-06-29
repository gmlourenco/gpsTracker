package com.segurancarural.gpstracker.di

import com.segurancarural.gpstracker.GpsTrackerApplication
import com.segurancarural.gpstracker.data.db.AppDatabase
import com.segurancarural.gpstracker.data.network.ApiClient
import com.segurancarural.gpstracker.data.network.ApiRoutes
import com.segurancarural.gpstracker.data.repository.FarmRepository
import com.segurancarural.gpstracker.data.repository.TelemetryRepository
import com.segurancarural.gpstracker.domain.usecase.SubmitLocationUseCase
import com.segurancarural.gpstracker.sync.SyncEngine
import com.segurancarural.gpstracker.ui.viewmodel.FamilyGroupsViewModel
import com.segurancarural.gpstracker.ui.viewmodel.MapViewModel
import io.ktor.client.HttpClient
import org.koin.android.ext.koin.androidApplication
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    // Database and DAO
    single<AppDatabase> { (androidApplication() as GpsTrackerApplication).database }
    single { get<AppDatabase>().telemetryDao() }
    
    // HTTP Client
    single<HttpClient> { ApiClient.httpClient }
    
    // Repositories & Use Cases
    single { FarmRepository() }
    single { TelemetryRepository(get()) }
    single { SubmitLocationUseCase(get()) }
    
    // SyncEngine (from shared KMP)
    factory {
        SyncEngine(
            dao = get(),
            httpClient = get(),
            locationUrl = ApiRoutes.LOCATION_V2,
            emergencyUrl = ApiRoutes.EMERGENCY,
            farmIdProvider = { get<FarmRepository>().currentFarmId }
        )
    }
    
    // ViewModels
    viewModel { FamilyGroupsViewModel(get()) }
    viewModel { MapViewModel(get()) }
}
