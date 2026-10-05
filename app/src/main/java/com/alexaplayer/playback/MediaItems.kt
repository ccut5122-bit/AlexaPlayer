package com.alexaplayer.playback

import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.alexaplayer.core.model.Song

/**
 * Media3 only needs an id, a uri and display metadata. Everything else (durations,
 * favourites) is re-read from the database on the UI side, so the session payload stays
 * small and never goes stale.
 */
fun Song.toMediaItem(): MediaItem = MediaItem.Builder()
    .setMediaId(id.toString())
    .setUri(uri)
    .setMediaMetadata(
        MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist.takeIf { it.isNotBlank() })
            .setAlbumTitle(album.takeIf { it.isNotBlank() })
            .setArtworkUri(artworkUri?.toUri())
            .setIsBrowsable(false)
            .setIsPlayable(true)
            .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
            .build(),
    )
    .build()

fun List<Song>.toMediaItems(): List<MediaItem> = map { it.toMediaItem() }

fun MediaItem.songId(): Long? = mediaId.toLongOrNull()
