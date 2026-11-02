plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.monip"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.monip"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildFeatures {
        dataBinding = true
        viewBinding = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core)
    implementation(libs.androidx.annotation)
    implementation(libs.androidx.lifecycle.runtime)
}
