import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.google.services)
}

android {
    namespace = "com.segurancarural.gpstracker"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.segurancarural.gpstracker"
        minSdk = 26           // Android 8.0 — minimum for reliable ForegroundService + FusedLocation
        targetSdk = 36

        val version = project.findProperty("versionName")?.toString() ?: "0.3.0"
        versionName = version
        versionCode = (project.findProperty("versionCode") as String?)?.toIntOrNull() ?: run {
            try {
                val parts = version.split(".")
                if (parts.size >= 3) {
                    val major = parts[0].toIntOrNull() ?: 0
                    val minor = parts[1].toIntOrNull() ?: 0
                    val patch = parts[2].split("-")[0].toIntOrNull() ?: 0
                    major * 10000 + minor * 100 + patch
                } else {
                    1
                }
            } catch (e: Exception) {
                1
            }
        }

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        val localProps = Properties()
        val localPropsFile = rootProject.file("local.properties")
        if (localPropsFile.exists()) {
            localProps.load(localPropsFile.inputStream())
        }

        val deviceSecret = localProps.getProperty("device.api.secret") 
            ?: project.findProperty("device.api.secret")?.toString() 
            ?: "change-me-in-local-properties"
        buildConfigField("String", "DEVICE_API_SECRET", "\"$deviceSecret\"")
    }

    flavorDimensions.add("environment")

    productFlavors {
        create("dev") {
            dimension = "environment"
            applicationIdSuffix = ".dev"
            resValue("string", "app_name", "SegurancaRuralDEV")

            val localProps = Properties()
            val localPropsFile = rootProject.file("local.properties")
            if (localPropsFile.exists()) {
                localProps.load(localPropsFile.inputStream())
            }
            val backendUrl = localProps.getProperty("backend.base.url.dev") 
                ?: localProps.getProperty("backend.base.url")
                ?: "http://10.0.2.2:3000"
            buildConfigField("String", "BACKEND_BASE_URL", "\"$backendUrl\"")
        }
        create("pre") {
            dimension = "environment"
            applicationIdSuffix = ".pre"
            resValue("string", "app_name", "SegurancaRuralPRE")

            val localProps = Properties()
            val localPropsFile = rootProject.file("local.properties")
            if (localPropsFile.exists()) {
                localProps.load(localPropsFile.inputStream())
            }
            val backendUrl = localProps.getProperty("backend.base.url.pre") 
                ?: localProps.getProperty("backend.base.url")
                ?: "https://segurancarural-gpstracker-pre.vercel.app"
            buildConfigField("String", "BACKEND_BASE_URL", "\"$backendUrl\"")
        }
        create("prod") {
            dimension = "environment"
            resValue("string", "app_name", "SegurancaRural")

            val localProps = Properties()
            val localPropsFile = rootProject.file("local.properties")
            if (localPropsFile.exists()) {
                localProps.load(localPropsFile.inputStream())
            }
            val backendUrl = localProps.getProperty("backend.base.url.prod") 
                ?: localProps.getProperty("backend.base.url")
                ?: "https://gps-tracker-nine-omega.vercel.app"
            buildConfigField("String", "BACKEND_BASE_URL", "\"$backendUrl\"")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isDebuggable = true
        }

    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    // Shared KMP module
    implementation(project(":shared"))

    // AndroidX Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    // Compose BOM + UI
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material3.adaptive.navigation.suite)
    implementation(libs.androidx.compose.material.icons.extended)

    // Room (used via :shared but referenced for annotation processing)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // WorkManager
    implementation(libs.androidx.work.runtime.ktx)

    // Fused Location (GPS)
    implementation(libs.play.services.location)

    // Ktor (HTTP client — Android engine)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.android)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.client.logging)
    // SLF4J no-op binding — silences "No SLF4J providers found" warnings from Ktor's logging
    implementation("org.slf4j:slf4j-simple:2.0.13")

    // Coroutines
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    // Provides Task.await() for Firebase / Play Services
    implementation(libs.kotlinx.coroutines.play.services)


    // EncryptedSharedPreferences
    implementation(libs.androidx.security.crypto)

    // MapLibre Android SDK
    implementation(libs.maplibre.android)

    // Firebase Cloud Messaging (push notifications)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging.ktx)

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}