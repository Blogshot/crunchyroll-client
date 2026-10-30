plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.github.blogshot.crtv"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.github.blogshot.crtv"
        minSdk = 28
        // 35 statt 36: ab 36 kommt KEYCODE_BACK bei Predictive Back nicht mehr als Key-Event (wie beim hanime-Client).
        targetSdk = 35
        // CI-Laufnummer als versionCode: Die Update-Prüfung vergleicht damit.
        versionCode = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
        // Commit im Versionsnamen, damit sichtbar ist, welcher Stand installiert ist.
        versionName = "0.1.0-" + (System.getenv("GITHUB_SHA")?.take(7) ?: "lokal")
        // Release-Tag, aus dem die App Updates holt (main: android-latest, sonst android-dev).
        buildConfigField("String", "UPDATE_CHANNEL", "\"${System.getenv("UPDATE_CHANNEL") ?: "android-latest"}\"")
        buildConfigField("String", "UPDATE_REPO", "\"Blogshot/crunchyroll-client\"")
    }

    // Fester Debug-Schlüssel (nicht geheim): CI- und lokale Builds lassen sich übereinander installieren.
    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        debug { signingConfig = signingConfigs.getByName("debug") }
        release { isMinifyEnabled = false }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":core"))
    implementation(libs.coroutines.android)
    implementation(libs.serialization.json)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.tv.material)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.process)
    implementation(libs.navigation.compose)
    implementation(libs.datastore.preferences)

    implementation(libs.media3.exoplayer)
    implementation(libs.media3.exoplayer.dash)
    implementation(libs.media3.ui.compose)
    implementation(libs.media3.session)
    implementation(libs.coil.compose)
    implementation(libs.coil.okhttp)
}
