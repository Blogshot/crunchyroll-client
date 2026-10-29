// Android-Plugin wird nur im :app-Modul geladen, damit `-PcoreOnly` ohne Android SDK und Google-Maven baut.
plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}
