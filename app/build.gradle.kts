plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val parallelTestInstallation = providers.gradleProperty("seafrogsParallelTest").orNull == "true"
val releaseKeystorePath = providers.environmentVariable("SEAFROGS_RELEASE_KEYSTORE").orNull
val releaseKeystorePassword =
    providers.environmentVariable("SEAFROGS_RELEASE_STORE_PASSWORD").orNull
val releaseKeyAlias = providers.environmentVariable("SEAFROGS_RELEASE_KEY_ALIAS").orNull
val releaseKeyPassword = providers.environmentVariable("SEAFROGS_RELEASE_KEY_PASSWORD").orNull

android {
    namespace = "de.jce.seafrogs"
    compileSdk = 36
    ndkVersion = "27.2.12479018"

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    defaultConfig {
        applicationId = if (parallelTestInstallation) "de.jce.seafrogs.test" else "de.jce.seafrogs"
        manifestPlaceholders["appLabel"] =
            if (parallelTestInstallation) "SeaFrogs Test" else "SeaFrogs Camera"
        minSdk = 26
        targetSdk = 35
        ndk { abiFilters += "arm64-v8a" }
        versionCode = 31
        versionName = "1.0.0-rc2"
    }

    buildFeatures { buildConfig = true }

    signingConfigs {
        providers.gradleProperty("seafrogsDebugKeystore").orNull?.let { debugKeystorePath ->
            getByName("debug").storeFile = file(debugKeystorePath)
        }
        create("release") {
            releaseKeystorePath?.let { storeFile = file(it) }
            storePassword = releaseKeystorePassword
            keyAlias = releaseKeyAlias
            keyPassword = releaseKeyPassword
        }
    }

    buildTypes {
        getByName("release") {
            signingConfig = signingConfigs.getByName("release")
            isDebuggable = false
            // We keep code and resource optimization off for this behavior-preserving release.
            isMinifyEnabled = false
            isShrinkResources = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    testOptions { unitTests.isIncludeAndroidResources = true }
}

// A release must never silently use a debug key or replace the test package with a new certificate.
val verifyReleaseSigning by
    tasks.registering {
        doLast {
            check(!parallelTestInstallation) {
                "Release uses de.jce.seafrogs; omit seafrogsParallelTest."
            }
            check(!releaseKeystorePath.isNullOrBlank() && file(releaseKeystorePath).isFile) {
                "Set SEAFROGS_RELEASE_KEYSTORE to the private release keystore."
            }
            check(
                !releaseKeystorePassword.isNullOrBlank() &&
                    !releaseKeyAlias.isNullOrBlank() &&
                    !releaseKeyPassword.isNullOrBlank()
            ) {
                "Set SEAFROGS_RELEASE_STORE_PASSWORD, SEAFROGS_RELEASE_KEY_ALIAS and SEAFROGS_RELEASE_KEY_PASSWORD."
            }
        }
    }

tasks.matching { it.name == "preReleaseBuild" }.configureEach { dependsOn(verifyReleaseSigning) }

dependencies {
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    implementation("androidx.activity:activity:1.10.1")
    implementation("androidx.camera:camera-camera2:1.6.2")
    implementation("androidx.camera:camera-lifecycle:1.6.2")
    implementation("androidx.camera:camera-view:1.6.2")
    implementation("androidx.camera:camera-video:1.6.2")
    implementation("androidx.camera:camera-extensions:1.6.2")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
}
