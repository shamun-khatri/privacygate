plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.privacygate.app"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.privacygate.app"
        minSdk = 30
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0-proof"
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildTypes {
        release { isMinifyEnabled = false }
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.activity.compose)
    implementation(libs.coroutines.android)
    implementation(libs.coroutines.play.services)
    implementation(libs.mlkit.text)
    implementation(libs.mlkit.labeling)
    implementation(libs.mlkit.face)
    implementation(libs.mlkit.segmentation)
    implementation(libs.coil.compose)
    implementation(libs.gson)
    testImplementation(libs.junit)
}
