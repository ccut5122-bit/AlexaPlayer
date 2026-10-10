package com.alexaplayer.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.alexaplayer.data.youtube.ActiveDownload
import com.alexaplayer.data.youtube.DownloadProgress
import com.alexaplayer.data.youtube.SearchOutcome
import com.alexaplayer.data.youtube.YoutubeBridge
import com.alexaplayer.data.youtube.YoutubeTrack
import com.alexaplayer.di.AppContainer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** One horizontal carousel: a title plus the tracks behind it. */
data class YoutubeSection(
    val title: String,
    val query: String,
    val results: List<YoutubeTrack>,
    val loading: Boolean,
    val error: String? = null,
)

data class YoutubeUiState(
    val query: String = "",
    /** Always-available home content, built from a handful of curated search terms. */
    val sections: List<YoutubeSection> = emptyList(),
    val loadingHome: Boolean = false,
    val homeError: String? = null,
    /** Results of an explicit search; lives side by side with home so back never clears them. */
    val results: List<YoutubeTrack> = emptyList(),
    val searching: Boolean = false,
    val hasSearched: Boolean = false,
    /** The row whose stream is being extracted right now. */
    val resolvingId: String? = null,
    val error: String? = null,
    /** Live + finished downloads, with percent so rows can show real progress. */
    val downloads: List<ActiveDownload> = emptyList(),
    /** Transient "Saved to ..." / "Download failed" feedback for the snackbar. */
    val message: String? = null,
) {
    val showingResults: Boolean get() = hasSearched || searching
}

private data class HomeFeed(
    val title: String,
    val query: String,
)

