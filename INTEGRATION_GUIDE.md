# Integration Guide

Step-by-step instructions for integrating DocScriptAI's modular packages into a new Android application.

---

## Table of Contents

- [Overview](#overview)
- [Prerequisites](#prerequisites)
- [Step 1 — Add Modules to Your Project](#step-1--add-modules-to-your-project)
- [Step 2 — Configure Gradle Dependencies](#step-2--configure-gradle-dependencies)
- [Step 3 — Add Version Catalog Entries](#step-3--add-version-catalog-entries)
- [Step 4 — Configure NDK and Packaging](#step-4--configure-ndk-and-packaging)
- [Step 5 — Bundle the Vosk Model](#step-5--bundle-the-vosk-model)
- [Step 6 — Add Permissions](#step-6--add-permissions)
- [Step 7 — Initialize Services](#step-7--initialize-services)
- [Step 8 — Implement Transcription](#step-8--implement-transcription)
- [Step 9 — Implement LLM Processing](#step-9--implement-llm-processing)
- [Step 10 — Wire Up the UI](#step-10--wire-up-the-ui)
- [Integration Scenarios](#integration-scenarios)
  - [Scenario A — Full Stack (Audio + Transcription + LLM)](#scenario-a--full-stack)
  - [Scenario B — Transcription Only (No LLM)](#scenario-b--transcription-only)
  - [Scenario C — LLM Only (Bring Your Own Text)](#scenario-c--llm-only)
  - [Scenario D — Audio Utilities Only](#scenario-d--audio-utilities-only)
- [Minimal Working Example](#minimal-working-example)
- [ProGuard / R8 Rules](#proguard--r8-rules)
- [Common Integration Errors](#common-integration-errors)
- [Version Compatibility Matrix](#version-compatibility-matrix)

---

## Overview

DocScriptAI is split into **four independent library modules** that can be integrated individually or together into any Android application:

| Module | Package | What It Provides |
|---|---|---|
| `:core` | `com.docscriptai.core` | Shared constants (`AudioConfig`) and utility types (`ResultState`) |
| `:domain` | `com.docscriptai.domain` | Data models, repository interfaces, and use-case classes |
| `:data:audio` | `com.docscriptai.data.audio` | Vosk speech-to-text, WAV recording, audio format conversion |
| `:data:llm` | `com.docscriptai.data.llm` | LiteRT-LM on-device medical report extraction |

You only need to include the modules relevant to your use-case. The dependency rules are:

```
:data:audio  →  :domain  →  :core
:data:llm    →  :domain  →  :core
```

> **Note:** `:data:audio` and `:data:llm` are fully independent of each other. You can use one without the other.

---

## Prerequisites

| Requirement | Minimum |
|---|---|
| Android Studio | Iguana (2023.2.1) or later |
| Gradle | 8.4+ |
| AGP | 8.2+ |
| Kotlin | 1.9+ |
| `minSdk` | 26 (Android 8.0 Oreo) |
| `compileSdk` | 34 |
| JDK | 17 |

---

## Step 1 — Add Modules to Your Project

### Option A: Copy Modules Directly

Clone or download the DocScriptAI repo, then copy the module directories into your project root:

```bash
# From the DocScriptAI repo
cp -r core/       /path/to/your-app/core/
cp -r domain/     /path/to/your-app/domain/
cp -r data/audio/ /path/to/your-app/data/audio/
cp -r data/llm/   /path/to/your-app/data/llm/
```

Then register them in your `settings.gradle.kts`:

```kotlin
// settings.gradle.kts
include(":app")
include(":core")
include(":domain")
include(":data:audio")
include(":data:llm")
```

### Option B: Git Submodule

```bash
cd /path/to/your-app
git submodule add https://github.com/DebasishTripathy13/DocScriptAI.git libs/docscriptai
```

Then reference the modules via path in `settings.gradle.kts`:

```kotlin
include(":core")
project(":core").projectDir = file("libs/docscriptai/core")

include(":domain")
project(":domain").projectDir = file("libs/docscriptai/domain")

include(":data:audio")
project(":data:audio").projectDir = file("libs/docscriptai/data/audio")

include(":data:llm")
project(":data:llm").projectDir = file("libs/docscriptai/data/llm")
```

---

## Step 2 — Configure Gradle Dependencies

In your app module's `build.gradle.kts`, add only the modules you need:

```kotlin
// your-app/app/build.gradle.kts
dependencies {
    // Always required
    implementation(project(":core"))
    implementation(project(":domain"))

    // Add if you need speech-to-text + audio recording
    implementation(project(":data:audio"))

    // Add if you need LLM medical report extraction
    implementation(project(":data:llm"))

    // Required for coroutines (used by all modules)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
}
```

---

## Step 3 — Add Version Catalog Entries

If you use a Gradle version catalog (`libs.versions.toml`), add these entries:

```toml
[versions]
vosk = "0.3.32"
jna = "5.12.1"
litertlm = "0.9.0"
coroutines = "1.10.2"

[libraries]
vosk-android = { group = "com.alphacephei", name = "vosk-android", version.ref = "vosk" }
jna = { group = "net.java.dev.jna", name = "jna", version.ref = "jna" }
litertlm-android = { group = "com.google.ai.edge.litertlm", name = "litertlm-android", version.ref = "litertlm" }
kotlinx-coroutines-android = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-android", version.ref = "coroutines" }
```

> **Important:** Vosk and JNA must be declared with `@aar` in the data:audio module's `build.gradle.kts`. This is already configured in the module file — no action needed if you copied the module as-is.

---

## Step 4 — Configure NDK and Packaging

If using `:data:audio` (Vosk), add these to your **app module's** `build.gradle.kts`:

```kotlin
android {
    defaultConfig {
        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86_64", "x86")
        }
    }

    packaging {
        jniLibs {
            pickFirsts += setOf("**/libc++_shared.so")
        }
    }
}
```

**Why?**
- `abiFilters`: Vosk includes native libraries for multiple architectures. Specifying filters controls APK size.
- `pickFirsts`: Both Vosk and JNA bundle `libc++_shared.so`. Without this rule, Gradle throws a duplicate file error.

---

## Step 5 — Bundle the Vosk Model

The Vosk speech model must be included in your app's assets:

```bash
# Copy the Hindi model from DocScriptAI
cp -r /path/to/DocScriptAI/app/src/main/assets/model-hi \
      /path/to/your-app/app/src/main/assets/model-hi
```

**Model directory structure:**
```
assets/
└── model-hi/
    ├── am/final.mdl
    ├── conf/mfcc.conf
    ├── conf/model.conf
    ├── graph/Gr.fst
    ├── graph/HCLr.fst
    ├── graph/disambig_tid.int
    ├── graph/phones/word_boundary.int
    ├── ivector/final.dubm
    ├── ivector/final.ie
    ├── ivector/final.mat
    ├── ivector/global_cmvn.stats
    ├── ivector/online_cmvn.conf
    ├── ivector/splice.conf
    └── uuid
```

> **Other languages:** Download models from [alphacephei.com/vosk/models](https://alphacephei.com/vosk/models) and place them under `assets/model-<lang>/`. Update the `MODEL_NAME` constant in `VoskTranscriptionService.kt`.

---

## Step 6 — Add Permissions

Add to your app's `AndroidManifest.xml`:

```xml
<!-- Required for audio recording -->
<uses-permission android:name="android.permission.RECORD_AUDIO" />

<!-- Required only if downloading models from the internet -->
<uses-permission android:name="android.permission.INTERNET" />

<!-- Required only for scanning Downloads folder for models (API ≤ 32) -->
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE"
    android:maxSdkVersion="32" />
```

Request `RECORD_AUDIO` at runtime before calling any recording or live recognition functions:

```kotlin
// Example using Activity Result API
val permissionLauncher = registerForActivityResult(
    ActivityResultContracts.RequestPermission()
) { granted ->
    if (granted) {
        // Safe to use microphone
    }
}
permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
```

---

## Step 7 — Initialize Services

Create the service instances in your `Activity.onCreate()` or your DI framework:

```kotlin
import com.docscriptai.data.audio.VoskTranscriptionService
import com.docscriptai.data.audio.WavRecorder
import com.docscriptai.data.audio.AudioConverter
import com.docscriptai.data.llm.LlmProcessor
import com.docscriptai.domain.usecase.TranscribeAudioUseCase
import com.docscriptai.domain.usecase.ExtractReportUseCase

class MyActivity : AppCompatActivity() {

    // Data layer (concrete implementations)
    private val transcriptionService = VoskTranscriptionService()
    private val llmProcessor = LlmProcessor()
    private lateinit var wavRecorder: WavRecorder
    private lateinit var audioConverter: AudioConverter

    // Domain layer (use-cases)
    private val transcribeUseCase = TranscribeAudioUseCase(transcriptionService)
    private val extractReportUseCase = ExtractReportUseCase(llmProcessor)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        wavRecorder = WavRecorder(cacheDir)
        audioConverter = AudioConverter(this)

        // Initialize Vosk speech model (async — takes 10-30s on first run)
        initVoskModel()
    }

    private fun initVoskModel() {
        transcriptionService.initModel(this, object : TranscriptionListener {
            override fun onModelReady() {
                runOnUiThread { /* Enable recording UI */ }
            }
            override fun onModelError(error: String) {
                runOnUiThread { /* Show error */ }
            }
            override fun onPartialResult(text: String) {}
            override fun onFinalResult(text: String) {}
            override fun onError(error: String) {}
        })
    }

    override fun onDestroy() {
        super.onDestroy()
        transcriptionService.destroy()
        llmProcessor.destroy()
    }
}
```

---

## Step 8 — Implement Transcription

### Live Microphone Transcription

```kotlin
val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
val results = StringBuilder()

// Start recording + transcription simultaneously
fun startListening() {
    transcriptionService.startLiveRecognition(object : TranscriptionListener {
        override fun onModelReady() {}
        override fun onModelError(error: String) {}

        override fun onPartialResult(text: String) {
            // Update UI with interim text (changes rapidly)
            textView.text = results.toString() + text
        }

        override fun onFinalResult(text: String) {
            // Commit finalized sentence
            results.append(text).append(" ")
            textView.text = results.toString()
        }

        override fun onError(error: String) {
            Log.e("MyApp", "Recognition error: $error")
        }
    })
}

fun stopListening() {
    transcriptionService.stopLiveRecognition()
}
```

### File-Based Transcription

```kotlin
// Record first, then transcribe
fun recordAndTranscribe() {
    scope.launch {
        // 1. Record audio
        val wavFile = wavRecorder.startRecording()  // suspends until stopRecording()

        // 2. Transcribe the file
        transcribeUseCase.transcribeFile(wavFile, object : TranscriptionListener {
            override fun onFinalResult(text: String) {
                results.append(text).append(" ")
            }
            // ... other callbacks
        })
    }
}

// Call this from a button to stop recording
fun stopRecording() {
    wavRecorder.stopRecording()
}
```

### Transcribe Uploaded Audio

```kotlin
// Convert any format → 16kHz WAV → transcribe
fun transcribeUploadedFile(uri: Uri) {
    scope.launch {
        val outputFile = File(cacheDir, "converted.wav")
        val result = audioConverter.convertToWav(uri, outputFile)

        result.onSuccess { wavFile ->
            transcribeUseCase.transcribeFile(wavFile, myListener)
        }.onFailure { error ->
            Log.e("MyApp", "Conversion failed", error)
        }
    }
}
```

---

## Step 9 — Implement LLM Processing

### Load the Model

```kotlin
fun loadModel(modelPath: String) {
    scope.launch {
        val result = llmProcessor.loadModel(this@MyActivity, modelPath)
        result.onSuccess {
            Log.d("MyApp", "LLM ready")
        }.onFailure { error ->
            Log.e("MyApp", "LLM load failed", error)
        }
    }
}
```

### Extract Medical Report

```kotlin
fun extractReport(transcriptionText: String) {
    scope.launch {
        val result = extractReportUseCase.execute(transcriptionText) { field, value ->
            // Called incrementally as each field is parsed
            withContext(Dispatchers.Main) {
                when (field) {
                    "diagnosis"  -> diagnosisTextView.text = value
                    "medication" -> medicationTextView.text = value
                    "tests"      -> testsTextView.text = value
                    "followUp"   -> followUpTextView.text = value
                }
            }
        }

        result.onSuccess { report ->
            // report.diagnosis, report.medication, report.otherTests, report.followUp
            Log.d("MyApp", "Report: $report")
        }.onFailure { error ->
            Log.e("MyApp", "Processing failed", error)
        }
    }
}
```

---

## Step 10 — Wire Up the UI

Here's how to connect the pieces in a simple single-Activity app:

```kotlin
// Button: Start Recording
btnRecord.setOnClickListener {
    if (isRecording) {
        wavRecorder.stopRecording()
        isRecording = false
    } else {
        isRecording = true
        scope.launch {
            val wav = wavRecorder.startRecording()
            transcribeUseCase.transcribeFile(wav, myListener)
        }
    }
}

// Button: Process with AI
btnProcess.setOnClickListener {
    val text = results.toString().trim()
    if (text.isNotEmpty() && llmProcessor.isModelLoaded) {
        extractReport(text)
    }
}

// Button: Upload Audio
btnUpload.setOnClickListener {
    audioPickerLauncher.launch("audio/*")
}
```

---

## Integration Scenarios

### Scenario A — Full Stack

**Use all modules** for the complete flow: record → transcribe → extract report.

```kotlin
dependencies {
    implementation(project(":core"))
    implementation(project(":domain"))
    implementation(project(":data:audio"))
    implementation(project(":data:llm"))
}
```

APK size impact: ~50 MB (mainly Vosk model in assets).

---

### Scenario B — Transcription Only

**Use `:data:audio` only** — no LLM, no report extraction. Good for apps that just need Hindi speech-to-text.

```kotlin
dependencies {
    implementation(project(":core"))
    implementation(project(":domain"))
    implementation(project(":data:audio"))
    // No :data:llm
}
```

You only use `TranscriptionRepository`, `WavRecorder`, and `AudioConverter`. Ignore `LlmRepository` and `ExtractReportUseCase`.

---

### Scenario C — LLM Only

**Use `:data:llm` only** — bring your own transcription text from any source (Google Speech, manual typing, etc.).

```kotlin
dependencies {
    implementation(project(":core"))
    implementation(project(":domain"))
    implementation(project(":data:llm"))
    // No :data:audio
}
```

```kotlin
val llm = LlmProcessor()
llm.loadModel(context, "/path/to/model.task")

val result = llm.processTranscription("मरीज़ को बुखार है...") { field, value ->
    // handle incrementally
}
```

No NDK configuration, no Vosk model, no audio permissions needed.

---

### Scenario D — Audio Utilities Only

Use just `WavRecorder` and `AudioConverter` without Vosk transcription. Good for apps that need audio recording in 16kHz WAV format.

```kotlin
// You still need :data:audio but only use WavRecorder / AudioConverter
val recorder = WavRecorder(cacheDir)
scope.launch {
    val wavFile = recorder.startRecording()
    // ... use the WAV file however you want
}
recorder.stopRecording()
```

---

## Minimal Working Example

A complete, working Activity that records → transcribes → extracts a report:

```kotlin
class MinimalExampleActivity : AppCompatActivity() {

    private val vosk = VoskTranscriptionService()
    private val llm = LlmProcessor()
    private lateinit var recorder: WavRecorder
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val text = StringBuilder()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_example)

        recorder = WavRecorder(cacheDir)

        // 1. Init Vosk
        vosk.initModel(this, object : TranscriptionListener {
            override fun onModelReady() {
                findViewById<Button>(R.id.btnRecord).isEnabled = true
            }
            override fun onModelError(e: String) { Toast.makeText(this@MinimalExampleActivity, e, Toast.LENGTH_LONG).show() }
            override fun onPartialResult(t: String) {}
            override fun onFinalResult(t: String) { text.append(t).append(" ") }
            override fun onError(e: String) {}
        })

        // 2. Load LLM (provide your own model path)
        scope.launch {
            llm.loadModel(this@MinimalExampleActivity, "/sdcard/model.task")
        }

        // 3. Record button
        var recording = false
        findViewById<Button>(R.id.btnRecord).setOnClickListener {
            if (recording) {
                recorder.stopRecording()
                recording = false
            } else {
                recording = true
                scope.launch {
                    val wav = recorder.startRecording()
                    vosk.transcribeFile(wav, object : TranscriptionListener {
                        override fun onModelReady() {}
                        override fun onModelError(e: String) {}
                        override fun onPartialResult(t: String) {}
                        override fun onFinalResult(t: String) {
                            text.append(t).append(" ")
                            runOnUiThread {
                                findViewById<TextView>(R.id.txtResult).text = text
                            }
                        }
                        override fun onError(e: String) {}
                    })
                }
            }
        }

        // 4. Process button
        findViewById<Button>(R.id.btnProcess).setOnClickListener {
            scope.launch {
                val result = llm.processTranscription(text.toString()) { _, _ -> }
                result.onSuccess { report ->
                    runOnUiThread {
                        findViewById<TextView>(R.id.txtResult).text = """
                            |Diagnosis: ${report.diagnosis}
                            |Medication: ${report.medication}
                            |Tests: ${report.otherTests}
                            |Follow-up: ${report.followUp}
                        """.trimMargin()
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel(); vosk.destroy(); llm.destroy()
    }
}
```

---

## ProGuard / R8 Rules

If you enable minification (`isMinifyEnabled = true`), add these rules:

```proguard
# Vosk / JNA
-keep class org.vosk.** { *; }
-keep class com.sun.jna.** { *; }
-dontwarn com.sun.jna.**

# LiteRT-LM
-keep class com.google.ai.edge.litertlm.** { *; }

# DocScriptAI domain models
-keep class com.docscriptai.domain.model.** { *; }
```

---

## Common Integration Errors

| Error | Cause | Fix |
|---|---|---|
| `Duplicate files: libc++_shared.so` | Vosk and JNA both bundle native libs | Add `pickFirsts += setOf("**/libc++_shared.so")` to `packaging.jniLibs` |
| `Model not found` on first launch | Assets not copied | Verify `assets/model-hi/` exists in your app module |
| `java.lang.UnsatisfiedLinkError` | Missing native libraries for architecture | Check `abiFilters` includes your device's ABI |
| `Recognizer init failed` | Model directory corrupted or incomplete | Re-copy the `model-hi/` directory; ensure all files are present |
| `LLM model not loaded` | `loadModel()` not called or failed | Verify the `.task` / `.litertlm` file exists and is > 100 KB |
| `CancellationException` during LLM | Coroutine scope cancelled (e.g., Activity destroyed) | Use `SupervisorJob()` and handle lifecycle properly |
| `Build error: Unresolved reference` | Module not included in `settings.gradle.kts` | Add the missing `include(":module")` line |
| Blank transcription | Audio not 16kHz mono WAV | Use `AudioConverter` to convert uploaded files first |

---

## Version Compatibility Matrix

| DocScriptAI Version | AGP | Kotlin | Vosk | LiteRT-LM | Min SDK |
|---|---|---|---|---|---|
| 1.0 (current) | 8.8.0 | 2.3.20 | 0.3.32 | 0.9.0 | 26 |

**Backward compatibility notes:**
- AGP ≥ 8.2 is required for `namespace` in `build.gradle.kts`
- Kotlin ≥ 1.9 is required for `data object` syntax in `ResultState.kt`
- Vosk 0.3.32 requires JNA 5.12.1 — mismatched versions cause native crashes
- LiteRT-LM 0.9.0 requires `compileSdk ≥ 34`
