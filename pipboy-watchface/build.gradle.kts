plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.apokali4.pipboy"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.apokali4.pipboy"
        minSdk = 33
        targetSdk = 35
        versionCode = 3
        versionName = "1.2.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
