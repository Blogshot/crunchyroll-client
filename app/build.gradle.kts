import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Client-Kennung für die Crunchyroll-API: aus local.properties oder Umgebungsvariablen (CI-Secrets),
// niemals im Repo. Ohne Werte baut die App trotzdem, der Login schlägt dann fehl.
val localProps = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
fun secret(name: String): String = (localProps.getProperty(name) ?: System.getenv(name) ?: "")
    .replace("\\", "\\\\").replace("\"", "\\\"")

android {
    namespace = "com.github.blogshot.crtv"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.github.blogshot.crtv"
        minSdk = 28
        // 35 statt 36: ab 36 kommt KEYCODE_BACK bei Predictive Back nicht mehr als Key-Event (wie beim hanime-Client).
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        buildConfigField("String", "CR_CLIENT_ID", "\"${secret("CR_CLIENT_ID")}\"")
        buildConfigField("String", "CR_CLIENT_SECRET", "\"${secret("CR_CLIENT_SECRET")}\"")
        buildConfigField("String", "CR_USER_AGENT", "\"${secret("CR_USER_AGENT")}\"")
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
