plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.softwinner.tvvolume"
    compileSdk = 35
    buildToolsVersion = "35.0.0"

    defaultConfig {
        applicationId = "com.softwinner.tvvolume"
        minSdk = 30
        targetSdk = 30
        versionCode = 31
        versionName = "1.0.0-hy300"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions { jvmTarget = "17" }

    // Private system-app replacement for the verified Android 11 firmware.
    lint { disable += "ExpiredTargetSdkVersion" }
}
