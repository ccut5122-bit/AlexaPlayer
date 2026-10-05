package com.alexaplayer.data.repository

import com.alexaplayer.core.model.Album
import com.alexaplayer.core.model.Artist
import com.alexaplayer.core.model.Playlist
import com.alexaplayer.core.model.Song
import com.alexaplayer.core.util.DispatcherProvider
import com.alexaplayer.data.local.dao.PlaylistDao
import com.alexaplayer.data.local.dao.RecentSearchDao
import com.alexaplayer.data.local.dao.SongDao
import com.alexaplayer.data.local.entity.RecentSearchEntity
import com.alexaplayer.data.local.toAlbums
import com.alexaplayer.data.local.toArtists
import com.alexaplayer.data.local.toDomain
import com.alexaplayer.data.local.toPlaylist
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

data class SearchResults(
    val songs: List<Song> = emptyList(),
    val albums: List<Album> = emptyList(),
    val artists: List<Artist> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
) {
    val isEmpty: Boolean
        get() = songs.isEmpty() && albums.isEmpty() && artists.isEmpty() && playlists.isEmpty()
}

/**
 * One query per entity type keeps search fast: SQLite does the matching, and albums and
 * artists are folded from the (already limited) song matches instead of re-scanning.
 */
class SearchRepository(
    private val songDao: SongDao,
    private val playlistDao: PlaylistDao,
    private val recentSearchDao: RecentSearchDao,
    private val dispatchers: DispatcherProvider,
) {

    val recentSearches: Flow<List<String>> = recentSearchDao.observeRecent(MAX_RECENT)
        .map { rows -> rows.map { it.query } }
        .flowOn(dispatchers.default)

    fun search(query: String): Flow<SearchResults> {
        val pattern = likePattern(query)
        val songEntities = songDao.searchSongs(pattern, MAX_RESULTS)
        val playlistRows = playlistDao.search(pattern)

        return combine(songEntities, playlistRows) { entities, playlists ->
            SearchResults(
                songs = entities.map { it.toDomain() },
                albums = entities.toAlbums(),
                artists = entities.toArtists(),
                playlists = playlists.map { it.toPlaylist() },
            )
        }.flowOn(dispatchers.default)
    }

    suspend fun record(query: String) = withContext(dispatchers.io) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return@withContext
        recentSearchDao.upsert(RecentSearchEntity(query = trimmed, searchedAt = System.currentTimeMillis()))
        recentSearchDao.trim(MAX_RECENT)
    }

    suspend fun forget(query: String) = withContext(dispatchers.io) {
        recentSearchDao.delete(query.trim())
    }

    suspend fun clearRecent() = withContext(dispatchers.io) {
        recentSearchDao.clear()
    }

    private companion object {
        const val MAX_RECENT = 8
        const val MAX_RESULTS = 60
    }
}

/** Escapes LIKE wildcards so a search for "100%" does not match everything. */
internal fun likePattern(query: String): String = buildString {
    append('%')
    query.trim().forEach { char ->
        when (char) {
            '\\', '%', '_' -> append('\\').append(char)
            else -> append(char)
        }
    }
    append('%')
}
