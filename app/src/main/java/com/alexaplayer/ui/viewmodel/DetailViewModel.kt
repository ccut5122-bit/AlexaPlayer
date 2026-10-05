package com.alexaplayer.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.alexaplayer.core.model.Album
import com.alexaplayer.core.model.LibraryFolder
import com.alexaplayer.core.model.PlaybackRequest
import com.alexaplayer.core.model.Song
import com.alexaplayer.data.repository.LibraryRepository
import com.alexaplayer.di.AppContainer
import com.alexaplayer.playback.PlayerConnection
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** What a detail screen is showing. One screen, three kinds of collection. */
sealed interface DetailTarget {
    data class AlbumTarget(val albumId: Long) : DetailTarget
    data class ArtistTarget(val artistId: Long) : DetailTarget
    data class FolderTarget(val path: String, val name: String) : DetailTarget
}

@OptIn(ExperimentalCoroutinesApi::class)
class DetailViewModel(
    private val library: LibraryRepository,
    private val playerConnection: PlayerConnection,
    private val target: DetailTarget,
) : ViewModel() {

    data class DetailUiState(
        val isLoading: Boolean = true,
        val title: String = "",
        val subtitle: String = "",
        val artworkUri: String? = null,
        val seed: Long = 0L,
        val songs: List<Song> = emptyList(),
        val albums: List<Album> = emptyList(),
        val totalDurationMs: Long = 0L,
        val currentSongId: Long? = null,
    ) {
        val songCount: Int get() = songs.size
    }

    private val songs = when (target) {
        is DetailTarget.AlbumTarget -> library.songsByAlbum(target.albumId)
        is DetailTarget.ArtistTarget -> library.songsByArtist(target.artistId)
        is DetailTarget.FolderTarget -> library.songsByFolder(
            LibraryFolder(path = target.path, name = target.name, songCount = 0),
        )
    }

    private val header = when (target) {
        is DetailTarget.AlbumTarget -> library.albums.map { albums ->
            val album = albums.firstOrNull { it.id == target.albumId }
            DetailHeader(
                title = album?.title.orEmpty(),
                subtitle = album?.artist.orEmpty(),
                artworkUri = album?.artworkUri,
                seed = target.albumId,
            )
        }

        is DetailTarget.ArtistTarget -> library.artists.map { artists ->
            val artist = artists.firstOrNull { it.id == target.artistId }
            DetailHeader(
                title = artist?.name.orEmpty(),
                subtitle = "",
                artworkUri = artist?.artworkUri,
                seed = target.artistId,
            )
        }

        is DetailTarget.FolderTarget -> flowOf(
            DetailHeader(
                title = target.name,
                subtitle = target.path,
                artworkUri = null,
                seed = target.path.hashCode().toLong(),
            ),
        )
    }

    private val albums = library.albums.flatMapLatest { list ->
        flowOf(
            when (target) {
                is DetailTarget.ArtistTarget -> list.filter { it.artistId == target.artistId }
                else -> emptyList()
            },
        )
    }

    private data class DetailHeader(
        val title: String,
        val subtitle: String,
        val artworkUri: String?,
        val seed: Long,
    )

    val uiState: StateFlow<DetailUiState> = combine(
        header,
        songs,
        albums,
        playerConnection.state,
    ) { header, songs, albums, playback ->
        DetailUiState(
            isLoading = false,
            title = header.title,
            subtitle = header.subtitle,
            artworkUri = header.artworkUri,
            seed = header.seed,
            songs = songs,
            albums = albums,
            totalDurationMs = songs.sumOf { it.durationMs },
            currentSongId = playback.currentSongId,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), DetailUiState())

    fun play(index: Int) {
        val songs = uiState.value.songs
        if (index !in songs.indices) return
        playerConnection.play(PlaybackRequest(songs = songs, startIndex = index))
    }

    fun playAll() = play(0)

    fun shuffle() {
        val songs = uiState.value.songs
        if (songs.isEmpty()) return
        playerConnection.play(PlaybackRequest(songs = songs, startIndex = 0, shuffle = true))
    }

    fun toggleFavorite(song: Song) {
        viewModelScope.launch {
            library.setFavorite(song.id, !song.isFavorite)
        }
    }

    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L

        fun factory(container: AppContainer, target: DetailTarget): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    DetailViewModel(
                        library = container.libraryRepository,
                        playerConnection = container.playerConnection,
                        target = target,
                    )
                }
            }
    }
}
