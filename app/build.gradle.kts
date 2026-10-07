plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "de.jce.seafrogs"
    compileSdk = 35
    defaultConfig {
        applicationId = "de.jce.seafrogs"
        minSdk = 26
        targetSdk = 35
        versionCode = 10
        versionName = "0.6.3-raw-jpeg"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    implementation("androidx.camera:camera-extensions:1.4.2")
    testImplementation("junit:junit:4.13.2")
    implementation("androidx.activity:activity:1.10.1")
    implementation("androidx.camera:camera-camera2:1.4.2")
    implementation("androidx.camera:camera-lifecycle:1.4.2")
    implementation("androidx.camera:camera-view:1.4.2")
}

