plugins {
    id("com.android.application") version "8.2.0"
    id("org.jetbrains.kotlin.android") version "1.9.20"
    id("org.jetbrains.kotlin.plugin.compose") version "1.9.20"
    id("com.google.devtools.ksp") version "1.9.20-1.0.14-beta01"
    id("com.google.dagger.hilt.android") version "2.48"
}

android {
    compileSdk = 36
    
    defaultConfig {
        minSdk = 23
        targetSdk = 36
    }
    
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
