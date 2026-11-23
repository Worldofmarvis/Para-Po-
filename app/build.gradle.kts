import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    // AGP 9 has built-in Kotlin, so org.jetbrains.kotlin.android is not applied.
}

// ORS_API_KEY is read from local.properties (never committed to git).
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "com.parapo"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.parapo"
        minSdk = 29 // requestCellInfoUpdate needs API 29+
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"

        buildConfigField(
            "String",
            "ORS_API_KEY",
            "\"${localProps.getProperty("ORS_API_KEY", "")}\""
        )

        buildConfigField(
            "String",
            "MAPTILER_KEY",
            "\"${localProps.getProperty("MAPTILER_KEY", "")}\""
        )
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.datastore)

    implementation(libs.osmdroid)
    implementation(libs.play.services.location)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)

    testImplementation("junit:junit:4.13.2")
}
