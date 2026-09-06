plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.example.sinshield"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.sinshield"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
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
