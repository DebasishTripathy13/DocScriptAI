package com.docscriptai.domain.repository

/**
 * Callback interface for transcription events.
 * Used by both live-mic and file-based transcription flows.
 */
interface TranscriptionListener {
    fun onModelReady()
    fun onModelError(error: String)
    fun onPartialResult(text: String)
    fun onFinalResult(text: String)
    fun onError(error: String)
}
