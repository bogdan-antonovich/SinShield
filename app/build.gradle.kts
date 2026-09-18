plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val releaseSigningEnvironment = mapOf(
    "storeFile" to providers.environmentVariable("ANDROID_KEYSTORE_PATH"),
    "storePassword" to providers.environmentVariable("ANDROID_KEYSTORE_PASSWORD"),
    "keyAlias" to providers.environmentVariable("ANDROID_KEY_ALIAS"),
    "keyPassword" to providers.environmentVariable("ANDROID_KEY_PASSWORD"),
)
val configuredReleaseSigningValues = releaseSigningEnvironment.filterValues { it.isPresent }

check(configuredReleaseSigningValues.isEmpty() || configuredReleaseSigningValues.size == releaseSigningEnvironment.size) {
    "Release signing is only partially configured. Set all ANDROID_KEYSTORE_* variables or none of them."
}

android {
    namespace = "app.sinshield"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "app.sinshield"
        minSdk = 26
        targetSdk = 37
        versionCode = providers.gradleProperty("releaseVersionCode").orNull?.toInt() ?: 1
        versionName = providers.gradleProperty("releaseVersionName").orNull ?: "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (configuredReleaseSigningValues.isNotEmpty()) {
            create("release") {
                storeFile = file(releaseSigningEnvironment.getValue("storeFile").get())
                storePassword = releaseSigningEnvironment.getValue("storePassword").get()
                keyAlias = releaseSigningEnvironment.getValue("keyAlias").get()
                keyPassword = releaseSigningEnvironment.getValue("keyPassword").get()
            }
        }
    }

    buildTypes {
        create("prodTest") {
            initWith(getByName("release"))
            // Production-like runtime behavior with a local-only certificate. This variant is for
            // measuring startup, process death, and Accessibility recovery without a debugger or a
            // Play Console upload; it must never be published.
            isDebuggable = false
            signingConfig = signingConfigs.getByName("debug")
            versionNameSuffix = "-prodtest"
            matchingFallbacks += "release"
            optimization {
                enable = true
            }
        }
        release {
            if (configuredReleaseSigningValues.isNotEmpty()) {
                signingConfig = signingConfigs.getByName("release")
            }
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        buildConfig = true
        compose = true
    }
    androidResources {
        noCompress += listOf("tflite", "onnx")
    }
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
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // TensorFlow Lite, via Google's LiteRT artifacts (the renamed successor).
    // The legacy org.tensorflow:tensorflow-lite* artifacts fail AGP's unique-namespace
    // manifest-merger check: their impl and -api splits both declare the same namespace.
    // litert/litert-api use distinct namespaces; the support lib is reimplemented inline
    // in NsfwClassifier (litert-support still shares a namespace with its -api split).
    implementation("com.google.ai.edge.litert:litert:1.4.2")
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.29.0")
    implementation("org.opencv:opencv:5.0.0.1")
}
