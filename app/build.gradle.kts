plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    kotlin("plugin.serialization") version "1.9.0" // or your Kotlin version
}

android {
    namespace = "com.svce.attendance"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.svce.attendance"
        minSdk = 21
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {
    // OneSignal for push notifications


    implementation("com.onesignal:OneSignal:5.1.6")

    // Supabase dependencies
    implementation("io.github.jan-tennert.supabase:postgrest-kt:2.4.1")
    implementation("io.github.jan-tennert.supabase:realtime-kt:2.4.1")
    implementation("io.ktor:ktor-client-android:2.3.4")
    implementation("io.ktor:ktor-client-core:2.3.4")

    // Socket.IO for real-time coordination
    implementation("io.socket:socket.io-client:2.0.1")

    // HTTP and JSON
    implementation("com.squareup.okhttp3:okhttp:4.11.0")
    implementation("com.squareup.moshi:moshi-kotlin:1.15.0")
    implementation("com.google.code.gson:gson:2.13.1")

    // Coroutines for async operations
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")


    // Add the BOM for version management
    implementation(platform("io.github.jan-tennert.supabase:bom:3.0.1"))

// Add the specific modules you need
    implementation("io.github.jan-tennert.supabase:postgrest-kt")
    implementation("io.github.jan-tennert.supabase:auth-kt") // if you need auth
    implementation("io.github.jan-tennert.supabase:storage-kt") // if you need storage

// Add Ktor client for networking
    implementation("io.ktor:ktor-client-android:3.0.0")


    // Standard Android dependencies
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    // CSV reading/writing library
    implementation("com.opencsv:opencsv:5.5.2")
}