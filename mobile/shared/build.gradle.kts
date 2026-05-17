/**
 * Shared KMP module — business logic, Room DB, and SyncEngine.
 * Targets: Android (production), iOS (future — stub actuals only).
 */
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.androidx.room)
}

kotlin {
    androidTarget {
        compilations.all {
            compileTaskProvider.configure {
                compilerOptions {
                    jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
                }
            }
        }
    }

    // iOS targets — placeholder for future port (Phase 3)
    iosX64()
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.ktor.client.logging)

            // Room runtime (KMP-compatible)
            implementation(libs.androidx.room.runtime)
            implementation(libs.androidx.room.ktx)
        }

        androidMain.dependencies {
            implementation(libs.kotlinx.coroutines.android)
            implementation(libs.ktor.client.android)
        }

        // iOS stubs — no real dependencies yet (Phase 3)
        iosMain.dependencies {}
    }
}

android {
    namespace = "com.seguranca.rural.shared"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

// Room schema export directory
room {
    schemaDirectory("$projectDir/schemas")
}

// KSP — Room annotation processor must run for each target
dependencies {
    add("kspAndroid", libs.androidx.room.compiler)
}
