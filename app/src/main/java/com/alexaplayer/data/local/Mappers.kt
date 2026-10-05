package com.alexaplayer.data.local

import com.alexaplayer.core.model.Album
import com.alexaplayer.core.model.Artist
import com.alexaplayer.core.model.LibraryFolder
import com.alexaplayer.core.model.Source
import com.alexaplayer.core.model.Song
import com.alexaplayer.data.local.dao.PlaylistSongRow
import com.alexaplayer.data.local.dao.PlaylistSummaryRow
import com.alexaplayer.data.local.entity.SongEntity

fun SongEntity.toDomain(): Song = Song(
    id = id,
    title = title,
    artist = artist,
    album = album,
    albumId = albumId,
    artistId = artistId,
    durationMs = durationMs,
    trackNumber = trackNumber,
    year = year,
    uri = uri,
    artworkUri = artworkUri,
    dateAdded = dateAdded,
    dateModified = dateModified,
    sizeBytes = sizeBytes,
    mimeType = mimeType,
    source = if (source == Source.STREAM.name) Source.STREAM else Source.LOCAL,
    isFavorite = isFavorite,
    lastPlayedAt = lastPlayedAt,
    playCount = playCount,
    resumePositionMs = resumePositionMs,
)

fun Song.toEntity(): SongEntity = SongEntity(
    id = id,
    title = title,
    artist = artist,
    album = album,
    albumId = albumId,
    artistId = artistId,
    durationMs = durationMs,
    trackNumber = trackNumber,
    year = year,
    uri = uri,
    artworkUri = artworkUri,
    dateAdded = dateAdded,
    dateModified = dateModified,
    sizeBytes = sizeBytes,
    mimeType = mimeType,
    source = source.name,
    isFavorite = isFavorite,
    lastPlayedAt = lastPlayedAt,
    playCount = playCount,
    resumePositionMs = resumePositionMs,
)

fun PlaylistSongRow.toSong(): Song = Song(
    id = songId,
    title = title,
    artist = artist,
    album = album,
    albumId = albumId,
    artistId = artistId,
    durationMs = durationMs,
    trackNumber = trackNumber,
    year = year,
    uri = uri,
    artworkUri = artworkUri,
    dateAdded = dateAdded,
    dateModified = dateModified,
    sizeBytes = sizeBytes,
    mimeType = mimeType,
    source = if (source == Source.STREAM.name) Source.STREAM else Source.LOCAL,
    isFavorite = isFavorite,
    lastPlayedAt = lastPlayedAt,
    playCount = playCount,
    resumePositionMs = resumePositionMs,
)

fun PlaylistSummaryRow.toPlaylist(artworkUris: List<String> = emptyList()) = com.alexaplayer.core.model.Playlist(
    id = id,
    name = name,
    createdAt = createdAt,
    updatedAt = updatedAt,
    songCount = songCount,
    totalDurationMs = totalDurationMs,
    artworkUris = artworkUris,
)

fun List<SongEntity>.toAlbums(): List<Album> = groupBy { it.albumId }
    .map { (albumId, songs) ->
        val first = songs.first()
        Album(
            id = albumId,
            title = first.album.ifBlank { UNKNOWN },
            artist = songs.firstOrNull { it.artist.isNotBlank() }?.artist ?: UNKNOWN,
            artistId = songs.firstOrNull { it.artistId != 0L }?.artistId ?: 0L,
            artworkUri = first.artworkUri,
            year = songs.maxOf { it.year },
            songCount = songs.size,
            totalDurationMs = songs.sumOf { it.durationMs },
        )
    }
    .sortedBy { it.title.lowercase() }

fun List<SongEntity>.toArtists(): List<Artist> = groupBy { it.artistId }
    .map { (artistId, songs) ->
        Artist(
            id = artistId,
            name = songs.firstOrNull { it.artist.isNotBlank() }?.artist ?: UNKNOWN,
            artworkUri = songs.firstOrNull { it.artworkUri != null }?.artworkUri,
            albumCount = songs.map { it.albumId }.distinct().size,
            songCount = songs.size,
        )
    }
    .sortedBy { it.name.lowercase() }

fun List<SongEntity>.toFolders(): List<LibraryFolder> = groupBy { it.folderPath() }
    .map { (path, songs) ->
        LibraryFolder(
            path = path,
            name = path.substringAfterLast('/').ifBlank { path },
            songCount = songs.size,
        )
    }
    .sortedBy { it.name.lowercase() }

private const val UNKNOWN = "—"

private fun SongEntity.folderPath(): String {
    val fromPath = uri.substringBeforeLast('/', missingDelimiterValue = "")
    return if (fromPath.isBlank()) "/" else fromPath
}
