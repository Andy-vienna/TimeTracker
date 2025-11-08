// In settings.gradle.kts

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        // Diese URL ist für Compose-Abhängigkeiten wichtig. Lassen wir sie drin.
        maven("https://maven.jetbrains.com/public")
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven("https://maven.jetbrains.com/public")
    }
}

rootProject.name = "TimeTracker"
include(":app")

 