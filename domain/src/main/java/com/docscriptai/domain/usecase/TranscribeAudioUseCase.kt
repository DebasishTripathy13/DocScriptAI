package com.docscriptai.domain.usecase

import com.docscriptai.domain.repository.TranscriptionListener
import com.docscriptai.domain.repository.TranscriptionRepository
import java.io.File

/**
 * Use-case that orchestrates audio transcription.
 * Delegates to [TranscriptionRepository] — keeps the app layer decoupled from
 * the concrete Vosk implementation.
 */
class TranscribeAudioUseCase(
    private val repository: TranscriptionRepository
) {

    /** Transcribe a WAV [file], reporting results through [listener]. */
    fun transcribeFile(file: File, listener: TranscriptionListener) {
        repository.transcribeFile(file, listener)
    }

    /** Start live microphone recognition. */
    fun startLiveRecognition(listener: TranscriptionListener) {
        repository.startLiveRecognition(listener)
    }

    /** Stop any in-progress file transcription. */
    fun stopTranscription() {
        repository.stopStreamTranscription()
    }

    /** Stop live microphone recognition. */
    fun stopLiveRecognition() {
        repository.stopLiveRecognition()
    }
}
