package com.docscriptai.core

/**
 * Generic sealed class representing the state of an async operation.
 */
sealed class ResultState<out T> {
    data object Loading : ResultState<Nothing>()
    data class Success<T>(val data: T) : ResultState<T>()
    data class Error(val exception: Throwable) : ResultState<Nothing>()
}
