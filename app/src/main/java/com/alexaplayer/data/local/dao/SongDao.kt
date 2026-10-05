package com.alexaplayer.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.alexaplayer.data.local.entity.SongEntity
import kotlinx.coroutines.flow.Flow

data class AlbumRow(
    val id: Long,
    val title: String,
    val artist: String,
    val artistId: Long,
    val songCount: Int,
    val totalDurationMs: Long,
    val year: Int,
)

data class ArtistRow(
    val id: Long,
    val name: String,
    val albumCount: Int,
    val songCount: Int,
)


/**
 * One explicit query per sort order. Cheaper to read and to verify than building
 * ORDER BY clauses at runtime, and it keeps every ordering indexable.
 */
@Dao
interface SongDao {

    @Query("SELECT * FROM songs WHERE source = 'LOCAL' ORDER BY date_added DESC LIMIT :limit")
    fun observeRecentlyAdded(limit: Int): Flow<List<SongEntity>>

    @Query(
        """
        SELECT * FROM songs
        WHERE is_favorite = 1
        GROUP BY id
        ORDER BY COALESCE(last_played_at, date_added) DESC
        LIMIT :limit
        """,
    )
    fun observeRecentlyPlayedFavorites(limit: Int): Flow<List<SongEntity>>

    @Query(
        """
        SELECT * FROM songs
        WHERE last_played_at IS NOT NULL
        GROUP BY id
        ORDER BY last_played_at DESC
        LIMIT :limit
        """,
    )
    fun observeRecentlyPlayed(limit: Int): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE is_favorite = 1 ORDER BY title COLLATE NOCASE ASC")
    fun observeFavorites(): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs ORDER BY title COLLATE NOCASE ASC")
    fun observeByTitle(): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs ORDER BY artist COLLATE NOCASE ASC, album COLLATE NOCASE ASC, track_number ASC")
    fun observeByArtist(): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs ORDER BY duration_ms ASC")
    fun observeByDuration(): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs ORDER BY date_added DESC")
    fun observeByRecentlyAdded(): Flow<List<SongEntity>>

    @Query(
        """
        SELECT * FROM songs
        WHERE last_played_at IS NOT NULL
        ORDER BY last_played_at DESC
        """,
    )
    fun observeByRecentlyPlayed(): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE id = :id LIMIT 1")
    fun observeById(id: Long): Flow<SongEntity?>

    @Query("SELECT * FROM songs WHERE id IN (:ids)")
    fun observeByIds(ids: List<Long>): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): SongEntity?

    @Query("SELECT * FROM songs WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<Long>): List<SongEntity>

    @Query("SELECT * FROM songs WHERE album_id = :albumId ORDER BY track_number ASC")
    fun observeByAlbum(albumId: Long): Flow<List<SongEntity>>

    @Query(
        """
        SELECT * FROM songs
        WHERE album_id = :albumId
        GROUP BY id
        ORDER BY track_number ASC
        """,
    )
    suspend fun getByAlbum(albumId: Long): List<SongEntity>

    @Query("SELECT * FROM songs WHERE artist_id = :artistId ORDER BY album COLLATE NOCASE ASC, track_number ASC")
    fun observeByArtist(artistId: Long): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE artist_id = :artistId")
    suspend fun getByArtist(artistId: Long): List<SongEntity>

    @Query("SELECT * FROM songs WHERE source = 'STREAM' ORDER BY date_added DESC")
    fun observeStreams(): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE source = 'STREAM' AND uri = :uri LIMIT 1")
    suspend fun findByUri(uri: String): SongEntity?

    @Query("SELECT COUNT(*) FROM songs")
    fun observeCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM songs WHERE source = 'LOCAL'")
    fun observeLocalCount(): Flow<Int>

    @Query("SELECT * FROM songs")
    suspend fun getAll(): List<SongEntity>

    @Query(
        """
        SELECT * FROM songs
        WHERE title LIKE :pattern ESCAPE '\' COLLATE NOCASE
           OR artist LIKE :pattern ESCAPE '\' COLLATE NOCASE
           OR album LIKE :pattern ESCAPE '\' COLLATE NOCASE
        ORDER BY
            CASE WHEN title LIKE :pattern ESCAPE '\' COLLATE NOCASE THEN 0 ELSE 1 END,
            title COLLATE NOCASE ASC
        LIMIT :limit
        """,
    )
    fun searchSongs(pattern: String, limit: Int): Flow<List<SongEntity>>

    @Query(
        """
        SELECT album_id AS id,
               album AS title,
               artist AS artist,
               artist_id AS artistId,
               COUNT(*) AS songCount,
               COALESCE(SUM(duration_ms), 0) AS totalDurationMs,
               MAX(year) AS year
        FROM songs
        GROUP BY album_id
        ORDER BY album COLLATE NOCASE ASC
        """,
    )
    fun observeAlbumRows(): Flow<List<AlbumRow>>

    @Query(
        """
        SELECT artist_id AS id,
               artist AS name,
               COUNT(DISTINCT album_id) AS albumCount,
               COUNT(*) AS songCount
        FROM songs
        GROUP BY artist_id
        ORDER BY artist COLLATE NOCASE ASC
        """,
    )
    fun observeArtistRows(): Flow<List<ArtistRow>>


    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(songs: List<SongEntity>)

    @Query("DELETE FROM songs WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM songs WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("DELETE FROM songs WHERE source = 'LOCAL'")
    suspend fun deleteAllLocal()

    @Query("UPDATE songs SET is_favorite = :favorite WHERE id = :id")
    suspend fun setFavorite(id: Long, favorite: Boolean)

    @Query("UPDATE songs SET is_favorite = 1 WHERE id IN (:ids)")
    suspend fun addFavorites(ids: List<Long>)

    @Query("UPDATE songs SET is_favorite = 0 WHERE id IN (:ids)")
    suspend fun removeFavorites(ids: List<Long>)

    @Query("UPDATE songs SET last_played_at = :timestamp, play_count = play_count + 1 WHERE id = :id")
    suspend fun markPlayed(id: Long, timestamp: Long)

    @Query("UPDATE songs SET resume_position_ms = :positionMs, last_played_at = :timestamp WHERE id = :id")
    suspend fun setResumePosition(id: Long, positionMs: Long, timestamp: Long)

    @Query("UPDATE songs SET resume_position_ms = 0 WHERE id = :id")
    suspend fun clearResumePosition(id: Long)

    @Query("UPDATE songs SET resume_position_ms = 0")
    suspend fun clearAllResumePositions()

    /**
     * Applies a library rescan in one transaction: upsert what is still there and drop
     * rows whose files have disappeared.
     */
    @Transaction
    suspend fun applyScan(scanned: List<SongEntity>, removedIds: List<Long>) {
        if (removedIds.isNotEmpty()) deleteByIds(removedIds)
        if (scanned.isNotEmpty()) upsertAll(scanned)
    }
}
