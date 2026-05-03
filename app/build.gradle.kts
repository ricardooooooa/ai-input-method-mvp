import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val localProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        localPropertiesFile.inputStream().use { load(it) }
    }
}

if (localProperties.containsKey("OPENROUTER_API_KEY")) {
    project.extensions.extraProperties["OPENROUTER_API_KEY"] =
        localProperties.getProperty("OPENROUTER_API_KEY")
}

android {
    namespace = "com.example.aiime"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.aiime"
        minSdk = 23
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        buildConfigField(
            "String",
            "OPENROUTER_API_KEY",
            "\"${project.findProperty("OPENROUTER_API_KEY") ?: ""}\""
        )
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}
