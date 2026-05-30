plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.openengage.tracker"
    compileSdk = 35

    defaultConfig {
        minSdk = 24
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlinOptions {
        jvmTarget = "21"
    }
    
    buildFeatures {
        compose = true
    }
    
    composeOptions {
        // Kotlin 2.1.0 uses compiler-embedded Compose Compiler, so we don't need a composeCompilerVersion definition.
    }
}

dependencies {
    implementation(project(":openengage-core"))
    
    // Core Android components
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    // Jetpack Compose components
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.navigation.compose)
    
    // Compile-only Firebase SDK interface
    compileOnly(platform(libs.firebase.bom))
    compileOnly(libs.firebase.analytics.ktx)
}

apply(from = "../gradle/publish.gradle")
