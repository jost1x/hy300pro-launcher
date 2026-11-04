plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val launcherCertificate = providers.gradleProperty("launcherCertificateSha256")
    .orElse("551469ad2db5f49b94e0f747c3f293b1a53018239241e162f7f2e8164746df07").get()
require(launcherCertificate.matches(Regex("[0-9a-f]{64}"))) { "Invalid launcher certificate SHA-256" }
android {
    namespace = "io.jostx.hy300launcher.imagecontrol"
    compileSdk = 35
    buildToolsVersion = "35.0.0"
    defaultConfig {
        applicationId = "io.jostx.hy300launcher.imagecontrol"
        minSdk = 30
        targetSdk = 32
        versionCode = 1
        versionName = "1.0"
        buildConfigField("String", "LAUNCHER_CERTIFICATE", "\"$launcherCertificate\"")
    }
    buildFeatures { aidl = true; buildConfig = true }
    sourceSets.getByName("main") {
        aidl.srcDir(rootProject.file("shared/aidl"))
        java.srcDir(rootProject.file("shared/kotlin"))
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    // Private companion for this Android 11 firmware, not a Play Store application.
    lint { disable += "ExpiredTargetSdkVersion" }
}
