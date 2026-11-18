plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
}

android {
    namespace = "app.cragpack"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        // ponytail: placeholder id until the name is secured (D13). It can't change after the first Play upload.
        applicationId = "app.cragpack"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "0.1.0"
    }
}

dependencies {
    implementation(projects.shared)
    implementation(libs.androidx.activity.compose)
}
