package com.alexaplayer.core.model

/** Where a playable item comes from. Everything else about playback is identical. */
enum class Source { LOCAL, STREAM }

/**
 * A playable item. Local files and user-added streams share one shape so that
 * queues, playlists and favourites never need to special-case either.
 */
data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val artistId: Long,
    val durationMs: Long,
    val trackNumber: Int,
    val year: Int,
    val uri: String,
    val artworkUri: String?,
    val dateAdded: Long,
    val dateModified: Long,
    val sizeBytes: Long,
    val mimeType: String?,
    val source: Source = Source.LOCAL,
    val isFavorite: Boolean = false,
    val lastPlayedAt: Long? = null,
    val playCount: Int = 0,
    val resumePositionMs: Long = 0L,
) {
    val isStream: Boolean get() = source == Source.STREAM
}

data class Album(
    val id: Long,
    val title: String,
    val artist: String,
    val artistId: Long,
    val artworkUri: String?,
    val year: Int,
    val songCount: Int,
    val totalDurationMs: Long,
)

data class Artist(
    val id: Long,
    val name: String,
    val artworkUri: String?,
    val albumCount: Int,
    val songCount: Int,
)

data class LibraryFolder(
    val path: String,
    val name: String,
    val songCount: Int,
)

data class Playlist(
    val id: Long,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    val songCount: Int = 0,
    val totalDurationMs: Long = 0L,
    val artworkUris: List<String> = emptyList(),
)

data class PlaylistWithSongs(
    val playlist: Playlist,
    val songs: List<Song>,
)

/** What the player should do next, described without touching Media3 types. */
data class PlaybackRequest(
    val songs: List<Song>,
    val startIndex: Int,
    val startPositionMs: Long = 0L,
    val shuffle: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.OFF,
)

enum class RepeatMode { OFF, ALL, ONE }

/** Snapshot of the player, safe to hand to the UI layer. */
data class PlaybackState(
    val currentSongId: Long? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val shuffleEnabled: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val queueSongIds: List<Long> = emptyList(),
    val queueIndex: Int = -1,
) {
    val hasSong: Boolean get() = currentSongId != null
    val hasNext: Boolean get() = repeatMode == RepeatMode.ALL || (queueIndex >= 0 && queueIndex < queueSongIds.lastIndex)
    val hasPrevious: Boolean get() = repeatMode == RepeatMode.ALL || queueIndex > 0
    val upNextIds: List<Long>
        get() = if (queueIndex in 0 until queueSongIds.lastIndex) queueSongIds.drop(queueIndex + 1) else emptyList()
}

/**
 * Ticks on its own so that only progress indicators recompose while a song plays,
 * instead of every screen that observes the player.
 */
data class PlaybackProgress(
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val bufferedPositionMs: Long = 0L,
)

/** User facing description of a playback failure. */
enum class PlaybackErrorKey { MISSING_FILE, UNSUPPORTED_FORMAT, DECODER, NETWORK, GENERIC }
