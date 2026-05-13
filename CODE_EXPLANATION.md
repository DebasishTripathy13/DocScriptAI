# DocScriptAI — Code Explanation

A detailed walkthrough of every module, class, and design decision in the DocScriptAI codebase.

---

## Table of Contents

- [Project Overview](#project-overview)
- [Module Breakdown](#module-breakdown)
  - [:core Module](#core-module)
  - [:domain Module](#domain-module)
  - [:data:audio Module](#dataaudio-module)
  - [:data:llm Module](#datallm-module)
  - [:app Module](#app-module)
- [Data Flow](#data-flow)
- [Key Algorithms](#key-algorithms)
- [Concurrency Model](#concurrency-model)

---

## Project Overview

DocScriptAI is an **on-device AI medical scribe** for Android. It records doctor-patient conversations in Hindi, transcribes them using the Vosk speech engine, and extracts structured medical reports using an on-device LLM (LiteRT-LM). Everything runs **locally** — zero cloud dependency, zero data leakage.

### Tech Stack

| Technology | Purpose |
|---|---|
| **Kotlin** | Primary language (100% Kotlin, no Java) |
| **Vosk** (`com.alphacephei:vosk-android`) | Offline Hindi speech-to-text |
| **LiteRT-LM** (`com.google.ai.edge.litertlm`) | On-device LLM inference |
| **Navigation Component** | Fragment-based 2-page navigation |
| **ViewBinding** | Type-safe view access |
| **Coroutines** | Async operations (recording, transcription, LLM) |
| **Gradle Version Catalog** | Centralized dependency management |

---

## Module Breakdown

### :core Module

**Path:** `core/src/main/java/com/docscriptai/core/`  
**Type:** Android Library  
**Dependencies:** None (leaf node)

The `:core` module contains shared constants and utility types used across all other modules.

#### `AudioConfig.kt`

```kotlin
object AudioConfig {
    const val SAMPLE_RATE = 16000           // Vosk requires 16kHz
    const val SAMPLE_RATE_FLOAT = 16000.0f  // Vosk API takes Float
    const val STREAM_BUFFER_BYTES = 8192    // ~256ms at 16kHz 16-bit mono
    const val TARGET_SAMPLE_RATE = 16000    // Audio conversion target
    const val CODEC_TIMEOUT_US = 10000L     // MediaCodec dequeue timeout
}
```

**Why these values?**
- **16kHz**: Vosk's Hindi model (`model-hi`) is trained on 16kHz audio. Higher sample rates waste memory; lower rates lose speech quality.
- **8192 bytes buffer**: At 16kHz × 16-bit = 32,000 bytes/sec, this gives ~256ms chunks — a good balance between latency and CPU efficiency.
- **10ms codec timeout**: Prevents `MediaCodec.dequeueOutputBuffer()` from blocking too long during audio conversion.

#### `ResultState.kt`

```kotlin
sealed class ResultState<out T> {
    data object Loading : ResultState<Nothing>()
    data class Success<T>(val data: T) : ResultState<T>()
    data class Error(val exception: Throwable) : ResultState<Nothing>()
}
```

A generic sealed class for representing async operation states. Used for type-safe state management across the app.

---

### :domain Module

**Path:** `domain/src/main/java/com/docscriptai/domain/`  
**Type:** Android Library  
**Dependencies:** `:core`

The domain layer contains **business logic contracts** — models, repository interfaces, and use-cases. It defines *what* the app can do without knowing *how*.

#### `model/MedicalReport.kt`

```kotlin
data class MedicalReport(
    val diagnosis: String,      // डॉक्टर — disease/symptoms
    val medication: String,     // दवाई — medicines with dosage (X-X-X format)
    val otherTests: String,     // जांच — lab tests ordered
    val followUp: String        // फॉलोअप — when to return
)
```

**Design decision:** Dosage was originally a separate field but was merged into `medication` because doctors in Hindi consultations typically mention the medicine and its dosage together (e.g., "पैरासिटामोल 500mg दिन में तीन बार"). Splitting them caused parsing errors.

#### `repository/TranscriptionListener.kt`

```kotlin
interface TranscriptionListener {
    fun onModelReady()
    fun onModelError(error: String)
    fun onPartialResult(text: String)   // Interim hypothesis (updates in real-time)
    fun onFinalResult(text: String)     // Committed sentence after silence
    fun onError(error: String)
}
```

This callback interface mirrors Vosk's `RecognitionListener` but lives in the domain layer so the `:app` module never directly depends on Vosk classes.

**Partial vs Final results:**
- `onPartialResult`: Fires continuously as the user speaks — the text changes with each update ("मेरे" → "मेरे सिर" → "मेरे सिर में दर्द")
- `onFinalResult`: Fires after a silence pause — the finalized, committed sentence

#### `repository/TranscriptionRepository.kt`

```kotlin
interface TranscriptionRepository {
    val isModelReady: Boolean
    fun initModel(context: Context, listener: TranscriptionListener)
    fun transcribeFile(file: File, listener: TranscriptionListener)
    fun stopStreamTranscription()
    fun startLiveRecognition(listener: TranscriptionListener)
    fun stopLiveRecognition()
    fun destroy()
}
```

Defines the contract for any speech-to-text engine. Currently implemented by `VoskTranscriptionService`, but could be swapped for Google Speech, Whisper, etc.

#### `repository/LlmRepository.kt`

```kotlin
interface LlmRepository {
    val isModelLoaded: Boolean
    suspend fun loadModel(context: Context, modelPath: String): Result<Unit>
    suspend fun processTranscription(
        text: String,
        onFieldDone: suspend (field: String, value: String) -> Unit
    ): Result<MedicalReport>
    fun wouldTruncate(text: String): Boolean
    fun destroy()
}
```

**Key design choices:**
- `suspend` functions: LLM inference is CPU-intensive (seconds), so it must run on a background thread via coroutines.
- `onFieldDone` callback: Enables incremental UI updates — each field (diagnosis, medication, etc.) appears as soon as it's parsed, rather than waiting for the entire response.
- `wouldTruncate()`: Lets the UI warn users before processing that their input will be trimmed.

#### `usecase/TranscribeAudioUseCase.kt`

Thin wrapper around `TranscriptionRepository`. Exists to enforce the domain layer's role as the entry point for business operations:

```kotlin
class TranscribeAudioUseCase(private val repository: TranscriptionRepository) {
    fun transcribeFile(file: File, listener: TranscriptionListener) { ... }
    fun startLiveRecognition(listener: TranscriptionListener) { ... }
    fun stopTranscription() { ... }
    fun stopLiveRecognition() { ... }
}
```

#### `usecase/ExtractReportUseCase.kt`

Wraps `LlmRepository.processTranscription()`:

```kotlin
class ExtractReportUseCase(private val repository: LlmRepository) {
    suspend fun execute(text: String, onFieldDone: suspend (String, String) -> Unit): Result<MedicalReport>
    fun wouldTruncate(text: String): Boolean
}
```

---

### :data:audio Module

**Path:** `data/audio/src/main/java/com/docscriptai/data/audio/`  
**Type:** Android Library  
**Dependencies:** `:core`, `:domain`, Vosk, JNA, Coroutines

This module provides the concrete audio processing implementations.

#### `VoskTranscriptionService.kt`

Implements `TranscriptionRepository`. This is the most complex class in the audio module.

**Model initialization flow:**
```
initModel() → StorageService.unpack(assets/model-hi/) → Model object → isModelReady = true
```

Vosk's `StorageService.unpack()` extracts the bundled `model-hi/` directory from APK assets to internal storage on first run (~45MB). Subsequent launches use the cached copy.

**File transcription flow:**
```
transcribeFile(wav) → skipWavHeader() → Recognizer → SpeechStreamService → callbacks
```

**WAV header parsing (`skipWavHeader`):**
The standard WAV header is 44 bytes, but many WAV files have extra metadata chunks (e.g., `LIST`, `fact`). This method walks the RIFF chunk structure to find the `data` chunk regardless of header size:

```kotlin
// Walk chunks until "data" is found
while (true) {
    val chunkId = ByteArray(4).also { dis.readFully(it) }
    val chunkSize = /* read 4 bytes little-endian */
    if (String(chunkId) == "data") return true  // found it!
    dis.skipBytes(chunkSize)  // skip non-data chunks
}
```

**Live recognition flow:**
```
startLiveRecognition() → Recognizer → SpeechService(microphone) → callbacks
```

Uses Android's `AudioRecord` internally (via Vosk's `SpeechService`) to stream microphone audio directly to the recognizer.

#### `WavRecorder.kt`

Records raw microphone audio to a WAV file.

**Recording flow:**
1. Create `AudioRecord` at 16kHz, 16-bit, mono
2. Write a **placeholder** 44-byte WAV header
3. Stream PCM data from mic → file
4. On stop: seek back to byte 0 and write the **real** header with correct `dataSize`

**Why write the header twice?**
WAV requires the total data size in the header (bytes 4-7 and 40-43), but we don't know the final size until recording stops. So we write zeros first, then patch them.

#### `AudioConverter.kt`

Converts any audio format (MP3, AAC, M4A, OGG, etc.) to 16kHz 16-bit mono WAV using Android's native `MediaCodec` + `MediaExtractor`.

**Conversion pipeline:**
```
Input URI → MediaExtractor (demux) → MediaCodec (decode to PCM)
         → Stereo→Mono mix → Resample to 16kHz → WAV file
```

**Resampling algorithm:** Simple linear interpolation between adjacent samples. Not audiophile-quality, but perfectly adequate for speech recognition:

```kotlin
val srcIndex = i * ratio
val index0 = srcIndex.toInt()
val frac = srcIndex - index0
val interpolated = (val0 + frac * (val1 - val0)).toInt().toShort()
```

---

### :data:llm Module

**Path:** `data/llm/src/main/java/com/docscriptai/data/llm/`  
**Type:** Android Library  
**Dependencies:** `:core`, `:domain`, LiteRT-LM, Coroutines

#### `LlmProcessor.kt`

Implements `LlmRepository`. This is the most complex class in the entire project.

**Model loading:**
```kotlin
Engine(EngineConfig(modelPath, backend = Backend.CPU(), cacheDir = ...))
engine.initialize()
```

LiteRT-LM loads `.task` or `.litertlm` model files (typically 500MB–2GB). The `Backend.CPU()` backend is used because GPU support varies across devices.

**Prompt engineering:**

The system instruction is carefully crafted in Hindi to extract exactly 4 fields:

```
डॉक्टर: [disease/symptoms]
दवाई: [medicine (X-X-X morning-afternoon-night)]
जांच: [lab tests]
फॉलोअप: [when to return]
```

**Key prompt design decisions:**
1. **Few-shot examples**: 3 examples are included in the system prompt to demonstrate the exact output format
2. **X-X-X dosage format**: "1-1-1 सुबह-दोपहर-रात" is the standard Indian prescription format
3. **Transliteration rule**: "Paracetamol → पैरासिटामोल" — forces Hindi output for medicine names
4. **Default value**: "उल्लेख नहीं" (not mentioned) prevents hallucination when info is missing

**Input truncation:**
```kotlin
const val MAX_INPUT_CHARS = 6000
const val TRUNCATION_HEAD = 2500  // Keep beginning (chief complaint)
const val TRUNCATION_TAIL = 3200  // Keep end (prescriptions, follow-up)
```

Long consultations are truncated with a middle gap `[...]`. The head/tail split preserves the most important parts — patients describe symptoms at the start, doctors prescribe at the end.

**Response parsing:**

Uses regex to extract the 4 labeled fields from the LLM output:

```kotlin
val FIELD_PATTERN = Regex("""(डॉक्टर|दवाई|जांच|फॉलोअप): *(.*?)(?=\n(?:...|$))""")
```

**Concurrency:** A `Mutex` prevents concurrent LLM calls — the model is single-threaded and would crash if two conversations ran simultaneously.

---

### :app Module

**Path:** `app/src/main/java/com/docscriptai/app/`  
**Type:** Android Application  
**Dependencies:** `:core`, `:domain`, `:data:audio`, `:data:llm`, AndroidX, Material, Navigation

#### `MainActivity.kt`

The thin Activity host. Responsibilities:
- Owns the `NavHostFragment` container
- Initializes both models (Vosk on startup, LLM on demand)
- Handles GDrive download via `DownloadManager`
- Implements `ServiceProvider` interface for fragment DI access
- Manages `BroadcastReceiver` for download completion

#### `SharedViewModel.kt`

Scoped to the Activity's lifecycle so it survives fragment navigation:
- `transcriptionBuilder`: Accumulated transcription text
- `isVoskReady` / `isLlmReady`: LiveData flags observed by `CaptureFragment`

#### `CaptureFragment.kt` (Page 1)

Handles:
- Record button with **pulse ring animation** (`ObjectAnimator` on scale + alpha)
- Audio upload with format conversion
- Live transcription display
- Model management buttons (load, download)
- Navigation to `ReportFragment` on "Process" tap

#### `ReportFragment.kt` (Page 2)

Handles:
- LLM processing on entry (auto-starts when fragment loads)
- **Staggered card entrance animations** — each card slides up with increasing delay (0ms, 100ms, 200ms, 300ms)
- "New Recording" button clears state and pops back

#### `ServiceProvider.kt`

Interface contract so fragments access DI'd services without knowing concrete types:

```kotlin
interface ServiceProvider {
    val transcriptionRepo: TranscriptionRepository
    val llmRepo: LlmRepository
    val wavRecorder: WavRecorder
    val audioConverter: AudioConverter
    val transcribeUseCase: TranscribeAudioUseCase
    val extractReportUseCase: ExtractReportUseCase
}
```

---

## Data Flow

```
┌──────────────┐     WAV file      ┌─────────────────────┐
│  Microphone  │ ───────────────▶  │  VoskTranscription   │
│  (WavRecorder)│                   │  Service             │
└──────────────┘                   │  (:data:audio)       │
                                   └──────────┬──────────┘
┌──────────────┐     WAV file                 │
│  Audio File  │ ───────────────▶             │ Hindi text
│  (AudioConverter)              │             │
└──────────────┘                              ▼
                                   ┌─────────────────────┐
                                   │  LlmProcessor       │
                                   │  (:data:llm)         │
                                   └──────────┬──────────┘
                                              │ MedicalReport
                                              ▼
                                   ┌─────────────────────┐
                                   │  ReportFragment      │
                                   │  (UI display)        │
                                   └─────────────────────┘
```

---

## Key Algorithms

### 1. WAV Header Chunk Walking
Standard 44-byte skip fails on WAV files with metadata. The chunk walker handles any valid RIFF/WAV file.

### 2. Linear Interpolation Resampling
Converts 44.1kHz/48kHz audio to 16kHz for Vosk. Quality is acceptable for speech (not music).

### 3. Regex-Based LLM Output Parsing
Extracts labeled Hindi fields from free-form LLM text using a single compiled regex pattern.

### 4. Head-Tail Truncation
Preserves the diagnostically important beginning and end of long transcriptions while removing the middle.

---

## Concurrency Model

| Operation | Thread | Mechanism |
|---|---|---|
| Vosk model init | Background (Vosk internal) | `StorageService.unpack()` callback |
| WAV recording | `Dispatchers.IO` | `withContext(Dispatchers.IO)` in coroutine |
| Audio conversion | `Dispatchers.IO` | `withContext(Dispatchers.IO)` in coroutine |
| File transcription | Background (Vosk internal) | `SpeechStreamService` thread |
| Live recognition | Background (Vosk internal) | `SpeechService` thread |
| LLM model loading | `Dispatchers.IO` | `withContext(Dispatchers.IO)` + coroutine |
| LLM inference | `Dispatchers.IO` | `Mutex` lock + `withContext(Dispatchers.IO)` |
| UI updates | `Dispatchers.Main` | `runOnUiThread` or `withContext(Main)` |

The `Mutex` in `LlmProcessor` is critical — LiteRT-LM's `Engine` is not thread-safe. Without it, concurrent calls would cause native crashes.
