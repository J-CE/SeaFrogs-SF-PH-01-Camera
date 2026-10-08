plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "de.jce.seafrogs"
    compileSdk = 36
    ndkVersion = "27.2.12479018"
    externalNativeBuild { cmake { path = file("src/main/cpp/CMakeLists.txt"); version = "3.22.1" } }
    defaultConfig {
        val parallelTest = providers.gradleProperty("seafrogsParallelTest").orNull == "true"
        applicationId = if (parallelTest) "de.jce.seafrogs.test" else "de.jce.seafrogs"
        manifestPlaceholders["appLabel"] = if (parallelTest) "SeaFrogs Test" else "SeaFrogs Camera"
        minSdk = 26
        targetSdk = 35
        ndk { abiFilters += "arm64-v8a" }
        versionCode = 29
        versionName = "0.8.10-hid-transition"
    }
    providers.gradleProperty("seafrogsDebugKeystore").orNull?.let { keyPath ->
        signingConfigs.getByName("debug").storeFile = file(keyPath)
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    testOptions { unitTests.isIncludeAndroidResources = true }
}
dependencies {
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    implementation("androidx.camera:camera-extensions:1.6.2")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
    implementation("androidx.activity:activity:1.10.1")
    implementation("androidx.camera:camera-camera2:1.6.2")
    implementation("androidx.camera:camera-lifecycle:1.6.2")
    implementation("androidx.camera:camera-view:1.6.2")
    implementation("androidx.camera:camera-video:1.6.2")
}

