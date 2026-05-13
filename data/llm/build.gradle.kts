plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.docscriptai.data.llm"
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

    // LiteRT-LM for on-device LLM inference
    implementation(libs.litertlm.android)

    // Kotlin Coroutines
    implementation(libs.kotlinx.coroutines.android)
}
