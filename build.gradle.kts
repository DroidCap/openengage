// Top-level build file for OpenEngage
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.google.services) apply false
    id("com.android.library") version "8.7.3" apply false
    kotlin("plugin.serialization") version "2.1.0" apply false
    alias(libs.plugins.kotlin.compose) apply false
}
