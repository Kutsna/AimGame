pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    // Allow repos both in settings and in project build files
    // repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)  // or ALLOW_PROJECT
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "AimGame"
include(":app")
 