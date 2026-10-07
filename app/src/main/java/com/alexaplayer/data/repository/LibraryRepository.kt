package com.alexaplayer.data.repository

import com.alexaplayer.core.model.Album
import com.alexaplayer.core.model.Artist
import com.alexaplayer.core.model.FailureReason
import com.alexaplayer.core.model.LibraryFolder
import com.alexaplayer.core.model.Outcome
import com.alexaplayer.core.model.Source
import com.alexaplayer.core.model.SortOrder
import com.alexaplayer.core.model.Song
import com.alexaplayer.core.util.DispatcherProvider
import com.alexaplayer.data.local.dao.SongDao
import com.alexaplayer.data.local.entity.SongEntity
import com.alexaplayer.data.local.toAlbums
import com.alexaplayer.data.local.toArtists
import com.alexaplayer.data.local.toDomain
import com.alexaplayer.data.local.toFolders
import com.alexaplayer.data.media.MediaStoreScanner
import com.alexaplayer.data.prefs.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Owns everything read from and written to the local library: the MediaStore scan,
 * the Room mirror of it, favourites and per-song resume positions.
 */
class LibraryRepository(
    private val songDao: SongDao,
    private val scanner: MediaStoreScanner,
    private val settingsRepository: SettingsRepository,
    private val dispatchers: DispatcherProvider,
) {

    fun songs(sortOrder: SortOrder): Flow<List<Song>> {
        val source = when (sortOrder) {
            SortOrder.RECENTLY_ADDED -> songDao.observeByRecentlyAdded()
            SortOrder.RECENTLY_PLAYED -> songDao.observeByRecentlyPlayed()
            SortOrder.TITLE -> songDao.observeByTitle()
            SortOrder.ARTIST -> songDao.observeByArtist()
            SortOrder.DURATION -> songDao.observeByDuration()
        }
        return source.map { rows -> rows.map { it.toDomain() } }.flowOn(dispatchers.default)
    }

    val albums: Flow<List<Album>> = songDao.observeAlbumRows()
        .map { rows ->
            rows.map { row ->
                Album(
                    id = row.id,
                    title = row.title,
                    artist = row.artist,
                    artistId = row.artistId,
                    artworkUri = MediaStoreScanner.albumArtUri(row.id),
                    year = row.year,
                    songCount = row.songCount,
                    totalDurationMs = row.totalDurationMs,
                )
            }
        }
        .flowOn(dispatchers.default)

    val artists: Flow<List<Artist>> = songDao.observeArtistRows()
        .map { rows ->
            rows.map { row ->
                Artist(
                    id = row.id,
                    name = row.name,
                    artworkUri = null,
                    albumCount = row.albumCount,
                    songCount = row.songCount,
                )
            }
        }
        .flowOn(dispatchers.default)

    val folders: Flow<List<LibraryFolder>> = songDao.observeByTitle()
        .map { rows -> rows.toFolders() }
        .flowOn(dispatchers.default)

    val favorites: Flow<List<Song>> = songDao.observeFavorites()
        .map { rows -> rows.map { it.toDomain() } }
        .flowOn(dispatchers.default)

    val streams: Flow<List<Song>> = songDao.observeStreams()
        .map { rows -> rows.map { it.toDomain() } }
        .flowOn(dispatchers.default)

    val songCount: Flow<Int> = songDao.observeCount()

    fun songsByAlbum(albumId: Long): Flow<List<Song>> = songDao.observeByAlbum(albumId)
        .map { rows -> rows.map { it.toDomain() } }
        .flowOn(dispatchers.default)

    fun songsByArtist(artistId: Long): Flow<List<Song>> = songDao.observeByArtist(artistId)
        .map { rows -> rows.map { it.toDomain() } }
        .flowOn(dispatchers.default)

    fun songsByFolder(folder: LibraryFolder): Flow<List<Song>> = songDao.observeByTitle()
        .map { rows -> rows.map { it.toDomain() }.filter { it.uri.startsWith("${folder.path}/") } }
        .flowOn(dispatchers.default)

    fun song(id: Long): Flow<Song?> = songDao.observeById(id)
        .map { it?.toDomain() }
        .flowOn(dispatchers.default)

    suspend fun songsByIds(ids: List<Long>): List<Song> = withContext(dispatchers.default) {
        songDao.getByIds(ids).map { it.toDomain() }
    }

    /**
     * Re-reads MediaStore and reconciles it with the database.
     *
     * Favourite state, play counts and resume positions are carried across so a rescan
     * never costs the user their history, and files that vanished are dropped.
     *
     * @return number of local songs available after the scan.
     */
    suspend fun rescan(): Int = withContext(dispatchers.io) {
        val excluded = settingsRepository.settings.first().excludedFolders
        val scanned = scanner.scan(excluded)
        val existing = songDao.getAll().associateBy { it.id }
        val scannedIds = scanned.asSequence().map { it.id }.toHashSet()

        val merged = scanned.map { fresh ->
            val previous = existing[fresh.id]
            if (previous == null) {
                fresh
            } else {
                fresh.copy(
                    isFavorite = previous.isFavorite,
                    lastPlayedAt = previous.lastPlayedAt,
                    playCount = previous.playCount,
                    resumePositionMs = previous.resumePositionMs,
                )
            }
        }

        val removed = existing.values
            .filter { it.source == Source.LOCAL.name && it.id !in scannedIds }
            .map { it.id }

        songDao.applyScan(merged, removed)
        merged.size
    }

    suspend fun setFavorite(songId: Long, favorite: Boolean) = withContext(dispatchers.io) {
        // YouTube tracks live in memory only; their negative ids point at no row, and the
        // favorites table would reject them on its foreign key.
        if (songId < 0) return@withContext
        songDao.setFavorite(songId, favorite)
    }

    suspend fun setFavorites(songIds: List<Long>, favorite: Boolean) = withContext(dispatchers.io) {
        val persisted = songIds.filter { it > 0 }
        if (persisted.isEmpty()) return@withContext
        if (favorite) songDao.addFavorites(persisted) else songDao.removeFavorites(persisted)
    }

    suspend fun markPlayed(songId: Long, timestamp: Long = System.currentTimeMillis()) =
        withContext(dispatchers.io) { songDao.markPlayed(songId, timestamp) }

    suspend fun savePosition(songId: Long, positionMs: Long) = withContext(dispatchers.io) {
        if (positionMs > 0) {
            songDao.setResumePosition(songId, positionMs, System.currentTimeMillis())
        } else {
            songDao.clearResumePosition(songId)
        }
    }

    /**
     * Registers a direct audio URL the user has the right to play.
     *
     * Ids for streams are negative so they can never collide with MediaStore ids.
     */
    suspend fun addStream(url: String, title: String): Outcome<Long> = withContext(dispatchers.io) {
        val normalized = url.trim()
        if (!normalized.startsWith("http://") && !normalized.startsWith("https://")) {
            return@withContext Outcome.Failure(FailureReason.INVALID_URL)
        }
        if (songDao.findByUri(normalized) != null) {
            return@withContext Outcome.Failure(FailureReason.NAME_TAKEN)
        }

        val derivedTitle = title.trim().ifBlank {
            normalized.substringBeforeLast('/').substringAfterLast('/')
                .substringBefore('?')
                .ifBlank { normalized }
        }

        val entity = SongEntity(
            id = streamId(normalized),
            title = derivedTitle,
            artist = "",
            album = "",
            albumId = 0L,
            artistId = 0L,
            durationMs = 0L,
            trackNumber = 0,
            year = 0,
            uri = normalized,
            artworkUri = null,
            dateAdded = System.currentTimeMillis(),
            dateModified = System.currentTimeMillis(),
            sizeBytes = 0L,
            mimeType = null,
            source = Source.STREAM.name,
        )

        try {
            songDao.upsertAll(listOf(entity))
            Outcome.Success(entity.id)
        } catch (error: Exception) {
            Outcome.Failure(FailureReason.STORAGE)
        }
    }

    suspend fun deleteStream(songId: Long) = withContext(dispatchers.io) {
        songDao.deleteById(songId)
    }

    companion object {
        /** Stable, collision free id derived from the URL. */
        fun streamId(url: String): Long = -((url.hashCode().toLong() and 0x7FFFFFFF) + 1L)
    }
}
