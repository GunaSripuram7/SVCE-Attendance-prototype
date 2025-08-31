plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)

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
//    ) // Make sure this version matches plugin version
    // or latest stable

    implementation(platform("io.github.jan-tennert.supabase:bom:3.2.2"))
    implementation("io.github.jan-tennert.supabase:postgrest-kt")
    implementation("io.github.jan-tennert.supabase:realtime-kt")

    implementation("io.github.jan-tennert.supabase:storage-kt")

    // Add the BOM once to keep all modules on 3.2.2 automatically


    implementation("io.ktor:ktor-client-android:3.2.2")

// or the latest release!
    implementation("com.onesignal:OneSignal:5.1.6")

    // Supabase dependencies


    implementation("io.github.jan-tennert.supabase:functions-kt")


    // Socket.IO for real-time coordination
    implementation("io.socket:socket.io-client:2.0.1")

    // HTTP and JSON
    implementation("com.squareup.okhttp3:okhttp:4.11.0")
    implementation("com.squareup.moshi:moshi-kotlin:1.15.0")
    implementation("com.google.code.gson:gson:2.13.1")

    // Coroutines for async operations
   // implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")


    // Add the BOM for version management


// Add the specific modules you need

     // if you need auth


// Add Ktor client for networking



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