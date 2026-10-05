package com.alexaplayer.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.alexaplayer.core.model.Album
import com.alexaplayer.core.model.PlaybackRequest
import com.alexaplayer.core.model.Playlist
import com.alexaplayer.core.model.Song
import com.alexaplayer.core.model.SortOrder
import com.alexaplayer.data.repository.LibraryRepository
import com.alexaplayer.data.repository.PlaylistRepository
import com.alexaplayer.di.AppContainer
import com.alexaplayer.playback.PlayerConnection
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Home shows what a person wants most often: pick up where they stopped, something new,
 * favourites, and the playlists they made.
 */
class HomeViewModel(
    private val library: LibraryRepository,
    playlists: PlaylistRepository,
    private val playerConnection: PlayerConnection,
) : ViewModel() {

    data class HomeUiState(
        val isLoading: Boolean = true,
        val songCount: Int = 0,
        val allSongs: List<Song> = emptyList(),
        val recentlyPlayed: List<Song> = emptyList(),
        val recentlyAdded: List<Song> = emptyList(),
        val favorites: List<Song> = emptyList(),
        val recentAlbums: List<Album> = emptyList(),
        val playlists: List<Playlist> = emptyList(),
    )

    private val songSections = combine(
        library.songs(SortOrder.TITLE),
        library.songs(SortOrder.RECENTLY_PLAYED),
        library.songs(SortOrder.RECENTLY_ADDED),
        library.albums,
        library.favorites,
    ) { allSongs, recentlyPlayed, recentlyAdded, albums, favorites ->
        SongSections(
            allSongs = allSongs,
            recentlyPlayed = recentlyPlayed.take(RECENT_LIMIT),
            recentlyAdded = recentlyAdded.take(RECENT_LIMIT),
            albums = albums.takeLast(ALBUM_LIMIT).reversed(),
            favorites = favorites.take(RECENT_LIMIT),
        )
    }

    private data class SongSections(
        val allSongs: List<Song>,
        val recentlyPlayed: List<Song>,
        val recentlyAdded: List<Song>,
        val albums: List<Album>,
        val favorites: List<Song>,
    )

    val uiState: StateFlow<HomeUiState> = combine(
        songSections,
        playlists.playlists,
        library.songCount,
    ) { sections, playlists, count ->
        HomeUiState(
            isLoading = false,
            songCount = count,
            allSongs = sections.allSongs,
            recentlyPlayed = sections.recentlyPlayed,
            recentlyAdded = sections.recentlyAdded,
            favorites = sections.favorites,
            recentAlbums = sections.albums,
            playlists = playlists.take(PLAYLIST_LIMIT),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), HomeUiState())

    fun play(songs: List<Song>, index: Int) {
        if (songs.isEmpty()) return
        playerConnection.play(PlaybackRequest(songs = songs, startIndex = index))
    }

    fun playAll(songs: List<Song>) = play(songs, 0)

    fun shuffleAll(songs: List<Song>) {
        if (songs.isEmpty()) return
        playerConnection.play(
            PlaybackRequest(songs = songs, startIndex = 0, shuffle = true),
        )
    }

    fun toggleFavorite(song: Song) {
        viewModelScope.launch {
            library.setFavorite(song.id, !song.isFavorite)
        }
    }

    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L
        private const val RECENT_LIMIT = 12
        private const val ALBUM_LIMIT = 12
        private const val PLAYLIST_LIMIT = 6

        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val library = container.libraryRepository
                HomeViewModel(
                    library = library,
                    playlists = container.playlistRepository,
                    playerConnection = container.playerConnection,
                )
            }
        }
    }
}
