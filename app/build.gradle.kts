plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.docscriptai.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.vosk"   // Keep original for APK compatibility
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86_64", "x86")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        compilerOptions {
            jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
        }
    }
    buildFeatures {
        viewBinding = true
    }
    packaging {
        jniLibs {
            pickFirsts += setOf("**/libc++_shared.so")
        }
    }
}

dependencies {
    // ── Project modules (ALDL layers) ────────────────────────────────────────
    implementation(project(":core"))
    implementation(project(":domain"))
    implementation(project(":data:audio"))
    implementation(project(":data:llm"))

    // ── AndroidX / UI ────────────────────────────────────────────────────────
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.cardview)
    implementation(libs.coordinatorlayout)
    implementation(libs.recyclerview)
    implementation(libs.activity)

    // ── Kotlin Coroutines ────────────────────────────────────────────────────
    implementation(libs.kotlinx.coroutines.android)

    // ── Testing ──────────────────────────────────────────────────────────────
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
