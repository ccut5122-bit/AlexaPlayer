package com.alexaplayer.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "songs",
    indices = [
        Index("album_id"),
        Index("artist_id"),
        Index("title"),
        Index("is_favorite"),
        Index("date_added"),
    ],
)
data class SongEntity(
    @PrimaryKey val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    @ColumnInfo(name = "album_id") val albumId: Long,
    @ColumnInfo(name = "artist_id") val artistId: Long,
    @ColumnInfo(name = "duration_ms") val durationMs: Long,
    @ColumnInfo(name = "track_number") val trackNumber: Int,
    val year: Int,
    val uri: String,
    @ColumnInfo(name = "artwork_uri") val artworkUri: String?,
    @ColumnInfo(name = "date_added") val dateAdded: Long,
    @ColumnInfo(name = "date_modified") val dateModified: Long,
    @ColumnInfo(name = "size_bytes") val sizeBytes: Long,
    @ColumnInfo(name = "mime_type") val mimeType: String?,
    val source: String,
    @ColumnInfo(name = "is_favorite") val isFavorite: Boolean = false,
    @ColumnInfo(name = "last_played_at") val lastPlayedAt: Long? = null,
    @ColumnInfo(name = "play_count") val playCount: Int = 0,
    @ColumnInfo(name = "resume_position_ms") val resumePositionMs: Long = 0L,
)

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

@Entity(
    tableName = "playlist_songs",
    primaryKeys = ["playlist_id", "song_id"],
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlist_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = SongEntity::class,
            parentColumns = ["id"],
            childColumns = ["song_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("playlist_id"), Index("song_id")],
)
data class PlaylistSongEntity(
    @ColumnInfo(name = "playlist_id") val playlistId: Long,
    @ColumnInfo(name = "song_id") val songId: Long,
    val position: Int,
    @ColumnInfo(name = "added_at") val addedAt: Long,
)

@Entity(tableName = "recent_searches")
data class RecentSearchEntity(
    @PrimaryKey val query: String,
    @ColumnInfo(name = "searched_at") val searchedAt: Long,
)
