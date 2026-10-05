package com.alexaplayer.ui.viewmodel

import androidx.annotation.StringRes
import com.alexaplayer.R
import com.alexaplayer.core.model.FailureReason

/**
 * A message destined for a snackbar. View models never hold a [android.content.Context], so
 * they describe what happened and the screen resolves the wording.
 */
data class UiMessage(
    @StringRes val res: Int,
    val args: List<Any> = emptyList(),
)

/** Wording for a repository failure, kept in one place so every screen says the same thing. */
fun FailureReason.toUiMessage(name: String = ""): UiMessage = when (this) {
    FailureReason.NAME_BLANK -> UiMessage(R.string.msg_playlist_name_required)
    FailureReason.NAME_TAKEN -> UiMessage(R.string.msg_playlist_name_exists)
    FailureReason.INVALID_URL -> UiMessage(R.string.streams_invalid_url)
    FailureReason.NOT_FOUND -> UiMessage(R.string.error_nothing_to_play)
    FailureReason.STORAGE -> UiMessage(R.string.error_database)
}