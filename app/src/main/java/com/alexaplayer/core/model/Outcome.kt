package com.alexaplayer.core.model

/** Result of an operation that can fail for a reason the UI needs to explain. */
sealed interface Outcome<out T> {
    data class Success<T>(val value: T) : Outcome<T>
    data class Failure(val reason: FailureReason) : Outcome<Nothing>
}

enum class FailureReason { NAME_BLANK, NAME_TAKEN, INVALID_URL, NOT_FOUND, STORAGE }

inline fun <T, R> Outcome<T>.map(transform: (T) -> R): Outcome<R> = when (this) {
    is Outcome.Success -> Outcome.Success(transform(value))
    is Outcome.Failure -> this
}
