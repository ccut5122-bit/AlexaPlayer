package com.alexaplayer.data.repository

import com.alexaplayer.core.model.FailureReason
import com.alexaplayer.core.model.Outcome
import com.alexaplayer.core.model.Playlist
import com.alexaplayer.core.model.PlaylistWithSongs
import com.alexaplayer.core.util.DispatcherProvider
import com.alexaplayer.data.local.dao.PlaylistDao
import com.alexaplayer.data.local.entity.PlaylistEntity
import com.alexaplayer.data.local.entity.PlaylistSongEntity
import com.alexaplayer.data.local.toPlaylist
import com.alexaplayer.data.local.toSong
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

/**
 * Playlist lifecycle. Ordering is stored explicitly rather than derived, so a reorder
 * survives a restart and survives the underlying songs being rescanned.
 */
class PlaylistRepository(
    private val playlistDao: PlaylistDao,
    private val dispatchers: DispatcherProvider,
) {

    val playlists: Flow<List<Playlist>> =
        combine(playlistDao.observeAll(), playlistDao.observeArtwork()) { rows, artwork ->
            val byPlaylist = artwork.groupBy { it.playlistId }
            rows.map { row ->
                val covers = byPlaylist[row.id].orEmpty()
                    .map { it.artworkUri }
                    .distinct()
                    .take(MAX_COVERS)
                row.toPlaylist(covers)
            }
        }
            .distinctUntilChanged()
            .flowOn(dispatchers.default)

    fun playlist(id: Long): Flow<PlaylistWithSongs?> = combine(
        playlistDao.observeById(id),
        playlistDao.observeSongs(id),
    ) { summary, songs ->
        summary?.let { row ->
            PlaylistWithSongs(
                playlist = row.toPlaylist(
                    songs.mapNotNull { song -> song.artworkUri }.distinct().take(MAX_COVERS),
                ),
                songs = songs.map { it.toSong() },
            )
        }
    }.flowOn(dispatchers.default)

    suspend fun create(name: String): Outcome<Long> = withContext(dispatchers.io) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return@withContext Outcome.Failure(FailureReason.NAME_BLANK)
        if (playlistDao.findByName(trimmed) != null) {
            return@withContext Outcome.Failure(FailureReason.NAME_TAKEN)
        }
        runCatching {
            val now = System.currentTimeMillis()
            playlistDao.insert(PlaylistEntity(name = trimmed, createdAt = now, updatedAt = now))
        }.fold(
            onSuccess = { Outcome.Success(it) },
            onFailure = { Outcome.Failure(FailureReason.STORAGE) },
        )
    }

    suspend fun rename(id: Long, name: String): Outcome<Unit> = withContext(dispatchers.io) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return@withContext Outcome.Failure(FailureReason.NAME_BLANK)
        val clash = playlistDao.findByName(trimmed)
        if (clash != null && playlistDao.getById(id)?.name != clash) {
            return@withContext Outcome.Failure(FailureReason.NAME_TAKEN)
        }
        runCatching {
            playlistDao.rename(id, trimmed, System.currentTimeMillis())
        }.fold(
            onSuccess = { Outcome.Success(Unit) },
            onFailure = { Outcome.Failure(FailureReason.STORAGE) },
        )
    }

    suspend fun delete(id: Long) = withContext(dispatchers.io) {
        playlistDao.deleteById(id)
        Unit
    }

    /** @return how many songs were actually added; duplicates are silently skipped. */
    suspend fun addSongs(playlistId: Long, songIds: List<Long>): Int = withContext(dispatchers.io) {
        // Only rows that exist can be linked; in-memory YouTube tracks are not in the table.
        val persisted = songIds.filter { it > 0 }
        if (persisted.isEmpty()) return@withContext 0
        val now = System.currentTimeMillis()
        var nextPosition = playlistDao.maxPosition(playlistId) + 1
        val rows = persisted.map { songId ->
            PlaylistSongEntity(
                playlistId = playlistId,
                songId = songId,
                position = nextPosition++,
                addedAt = now,
            )
        }
        playlistDao.insertSongs(rows)
        playlistDao.touchAfterEdit(playlistId, now)
        rows.size
    }

    suspend fun removeSong(playlistId: Long, songId: Long) = withContext(dispatchers.io) {
        playlistDao.removeSong(playlistId, songId)
        compactPositions(playlistId)
        Unit
    }

    suspend fun clear(playlistId: Long) = withContext(dispatchers.io) {
        playlistDao.clear(playlistId)
        playlistDao.touchAfterEdit(playlistId, System.currentTimeMillis())
        Unit
    }

    /** Persists the result of a drag-and-drop reorder. */
    suspend fun applyOrder(playlistId: Long, orderedSongIds: List<Long>) = withContext(dispatchers.io) {
        playlistDao.applyOrder(playlistId, orderedSongIds, System.currentTimeMillis())
    }

    private suspend fun compactPositions(playlistId: Long) {
        playlistDao.songIds(playlistId).forEachIndexed { index, songId ->
            playlistDao.updatePosition(playlistId, songId, index)
        }
        playlistDao.touchAfterEdit(playlistId, System.currentTimeMillis())
    }

    private companion object {
        const val MAX_COVERS = 4
    }
}
