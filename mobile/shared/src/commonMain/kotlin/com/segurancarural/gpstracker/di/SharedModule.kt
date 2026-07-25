package com.segurancarural.gpstracker.di

import com.segurancarural.gpstracker.data.network.ApiClient
import com.segurancarural.gpstracker.data.repository.FamilyPositionsRepository
import com.segurancarural.gpstracker.data.repository.FarmRepository
import com.segurancarural.gpstracker.domain.usecase.SubmitLocationUseCase
import io.ktor.client.HttpClient
import org.koin.dsl.module

val sharedModule = module {
    single<HttpClient> { ApiClient.httpClient }
    single { FarmRepository() }
    single { FamilyPositionsRepository() }
    single { com.segurancarural.gpstracker.data.repository.DeviceConfigRepository() }
    single { com.segurancarural.gpstracker.data.repository.PushTokenRepository() }
    single { SubmitLocationUseCase(get()) }
}
