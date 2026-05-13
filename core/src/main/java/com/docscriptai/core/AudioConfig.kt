package com.docscriptai.core

/**
 * Shared audio configuration constants used across :data:audio and :data:llm modules.
 */
object AudioConfig {
    /** Sample rate for Vosk recognition and WAV recording (Hz). */
    const val SAMPLE_RATE = 16000

    /** Sample rate as Float for Vosk API. */
    const val SAMPLE_RATE_FLOAT = 16000.0f

    /** Stream buffer size in bytes — ~256 ms at 16kHz 16-bit mono. */
    const val STREAM_BUFFER_BYTES = 8192

    /** Target sample rate for audio conversion (Hz). */
    const val TARGET_SAMPLE_RATE = 16000

    /** MediaCodec timeout in microseconds. */
    const val CODEC_TIMEOUT_US = 10000L
}
