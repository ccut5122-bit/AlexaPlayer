package com.alexaplayer.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.alexaplayer.data.local.entity.PlaylistEntity
import com.alexaplayer.data.local.entity.PlaylistSongEntity
import kotlinx.coroutines.flow.Flow

data class PlaylistSummaryRow(
    val id: Long,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    val songCount: Int,
    val totalDurationMs: Long,
)

data class PlaylistSongRow(
    val playlistId: Long,
    val songId: Long,
    val position: Int,
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
    val source: String,
    val isFavorite: Boolean,
    val lastPlayedAt: Long?,
    val playCount: Int,
    val resumePositionMs: Long,
)

data class PlaylistArtworkRow(
    val playlistId: Long,
    val artworkUri: String,
)

@Dao
interface PlaylistDao {

    @Query(
        """
        SELECT p.id AS id, p.name AS name, p.created_at AS createdAt, p.updated_at AS updatedAt,
               (SELECT COUNT(*) FROM playlist_songs WHERE playlist_id = p.id) AS songCount,
               (SELECT COALESCE(SUM(s.duration_ms), 0) FROM playlist_songs ps
                    JOIN songs s ON s.id = ps.song_id
                    WHERE ps.playlist_id = p.id) AS totalDurationMs
        FROM playlists p
        ORDER BY p.updated_at DESC
        """,
    )
    fun observeAll(): Flow<List<PlaylistSummaryRow>>

    @Query(
        """
        SELECT p.id AS id, p.name AS name, p.created_at AS createdAt, p.updated_at AS updatedAt,
               (SELECT COUNT(*) FROM playlist_songs WHERE playlist_id = p.id) AS songCount,
               (SELECT COALESCE(SUM(s.duration_ms), 0) FROM playlist_songs ps
                    JOIN songs s ON s.id = ps.song_id
                    WHERE ps.playlist_id = p.id) AS totalDurationMs
        FROM playlists p
        WHERE p.id = :id
        """,
    )
    fun observeById(id: Long): Flow<PlaylistSummaryRow?>

    @Query(
        """
        SELECT s.*, ps.playlist_id AS playlistId, ps.position AS position
        FROM playlist_songs ps
        JOIN songs s ON s.id = ps.song_id
        WHERE ps.playlist_id = :playlistId
        ORDER BY ps.position ASC
        """,
    )
    fun observeSongs(playlistId: Long): Flow<List<PlaylistSongRow>>

    @Query("SELECT song_id FROM playlist_songs WHERE playlist_id = :playlistId")
    suspend fun songIds(playlistId: Long): List<Long>

    @Query(
        """
        SELECT ps.playlist_id AS playlistId, s.artwork_uri AS artworkUri
        FROM playlist_songs ps
        JOIN songs s ON s.id = ps.song_id
        WHERE s.artwork_uri IS NOT NULL
        ORDER BY ps.playlist_id ASC, ps.position ASC
        """,
    )
    fun observeArtwork(): Flow<List<PlaylistArtworkRow>>

    @Query("SELECT COALESCE(MAX(position), -1) FROM playlist_songs WHERE playlist_id = :playlistId")
    suspend fun maxPosition(playlistId: Long): Int

    @Query("SELECT name FROM playlists WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun findByName(name: String): PlaylistEntity?

    @Query("SELECT * FROM playlists WHERE id = :id")
    suspend fun getById(id: Long): PlaylistEntity?

    @Query(
        """
        SELECT p.id AS id, p.name AS name, p.created_at AS createdAt, p.updated_at AS updatedAt,
               (SELECT COUNT(*) FROM playlist_songs WHERE playlist_id = p.id) AS songCount,
               (SELECT COALESCE(SUM(s.duration_ms), 0) FROM playlist_songs ps
                    JOIN songs s ON s.id = ps.song_id
                    WHERE ps.playlist_id = p.id) AS totalDurationMs
        FROM playlists p
        WHERE p.name LIKE :pattern ESCAPE '\' COLLATE NOCASE
        ORDER BY p.updated_at DESC
        """,
    )
    fun search(pattern: String): Flow<List<PlaylistSummaryRow>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(playlist: PlaylistEntity): Long

    @Query("UPDATE playlists SET name = :name, updated_at = :timestamp WHERE id = :id")
    suspend fun rename(id: Long, name: String, timestamp: Long)

    @Query("UPDATE playlists SET updated_at = :timestamp WHERE id = :id")
    suspend fun touch(id: Long, timestamp: Long)

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSongs(songs: List<PlaylistSongEntity>)

    @Query("DELETE FROM playlist_songs WHERE playlist_id = :playlistId AND song_id = :songId")
    suspend fun removeSong(playlistId: Long, songId: Long)

    @Query("DELETE FROM playlist_songs WHERE playlist_id = :playlistId")
    suspend fun clear(playlistId: Long)

    @Query("UPDATE playlist_songs SET position = :position WHERE playlist_id = :playlistId AND song_id = :songId")
    suspend fun updatePosition(playlistId: Long, songId: Long, position: Int)

    @Query("UPDATE playlists SET updated_at = :timestamp WHERE id = :playlistId")
    suspend fun touchAfterEdit(playlistId: Long, timestamp: Long)

    /**
     * Reordering arrives as an already-permuted list; writing it in one transaction keeps
     * the intermediate states (duplicate positions) invisible to the UI.
     */
    @Transaction
    suspend fun applyOrder(playlistId: Long, orderedSongIds: List<Long>, timestamp: Long) {
        orderedSongIds.forEachIndexed { index, songId ->
            updatePosition(playlistId, songId, index)
        }
        touchAfterEdit(playlistId, timestamp)
    }
}
