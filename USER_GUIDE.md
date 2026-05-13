# DocScriptAI — User Guide

A step-by-step guide on how to install, set up, and use the DocScriptAI AI Medical Scribe application.

---

## Table of Contents

- [Installation](#installation)
- [First Launch Setup](#first-launch-setup)
- [Loading the AI Model](#loading-the-ai-model)
- [Recording a Consultation](#recording-a-consultation)
- [Uploading Audio Files](#uploading-audio-files)
- [Viewing the Medical Report](#viewing-the-medical-report)
- [Tips for Best Results](#tips-for-best-results)
- [Troubleshooting](#troubleshooting)
- [FAQ](#faq)

---

## Installation

### Prerequisites

- Android device running **Android 8.0 (Oreo)** or later
- At least **500 MB free storage** (for models)
- Supported architectures: `arm64-v8a`, `armeabi-v7a`, `x86_64`, `x86`

### Building from Source

```bash
# Clone the repo
git clone https://github.com/DebasishTripathy13/DocScriptAI.git
cd DocScriptAI

# Build debug APK
./gradlew assembleDebug

# The APK will be at:
# app/build/outputs/apk/debug/app-debug.apk
```

Install the APK on your device via:
```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

---

## First Launch Setup

### Step 1: Grant Microphone Permission

On first launch, the app will request **microphone permission**. This is required for recording consultations.

- Tap **"Allow"** when the permission dialog appears
- If denied, the app cannot record audio — go to Settings → Apps → DocScriptAI → Permissions to enable it

### Step 2: Wait for Vosk Model

The app bundles a Hindi speech recognition model (~45 MB). On first launch, it unpacks this model from the APK to internal storage:

- You'll see **"🔄 Vosk लोड हो रहा है"** (Vosk Loading) badge
- Wait for it to change to **"✓ Vosk तैयार"** (Vosk Ready)
- This takes 10-30 seconds on first launch, instant on subsequent launches

### Step 3: Load AI Model

The LLM model for report extraction is **not bundled** in the APK (it's too large, typically 500MB–2GB). You need to load it separately.

---

## Loading the AI Model

You have **three options** to load the AI model:

### Option A: Auto-Scan (Easiest)

If you've previously downloaded a `.task` or `.litertlm` model file, the app automatically scans these locations on startup:

- App's external files directory
- App's internal files directory
- App's cache directory
- `Downloads/` folder

If found, it loads automatically — you'll see the **"✓ AI तैयार"** badge.

### Option B: Google Drive Download

1. Tap the **"Google Drive से डाउनलोड करें"** button
2. The download starts via Android's Download Manager
3. You'll see a progress notification
4. Once complete, the model loads automatically

> **Note:** The GDrive file ID is configured in `res/values/strings.xml` → `gdrive_model_file_id`. Update this if you're using your own model.

### Option C: Manual File Selection

1. Tap the **"LLM मॉडल लोड करें"** button
2. Browse your device for a `.task` or `.litertlm` file
3. The app copies it to cache and loads it

> **Supported model formats:** `.task` (TensorFlow Lite Task Library) and `.litertlm` (LiteRT-LM native format)

---

## Recording a Consultation

### Step 1: Start Recording

1. Ensure both badges show ready: **"✓ Vosk तैयार"** and **"✓ AI तैयार"**
2. Tap the large **microphone button** (🎙️) in the center
3. The button turns **red** and a pulse animation starts
4. The status changes to **"रिकॉर्डिंग जारी है…"**

### Step 2: Speak Naturally

- Hold the phone near the conversation
- Speak in **Hindi** (the Vosk model is trained on Hindi)
- The app works best with clear speech and minimal background noise
- There is no time limit — record as long as needed

### Step 3: Stop Recording

1. Tap the **red button** again to stop
2. The app automatically saves the WAV file and starts transcribing
3. You'll see the transcription appearing in the **Transcription card** in real-time

### Step 4: Review Transcription

- The transcription text appears below the record button
- Partial results update live, final results are committed after silence pauses
- If the transcription looks incomplete, you can record again — new text is **appended** to existing text

### Step 5: Process with AI

1. Once transcription is complete, tap **"🤖 AI से प्रोसेस करें →"**
2. The app navigates to the **Report page**
3. Processing takes 10-60 seconds depending on your device and model size

---

## Uploading Audio Files

Instead of recording live, you can upload pre-recorded audio files:

1. Tap **"ऑडियो अपलोड करें"** (Upload Audio)
2. Select any audio file (MP3, AAC, M4A, WAV, OGG, etc.)
3. The app automatically converts it to 16kHz mono WAV
4. Transcription starts automatically after conversion

> **Supported formats:** Any format supported by Android's MediaCodec — MP3, AAC, M4A, OGG, FLAC, WAV, AMR, etc.

---

## Viewing the Medical Report

After tapping "Process with AI", the **Report page** shows:

### Report Cards

| Card | Color | Contents |
|---|---|---|
| 🩺 **Diagnosis** | Green | Disease, symptoms, clinical observations |
| 💊 **Medication** | Blue | Medicines with dosage in X-X-X format (morning-afternoon-night) |
| 🔬 **Tests** | Pink | Lab tests and investigations ordered |
| 📅 **Follow-up** | Orange | When and why to return |

Each card **slides in with an animation** as it's extracted by the AI.

### Understanding the Dosage Format

Medications are shown in the Indian prescription format:

```
पैरासिटामोल 500mg (1-1-1 सुबह-दोपहर-रात)
```

This means: **1 tablet morning, 1 afternoon, 1 night**

More examples:
- `(1-0-1)` = morning and night only
- `(0-0-1)` = night only
- `(1-0-0)` = morning only

### Starting a New Recording

Tap **"🎙️ New Recording"** to clear everything and go back to the capture page.

---

## Tips for Best Results

### For Recording Quality
- 🎯 Hold the phone **30-60 cm** from the speaker
- 🔇 Minimize **background noise** (close doors, turn off fans)
- 🗣️ Ask both doctor and patient to speak **clearly**
- ⏱️ Keep recordings under **10 minutes** for best LLM accuracy
- 🔋 Ensure sufficient **battery** (LLM processing is CPU-intensive)

### For AI Accuracy
- 📝 The AI works best when the conversation includes **explicit mentions** of:
  - Disease name or symptoms
  - Medicine names and dosages
  - Test names
  - Follow-up instructions
- ❌ If the doctor doesn't mention something, the report will show **"उल्लेख नहीं"** (not mentioned)
- ⚠️ Very long transcriptions (>6000 characters) are automatically trimmed — the middle section is removed to fit the AI's context window

### For Model Selection
- Larger models (1-2 GB) produce better results but are slower
- Smaller models (500 MB) are faster but may miss nuances
- CPU inference works on all devices; GPU is not currently used

---

## Troubleshooting

### "Vosk विफल" (Vosk Failed)
- **Cause:** Insufficient storage to unpack the model
- **Fix:** Free up at least 100 MB of internal storage and restart the app

### Recording button stays disabled
- **Cause:** Vosk model hasn't finished loading
- **Fix:** Wait for the "✓ Vosk तैयार" badge to appear

### "Process" button stays disabled
- **Cause:** Either no transcription text or LLM not loaded
- **Fix:** Record/upload audio first, and ensure the LLM model is loaded

### Transcription is empty or garbled
- **Cause:** Audio quality too low or not Hindi
- **Fix:** Record in a quieter environment, speak closer to the mic

### AI report shows "उल्लेख नहीं" for all fields
- **Cause:** The transcription didn't contain medical information, or the model is too small
- **Fix:** Verify the transcription contains medical terms, try a larger model

### App crashes during AI processing
- **Cause:** Insufficient RAM for the LLM model
- **Fix:** Close other apps, or use a smaller model file

### GDrive download fails
- **Cause:** Network issue or incorrect file ID
- **Fix:** Check internet connection; verify `gdrive_model_file_id` in `strings.xml`

---

## FAQ

**Q: Does the app send any data to the internet?**  
A: No. All processing happens **entirely on-device**. The only network call is the optional GDrive model download. No audio, transcription, or medical data ever leaves the device.

**Q: What languages are supported?**  
A: Currently **Hindi only**. The Vosk model (`model-hi`) is trained on Hindi speech. Supporting other languages would require bundling additional Vosk models.

**Q: Can I use this for English consultations?**  
A: Not with the current bundled model. You would need to replace `assets/model-hi/` with a Vosk English model and adjust the LLM prompt.

**Q: How large are the model files?**  
A: The Vosk Hindi model is ~45 MB (bundled). LLM models range from 500 MB to 2 GB depending on the model.

**Q: Does this replace a real medical scribe?**  
A: No. This is a **tool to assist** medical documentation. All AI-generated reports should be reviewed and verified by a qualified medical professional before use.

**Q: Can I record in a noisy clinic?**  
A: The app works in moderate noise, but accuracy decreases significantly in very noisy environments. Quiet rooms or directional microphones give the best results.
