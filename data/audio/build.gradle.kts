plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.docscriptai.data.audio"
    compileSdk = 34

    defaultConfig {
        minSdk = 26
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
}

dependencies {
    implementation(project(":core"))
    implementation(project(":domain"))

    // Vosk Speech Recognition (must use @aar for JNI-based artifacts)
    implementation("net.java.dev.jna:jna:5.12.1@aar")
    implementation("com.alphacephei:vosk-android:0.3.32@aar")

    // Kotlin Coroutines
    implementation(libs.kotlinx.coroutines.android)
}
