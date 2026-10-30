val coreOnly = providers.gradleProperty("coreOnly").isPresent

pluginManagement {
    repositories {
        if (!providers.gradleProperty("coreOnly").isPresent) google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        if (!coreOnly) google()
        mavenCentral()
    }
}

rootProject.name = "crunchyroll-client"
if (coreOnly) rootProject.buildFileName = "core-only.gradle.kts"

include(":core")
// -PcoreOnly baut nur das reine Kotlin-Modul (ohne Android SDK), z. B. für schnelle Tests.
if (!coreOnly) {
    include(":app")
}
