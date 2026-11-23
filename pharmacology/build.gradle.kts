plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.apokali4.pharmacology"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.apokali4.pharmacology"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    // Android 11+ rejects v1-only APKs when targetSdk >= 30.
    // Keep v1 for older Android versions and explicitly enable v2/v3.
    signingConfigs {
        getByName("debug") {
            enableV1Signing = true
            enableV2Signing = true
            enableV3Signing = true
        }
    }

    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
}
