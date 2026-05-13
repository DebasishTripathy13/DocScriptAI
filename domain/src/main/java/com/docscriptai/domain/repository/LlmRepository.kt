package com.docscriptai.domain.repository

import android.content.Context
import com.docscriptai.domain.model.MedicalReport

/**
 * Abstract contract for LLM-based medical report extraction.
 * Implemented by LlmProcessor in :data:llm.
 */
interface LlmRepository {

    /** Whether the LLM model is loaded and ready for inference. */
    val isModelLoaded: Boolean

    /** Load a model file from the given [modelPath]. */
    suspend fun loadModel(context: Context, modelPath: String): Result<Unit>

    /**
     * Process transcription text through the LLM and extract a structured [MedicalReport].
     * Calls [onFieldDone] as each field is extracted, enabling incremental UI updates.
     */
    suspend fun processTranscription(
        text: String,
        onFieldDone: suspend (field: String, value: String) -> Unit = { _, _ -> }
    ): Result<MedicalReport>

    /** Returns true if the given text would be truncated to fit the context window. */
    fun wouldTruncate(text: String): Boolean

    /** Release the engine and all resources. */
    fun destroy()
}
