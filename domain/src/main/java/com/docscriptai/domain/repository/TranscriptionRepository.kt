package com.docscriptai.domain.repository

import android.content.Context
import java.io.File

/**
 * Abstract contract for speech-to-text transcription.
 * Implemented by VoskTranscriptionService in :data:audio.
 */
interface TranscriptionRepository {

    /** Whether the underlying speech model is loaded and ready. */
    val isModelReady: Boolean

    /** Initialize/unpack the speech recognition model. */
    fun initModel(context: Context, listener: TranscriptionListener)

    /** Transcribe a WAV file asynchronously, reporting results via [listener]. */
    fun transcribeFile(file: File, listener: TranscriptionListener)

    /** Cancel any in-progress file transcription. */
    fun stopStreamTranscription()

    /** Start live microphone recognition, reporting results via [listener]. */
    fun startLiveRecognition(listener: TranscriptionListener)

    /** Stop live microphone recognition. */
    fun stopLiveRecognition()

    /** Release all resources. */
    fun destroy()
}
