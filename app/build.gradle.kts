plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "com.claudeswitcher.app"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.claudeswitcher.app"
        minSdk = 28
        targetSdk = 34
        versionCode = 2
        versionName = "0.1.1"
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64") }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    val signingPath = System.getenv("RELEASE_KEYSTORE")
    if (signingPath != null) {
        signingConfigs { create("production") {
            storeFile = file(signingPath)
            storePassword = System.getenv("RELEASE_STORE_PASSWORD")
            keyAlias = System.getenv("RELEASE_KEY_ALIAS")
            keyPassword = System.getenv("RELEASE_KEY_PASSWORD")
        } }
        buildTypes { getByName("release") { signingConfig = signingConfigs.getByName("production") } }
    }
}
dependencies { implementation("androidx.webkit:webkit:1.11.0") }
