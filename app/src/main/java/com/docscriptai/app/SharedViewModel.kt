package com.docscriptai.app

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

/**
 * Shared ViewModel scoped to the Activity.
 * Holds state that persists across CaptureFragment ↔ ReportFragment navigation.
 */
class SharedViewModel : ViewModel() {

    /** Accumulated transcription text (built up by CaptureFragment). */
    val transcriptionBuilder = StringBuilder()
    val transcriptionText = MutableLiveData("")

    /** Model readiness flags (set by MainActivity). */
    val isVoskReady = MutableLiveData(false)
    val isLlmReady = MutableLiveData(false)
}
