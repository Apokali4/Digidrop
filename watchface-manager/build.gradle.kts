plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.apokali4.watchfacemanager"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.apokali4.watchfacemanager"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }
    sourceSets.getByName("main").assets.srcDir(layout.buildDirectory.dir("generated/pipboyAssets"))
}

val bundlePipBoyApk by tasks.registering(Copy::class) {
    dependsOn(":pipboy-watchface:assembleDebug")
    from(project(":pipboy-watchface").layout.buildDirectory.file("outputs/apk/debug/pipboy-watchface-debug.apk"))
    into(layout.buildDirectory.dir("generated/pipboyAssets/watchfaces"))
    rename { "pipboy-watchface.apk" }
}

tasks.named("preBuild").configure {
    dependsOn(bundlePipBoyApk)
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation("com.flyfishxu:kadb-android:2.1.5")
    debugImplementation(libs.androidx.compose.ui.tooling)
    testImplementation(libs.junit)
}
