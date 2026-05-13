package com.docscriptai.app

import com.docscriptai.data.audio.AudioConverter
import com.docscriptai.data.audio.WavRecorder
import com.docscriptai.domain.repository.LlmRepository
import com.docscriptai.domain.repository.TranscriptionRepository
import com.docscriptai.domain.usecase.ExtractReportUseCase
import com.docscriptai.domain.usecase.TranscribeAudioUseCase

/**
 * Contract that [MainActivity] implements so fragments can access
 * the dependency-injected services without knowing concrete types.
 */
interface ServiceProvider {
    val transcriptionRepo: TranscriptionRepository
    val llmRepo: LlmRepository
    val wavRecorder: WavRecorder
    val audioConverter: AudioConverter
    val transcribeUseCase: TranscribeAudioUseCase
    val extractReportUseCase: ExtractReportUseCase
}
