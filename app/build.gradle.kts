plugins {
	id("com.android.application")
	id("org.jetbrains.kotlin.android")
}

android {
	namespace = "io.jostx.hy300launcher"
	compileSdk = 35
	buildToolsVersion = "35.0.0"

	defaultConfig {
		applicationId = "io.jostx.hy300launcher"
		minSdk = 23
		targetSdk = 32
		versionCode = 1
		versionName = "1.0"
	}

	compileOptions {
		sourceCompatibility = JavaVersion.VERSION_17
		targetCompatibility = JavaVersion.VERSION_17
	}

	kotlinOptions {
		jvmTarget = "17"
	}
}
