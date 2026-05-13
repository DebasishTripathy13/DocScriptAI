# DocScriptAI — AI Medical Scribe

An on-device AI medical scribe Android app that records doctor-patient conversations in Hindi, transcribes them using Vosk, and extracts structured medical reports using LiteRT-LM — all running **locally on the device** with zero cloud dependency.

## Architecture

This project follows **Android's recommended modularization pattern** (App → Domain → Data → Core) for clean separation of concerns, enforced dependency direction, and faster incremental builds.

### Module Structure

```
DocScriptAI/
├── :app                    ← Thin UI orchestrator (Activity, ViewBinding, permissions)
├── :domain                 ← Pure business logic (models, repository interfaces, use-cases)
├── :data:audio             ← Audio data layer (Vosk STT, WAV recording, audio conversion)
├── :data:llm               ← LLM data layer (LiteRT-LM inference, prompt engineering)
└── :core                   ← Shared configuration & utilities
```

### Dependency Graph

```
         ┌─────────┐
         │  :app    │
         └────┬─────┘
              │ depends on
    ┌─────────┼──────────┐
    ▼         ▼          ▼
:data:audio :data:llm :domain
    │         │          │
    └────┬────┘          │
         │ depends on    │
         ▼               │
      :domain ◄──────────┘
         │
         ▼
       :core
```

**Key rules:**
- Dependencies always point **inward** (App → Data → Domain → Core)
- `:domain` defines **interfaces** (`TranscriptionRepository`, `LlmRepository`)
- `:data` modules provide **implementations** (`VoskTranscriptionService`, `LlmProcessor`)
- `:app` wires implementations to interfaces (manual DI in `onCreate`)

### Module Details

| Module | Type | Contains |
|---|---|---|
| `:app` | `com.android.application` | `MainActivity`, resources, assets, manifest |
| `:domain` | `com.android.library` | `MedicalReport` model, `TranscriptionRepository` / `LlmRepository` interfaces, `TranscribeAudioUseCase` / `ExtractReportUseCase` |
| `:data:audio` | `com.android.library` | `VoskTranscriptionService`, `WavRecorder`, `AudioConverter` |
| `:data:llm` | `com.android.library` | `LlmProcessor` (prompts, parsing, LiteRT-LM engine) |
| `:core` | `com.android.library` | `AudioConfig` constants, `ResultState` sealed class |

## Features

- 🎙️ **Hindi speech recognition** via Vosk (offline, on-device)
- 🤖 **AI medical report extraction** via LiteRT-LM (offline, on-device)
- 📤 **Audio file upload** with automatic format conversion to 16kHz WAV
- 📥 **Google Drive model download** with progress tracking
- 🔍 **Auto-scan** for locally available LLM model files

## Requirements

- Android 8.0+ (API 26)
- ~45 MB for Vosk Hindi model (bundled in assets)
- LLM model file (`.task` or `.litertlm`) — loaded at runtime

## Building

```bash
./gradlew assembleDebug
```

## License

MIT
