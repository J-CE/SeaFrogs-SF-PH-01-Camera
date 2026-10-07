plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "de.jce.seafrogs"
    compileSdk = 35
    defaultConfig {
        applicationId = "de.jce.seafrogs"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0-hid"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
