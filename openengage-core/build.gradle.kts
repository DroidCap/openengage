plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.openengage.core"
    compileSdk = 35

    defaultConfig {
        minSdk = 24
        
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlinOptions {
        jvmTarget = "21"
    }
}

dependencies {
    // Compile-only to prevent forcing dependency versions on the host application
    compileOnly(platform(libs.firebase.bom))
    compileOnly(libs.firebase.analytics.ktx)
    
    // Core requirements
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.core.ktx)

    // Testing
    testImplementation(libs.junit)
}

apply(from = "../gradle/publish.gradle")
