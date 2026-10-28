plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "tv.castly.tv"
    compileSdk = 34
    defaultConfig {
        applicationId = "tv.castly.tv"
        minSdk = 29
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    packaging { resources { excludes += listOf("META-INF/INDEX.LIST", "META-INF/DEPENDENCIES", "META-INF/{AL2.0,LGPL2.1}") } }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("org.nanohttpd:nanohttpd:2.3.1")
    implementation("org.java-websocket:Java-WebSocket:1.5.6")
    implementation("com.google.zxing:core:3.5.3")
}
