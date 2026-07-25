package com.segurancarural.gpstracker.data.network

import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest

object SharedConfig {
    var SUPABASE_URL: String = ""
    var SUPABASE_KEY: String = ""
    var DEVICE_API_SECRET: String = ""
    var BACKEND_BASE_URL: String = ""
    var IS_DEBUG: Boolean = false
}

object SupabaseClient {
    private var extraConfig: (io.github.jan.supabase.SupabaseClientBuilder.() -> Unit)? = null

    fun init(config: io.github.jan.supabase.SupabaseClientBuilder.() -> Unit) {
        extraConfig = config
    }

    val client by lazy {
        createSupabaseClient(
            supabaseUrl = SharedConfig.SUPABASE_URL,
            supabaseKey = SharedConfig.SUPABASE_KEY
        ) {
            install(Auth)
            install(Postgrest)
            extraConfig?.invoke(this)
        }
    }
}
