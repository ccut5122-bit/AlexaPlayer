package com.alexaplayer.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.alexaplayer.data.youtube.ActiveDownload
import com.alexaplayer.data.youtube.YoutubeBridge
import com.alexaplayer.di.AppContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Backs the Downloader tab: paste a YouTube or Instagram link, pick a quality, download.
 * Python (yt-dlp) does the fetching on an IO thread while this polls its progress, exactly
 * like the in-list download on the YouTube tab.
 */
class DownloaderViewModel(
    private val bridge: com.alexaplayer.data.youtube.YoutubeBridge,
) : ViewModel() {

    data class DownloaderUiState(
        val youtubeUrl: String = "",
        val instagramUrl: String = "",
        val quality: Int = 1080,
        val downloads: List<ActiveDownload> = emptyList(),
        val message: String? = null,
    )

    private val _uiState = MutableStateFlow(DownloaderUiState())
    val uiState: StateFlow<DownloaderUiState> = _uiState.asStateFlow()

    fun onYoutubeUrlChange(value: String) {
        _uiState.value = _uiState.value.copy(youtubeUrl = value, message = null)
    }

    fun onInstagramUrlChange(value: String) {
        _uiState.value = _uiState.value.copy(instagramUrl = value, message = null)
    }

    fun setQuality(height: Int) {
        _uiState.value = _uiState.value.copy(quality = height)
    }

    /** kind = "video" or "audio". */
    fun downloadYoutube(kind: String) {
        val url = _uiState.value.youtubeUrl.trim()
        if (url.isBlank()) {
            _uiState.value = _uiState.value.copy(message = "Paste a YouTube link first")
            return
        }
        startDownload(url, kind, if (kind == "audio") "YouTube audio" else "YouTube video")
    }

    /** Instagram: reels and videos become mp4, single-photo posts become jpg. */
    fun downloadInstagram() {
        val url = _uiState.value.instagramUrl.trim()
        if (url.isBlank()) {
            _uiState.value = _uiState.value.copy(message = "Paste an Instagram link first")
            return
        }
        startDownload(url, "video", "Instagram")
    }

    private fun startDownload(url: String, kind: String, label: String) {
        val token = "dl-${System.currentTimeMillis()}"
        _uiState.value = _uiState.value.copy(
            downloads = listOf(
                ActiveDownload(token = token, kind = kind, title = label, percent = 0, done = false),
            ) + _uiState.value.downloads,
            message = null,
        )
        viewModelScope.launch {
            val job = async(Dispatchers.IO) {
                bridge.downloadUrl(url, kind, token, _uiState.value.quality)
            }
            try {
                while (job.isActive) {
                    delay(600)
                    val snapshot = bridge.downloadProgress(token)
                    if (snapshot.status == "finished" || snapshot.status == "error") break
                    update(token, progressPercent(snapshot), snapshot.path, failed = false)
                }
                val result = job.await()
                update(token, 100, result.path, failed = false, done = true)
                _uiState.value = _uiState.value.copy(message = "Saved to Download/AlexaPlayer")
            } catch (error: Exception) {
                _uiState.value = _uiState.value.copy(
                    message = error.message ?: "Download failed",
                )
                update(token, 0, null, failed = true)
            }
        }
    }

    private fun progressPercent(progress: com.alexaplayer.data.youtube.DownloadProgress): Int {
        if (progress.total <= 0L) return 99
        return ((progress.downloaded * 100) / progress.total).toInt().coerceIn(0, 99)
    }

    private fun update(token: String, percent: Int, path: String?, failed: Boolean, done: Boolean? = null) {
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

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                DownloaderViewModel(bridge = container.youtubeBridge)
            }
        }
    }
}