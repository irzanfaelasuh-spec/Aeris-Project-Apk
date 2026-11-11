plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "dev.aeris.island"
    compileSdk = 36
    defaultConfig {
        applicationId = "dev.aeris.island"
        minSdk = 26
        targetSdk = 35
        versionCode = 3
        versionName = "3.0.0"
    }
    buildTypes { release { isMinifyEnabled = false } }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("androidx.core:core-ktx:1.16.0")
}
