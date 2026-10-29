// Root-Build für `-PcoreOnly`: ohne Android-Gradle-Plugin, also ohne Google-Maven und Android SDK.
plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}
