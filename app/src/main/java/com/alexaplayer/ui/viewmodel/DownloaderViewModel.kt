package com.alexaplayer.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.alexaplayer.data.youtube.ActiveDownload
import com.alexaplayer.data.youtube.DownloadProgress
import com.alexaplayer.data.youtube.FetchedMedia
import com.alexaplayer.data.youtube.YoutubeBridge
import com.alexaplayer.di.AppContainer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Backs the Downloader tab: paste a YouTube or Instagram link, tap Fetch to preview it,
 * play it full-screen, or download the video/audio. Extraction (yt-dlp) runs on an IO
 * thread inside the Python bridge while this polls its byte-level progress.
 */
class DownloaderViewModel(
    private val bridge: YoutubeBridge,
) : ViewModel() {

    data class DownloaderUiState(
        val link: String = "",
        val quality: Int = 1080,
        val fetching: Boolean = false,
        val media: FetchedMedia? = null,
        val resolvingPlay: Boolean = false,
        val downloads: List<ActiveDownload> = emptyList(),
        val message: String? = null,
    )

    private val _uiState = MutableStateFlow(DownloaderUiState())
    val uiState: StateFlow<DownloaderUiState> = _uiState.asStateFlow()

    fun onLinkChange(value: String) {
        _uiState.value = _uiState.value.copy(link = value, message = null)
    }

    fun setQuality(height: Int) {
        _uiState.value = _uiState.value.copy(quality = height)
    }

    /** Reads title/thumbnail/duration + available heights for the pasted link. */
    fun fetch() {
        val link = _uiState.value.link.trim()
        if (link.isBlank()) {
            _uiState.value = _uiState.value.copy(message = "Paste a link first")
            return
        }
        if (_uiState.value.fetching) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(fetching = true, message = null, media = null)
            try {
                val media = bridge.fetch(link)
                _uiState.value = _uiState.value.copy(fetching = false, media = media)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                _uiState.value = _uiState.value.copy(
                    fetching = false,
                    message = error.message ?: "Could not read that link",
                )
            }
        }
    }

    /** Resolves the fetched link into a playable stream and hands it to the full player. */
    fun play(onReady: (videoUrl: String, audioUrl: String?, title: String) -> Unit) {
        val media = _uiState.value.media ?: return
        if (!media.isVideo || _uiState.value.resolvingPlay) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(resolvingPlay = true, message = null)
            try {
                val dash = bridge.resolveUrl(media.url, _uiState.value.quality)
                onReady(dash.videoUrl, dash.audioUrl, dash.title.ifBlank { media.title })
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                _uiState.value = _uiState.value.copy(message = error.message ?: "Could not play that link")
            } finally {
                _uiState.value = _uiState.value.copy(resolvingPlay = false)
            }
        }
    }

    /** kind = "video" or "audio". Works for both YouTube and Instagram links. */
    fun download(kind: String) {
        val media = _uiState.value.media ?: return
        val token = "dl-${System.currentTimeMillis()}"
        _uiState.value = _uiState.value.copy(
            downloads = _uiState.value.downloads +
                ActiveDownload(token = token, kind = kind, title = media.title, percent = 0, done = false),
            message = null,
        )
        viewModelScope.launch {
            val job = async(Dispatchers.IO) {
                bridge.downloadUrl(media.url, kind, token, _uiState.value.quality)
            }
            try {
                while (job.isActive) {
                    delay(600)
                    val snapshot = bridge.downloadProgress(token)
                    when (snapshot.status) {
                        "finished", "error" -> break
                        else -> updateProgress(token, progressPercent(snapshot), snapshot.path, failed = false)
                    }
                }
                val result = try {
                    job.await()
                } catch (error: Exception) {
                    markFailed(token, error.message ?: "Download failed")
                    return@launch
                }
                updateProgress(token, 100, result.path, failed = false, done = true)
                _uiState.value = _uiState.value.copy(message = "Saved to Download/AlexaPlayer")
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                markFailed(token, error.message ?: "Download failed")
            }
        }
    }

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }

    private fun progressPercent(progress: DownloadProgress): Int {
        if (progress.total <= 0L) return 99
        return ((progress.downloaded * 100) / progress.total).toInt().coerceIn(0, 99)
    }

    private fun updateProgress(token: String, percent: Int, path: String?, failed: Boolean, done: Boolean? = null) {
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

    private fun markFailed(token: String, message: String) {
        _uiState.value = _uiState.value.copy(
            message = message,
            downloads = _uiState.value.downloads.map {
                if (it.token == token) it.copy(failed = true, done = false) else it
            },
        )
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                DownloaderViewModel(bridge = container.youtubeBridge)
            }
        }
    }
}