class YoutubeViewModel(
    private val bridge: YoutubeBridge,
    private val settingsRepository: com.alexaplayer.data.prefs.SettingsRepository,
    private val playerConnection: com.alexaplayer.playback.PlayerConnection,
) : ViewModel() {

    private var autoplayJob: Job? = null

    private val _uiState = MutableStateFlow(YoutubeUiState())
    val uiState: StateFlow<YoutubeUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null
    private var homeLoaded = false

    init {
        loadHome()
    }

    /**
     * On open, the tab is a mini YouTube Music home: curated genres as swipeable rows. The
     * calls run in parallel and each section publishes as soon as it is ready.
     */
    fun loadHome() {
        if (homeLoaded) return
        homeLoaded = true
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loadingHome = true, homeError = null)
            val feeds = listOf(
                HomeFeed("Phonk", "phonk"),
                HomeFeed("Hindi Hits", "top hindi songs"),
                HomeFeed("English Pop", "english pop songs"),
                HomeFeed("Arijit Singh", "arijit singh songs"),
            )
            val jobs = feeds.map { feed ->
                async {
                    try {
                        feed to bridge.search(feed.query, limit = 12)
                    } catch (cancellation: CancellationException) {
                        throw cancellation
                    } catch (error: Exception) {
                        feed to SearchOutcome(emptyList())
                    }
                }
            }
            jobs.forEach { deferred ->
                val (feed, outcome) = deferred.await()
                _uiState.value = _uiState.value.copy(
                    sections = _uiState.value.sections + YoutubeSection(
                        title = feed.title,
                        query = feed.query,
                        results = outcome.results,
                        loading = false,
                        error = if (outcome.results.isEmpty()) {
                            outcome.note.takeIf { it.isNotBlank() } ?: "Could not load ${feed.title}"
                        } else null,
                    ),
                )
            }
            _uiState.value = _uiState.value.copy(loadingHome = false)
        }
    }

    fun onQueryChange(value: String) {
        _uiState.value = _uiState.value.copy(query = value, error = null)
    }

    fun clearQuery() {
        searchJob?.cancel()
        _uiState.value = _uiState.value.copy(
            query = "",
            results = emptyList(),
            hasSearched = false,
            searching = false,
            error = null,
        )
    }

    /** When a carousel says "See all", the genre becomes the query and it searches fully. */
    fun openGenre(query: String) {
        onQueryChange(query)
        submit()
    }

    /**
     * Searches only on submit. Per-keystroke searching is what gets a client throttled by
     * YouTube, and the round trip costs seconds anyway.
     */
    fun submit() {
        val query = _uiState.value.query.trim()
        if (query.isEmpty()) return
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(searching = true, hasSearched = true, error = null)
            try {
                val outcome = bridge.search(query)
                _uiState.value = _uiState.value.copy(
                    results = outcome.results,
                    searching = false,
                    error = if (outcome.results.isEmpty()) outcome.note.takeUnless { it.isBlank() } else null,
                )
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                _uiState.value = _uiState.value.copy(
                    results = emptyList(),
                    searching = false,
                    hasSearched = true,
                    error = error.message ?: "Search failed",
                )
            }
        }
    }

    fun retry() {
        if (_uiState.value.showingResults) {
            submit()
        } else {
            homeLoaded = false
            _uiState.value = _uiState.value.copy(sections = emptyList(), homeError = null)
            loadHome()
        }
    }

    /**
     * Resolves the audio URL and hands the finished [com.alexaplayer.core.model.Song] to
     * [onReady]. Extraction takes a couple of seconds, so the row shows a spinner meanwhile.
     */
    fun play(track: YoutubeTrack, onReady: (com.alexaplayer.core.model.Song) -> Unit) {
        if (_uiState.value.resolvingId != null) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(resolvingId = track.videoId, error = null)
            try {
                onReady(bridge.toSong(bridge.resolve(track.videoId, videoHeight())))
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                _uiState.value = _uiState.value.copy(
                    resolvingId = null,
                    error = error.message ?: "Could not start playback",
                )
                return@launch
            }
            _uiState.value = _uiState.value.copy(resolvingId = null)
            startAutoplay(track)
        }
    }

    /**
     * YouTube-style autoplay: after the tapped song starts, quietly resolve a few similar
     * songs and append them, so Next and continuous playback work right away.
     */
    private fun startAutoplay(track: YoutubeTrack) {
        autoplayJob?.cancel()
        autoplayJob = viewModelScope.launch {
            try {
                val related = bridge.search("${track.title} ${track.channel}".trim(), limit = 8)
                val height = videoHeight()
                var queued = 0
                for (item in related.results) {
                    if (queued >= AUTOPLAY_LIMIT) break
                    if (item.videoId == track.videoId) continue
                    val song = runCatching { bridge.toSong(bridge.resolve(item.videoId, height)) }
                        .getOrNull() ?: continue
                    playerConnection.addToQueue(listOf(song))
                    queued++
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                // Autoplay is best-effort; a failure here must never surface to the user.
            }
        }
    }

    /** Same flow but for the muxed video stream, funnelled into the full-screen player. */
    fun playVideo(track: YoutubeTrack, onReady: (url: String, title: String) -> Unit) {
        if (_uiState.value.resolvingId != null) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(resolvingId = track.videoId, error = null)
            try {
                onReady(bridge.resolveVideo(track.videoId, videoHeight()).url, track.title)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                _uiState.value = _uiState.value.copy(resolvingId = null, error = error.message ?: "Video failed to load")
                return@launch
            }
            _uiState.value = _uiState.value.copy(resolvingId = null)
        }
    }

    /**
     * Queue a download (audio or video) and pump its progress. Python does the work on a
     * separate IO thread while this coroutine polls the bridge until it reports finished.
     */
    fun startDownload(track: YoutubeTrack, kind: String) {
        val existing = _uiState.value.downloads.firstOrNull { it.token.startsWith("${track.videoId}-") }
        if (existing != null && !existing.failed) return
        val token = "${track.videoId}-${kind}-${System.currentTimeMillis()}"
        _uiState.value = _uiState.value.copy(
            downloads = _uiState.value.downloads.filterNot { it.token.startsWith("${track.videoId}-") } +
                ActiveDownload(token = token, kind = kind, title = track.title, percent = 0, done = false),
            message = null,
        )
        viewModelScope.launch {
            val downloadAsync = async(Dispatchers.IO) {
                bridge.download(track.videoId, kind, token, videoHeight())
            }
            try {
                while (downloadAsync.isActive) {
                    delay(600)
                    val snapshot = bridge.downloadProgress(token)
                    when (snapshot.status) {
                        "finished", "error" -> break
                        else -> updateDownloadProgress(token, progressPercent(snapshot), snapshot.path, failed = false)
                    }
                }
                val result = try {
                    downloadAsync.await()
                } catch (error: Exception) {
                    markDownloadFailed(token, error.message ?: "Download failed")
                    return@launch
                }
                updateDownloadProgress(token, 100, result.path, failed = false, done = true)
                _uiState.value = _uiState.value.copy(message = "Saved to Download/AlexaPlayer")
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                markDownloadFailed(token, error.message ?: "Download failed")
            }
        }
    }

    fun activeDownloadFor(track: YoutubeTrack): ActiveDownload? =
        _uiState.value.downloads.firstOrNull { it.token.startsWith("${track.videoId}-") }

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }

    /** Resolution cap (480/720/1080/2160) chosen in Settings, used for streams + downloads. */
    private suspend fun videoHeight(): Int = settingsRepository.settings.first().videoQuality

    private fun progressPercent(progress: DownloadProgress): Int {
        if (progress.total <= 0L) return 99
        return ((progress.downloaded * 100) / progress.total).toInt().coerceIn(0, 99)
    }

    private fun updateDownloadProgress(token: String, percent: Int, path: String?, failed: Boolean, done: Boolean? = null) {
        _uiState.value = _uiState.value.copy(
            downloads = _uiState.value.downloads.map {
                if (it.token == token) {
                    it.copy(
                        percent = percent,
                        path = path ?: it.path,
                        failed = failed,
                        done = done ?: (percent == 100),
                    )
                } else it
            },
        )
    }

    private fun markDownloadFailed(token: String, message: String) {
        _uiState.value = _uiState.value.copy(
            message = message,
            downloads = _uiState.value.downloads.map {
                if (it.token == token) it.copy(failed = true, done = false, percent = it.percent) else it
            },
        )
    }

    companion object {
        private const val AUTOPLAY_LIMIT = 5

        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                YoutubeViewModel(
                    bridge = container.youtubeBridge,
                    settingsRepository = container.settingsRepository,
                    playerConnection = container.playerConnection,
                )
            }
        }
    }
}