package com.alexaplayer.playback

import android.os.Bundle
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.SessionCommand

/** Custom session commands that go beyond the standard transport controls. */
@UnstableApi
object SessionCommands {

    const val TOGGLE_FAVORITE = "com.alexaplayer.command.TOGGLE_FAVORITE"

    /** Bundled with the command so the caller can act on a specific song. */
    const val EXTRA_SONG_ID = "song_id"

    private val EMPTY_EXTRAS = Bundle()

    val TOGGLE_FAVORITE_COMMAND = SessionCommand(TOGGLE_FAVORITE, EMPTY_EXTRAS)

    val all: List<SessionCommand> = listOf(TOGGLE_FAVORITE_COMMAND)
}
