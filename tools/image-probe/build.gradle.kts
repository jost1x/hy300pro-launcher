plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "io.jostx.hy300launcher.imageprobe"
    compileSdk = 35
    buildToolsVersion = "35.0.0"
    defaultConfig {
        applicationId = "io.jostx.hy300launcher.imageprobe"
        minSdk = 30
        targetSdk = 32
        versionCode = 1
        versionName = "1.0"
    }
    // Private device probe: match the launcher target, not distributed through Google Play.
    lint { disable += "ExpiredTargetSdkVersion" }
    sourceSets.getByName("main") { java.srcDir(rootProject.file("shared/kotlin")) }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
