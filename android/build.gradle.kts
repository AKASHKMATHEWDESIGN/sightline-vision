// Top-level build file. AGP 9 ships built-in Kotlin support, so only the
// Compose compiler plugin needs to be declared alongside the Android plugin.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
