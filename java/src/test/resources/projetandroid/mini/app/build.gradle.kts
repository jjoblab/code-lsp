plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.exemple.mini"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.exemple.mini"
        minSdk = 21
        targetSdk = 34
        versionCode = 7
        versionName = "2.1"
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
