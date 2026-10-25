plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "pt.grok.sway"
    compileSdk = 36

    defaultConfig {
        applicationId = "pt.grok.android.sway"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
        // Bump versionCode for every APK published to frontend/public/downloads, and update
        // sway.json there to match: installed apps compare against it on launch.
        buildConfigField("String", "UPDATE_MANIFEST_URL", "\"https://app.gowithsway.com/downloads/sway.json\"")
    }

    buildTypes {
        debug {
            // 10.0.2.2 is the host machine as seen from the Android emulator.
            buildConfigField("String", "API_BASE_URL", "\"http://10.0.2.2:3100\"")
        }
        release {
            buildConfigField("String", "API_BASE_URL", "\"https://api.gowithsway.com\"")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Signed with the debug key until a release keystore exists, so the APK installs as-is.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.browser)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.kotlinx.serialization.json)
    debugImplementation(libs.compose.ui.tooling)
}
