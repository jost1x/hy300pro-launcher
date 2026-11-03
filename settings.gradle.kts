pluginManagement {
	repositories {
		google()
		mavenCentral()
		gradlePluginPortal()
	}
}

dependencyResolutionManagement {
	repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
	repositories {
		google()
		mavenCentral()
	}
}

rootProject.name = "Hy300Launcher"
include(":app", ":image-control")

// Isolated firmware permission probe; excluded from normal launcher builds.
if (providers.gradleProperty("imageProbe").orNull == "true") {
    include(":imageProbe")
    project(":imageProbe").projectDir = file("tools/image-probe")
}
