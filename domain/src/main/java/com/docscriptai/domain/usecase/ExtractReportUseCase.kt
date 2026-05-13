package com.docscriptai.domain.usecase

import com.docscriptai.domain.model.MedicalReport
import com.docscriptai.domain.repository.LlmRepository

/**
 * Use-case that orchestrates LLM-based medical report extraction.
 * Delegates to [LlmRepository] — keeps the app layer decoupled from
 * the concrete LiteRT-LM implementation.
 */
class ExtractReportUseCase(
    private val repository: LlmRepository
) {

    /**
     * Extract a structured [MedicalReport] from the given transcription [text].
     * Calls [onFieldDone] incrementally as each field is parsed.
     */
    suspend fun execute(
        text: String,
        onFieldDone: suspend (field: String, value: String) -> Unit = { _, _ -> }
    ): Result<MedicalReport> {
        return repository.processTranscription(text, onFieldDone)
    }

    /** Check whether input [text] would be truncated by the LLM context window. */
    fun wouldTruncate(text: String): Boolean = repository.wouldTruncate(text)
}
