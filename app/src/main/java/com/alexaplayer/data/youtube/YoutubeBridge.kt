package com.alexaplayer.data.youtube

import android.content.Context
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import com.alexaplayer.core.model.Song
import com.alexaplayer.core.model.Source
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import kotlin.math.abs

/** Raised when yt-dlp reports a failure; the message is already human readable. */
class YoutubeException(message: String) : Exception(message)

/** A hit from YouTube search. Streams are resolved lazily, only when someone taps a row. */
data class YoutubeTrack(
    val videoId: String,
    val title: String,
    val channel: String,
    val durationMs: Long,
    val thumbnailUrl: String,
    val views: Long?,
)

/** The short lived (a few hours) audio URL handed to Media3. */
data class ResolvedStream(
    val videoId: String,
    val url: String,
    val title: String,
    val channel: String,
    val durationMs: Long,
    val thumbnailUrl: String,
)

/** A finished download: absolute path on disk plus what kind of file it is. */
data class DownloadResult(
    val path: String,
    val title: String,
    val ext: String,
)

/** Progress of one live download; polled from the UI thread via a coroutine. */
data class DownloadProgress(
    val status: String,
    val downloaded: Long,
    val total: Long,
    val path: String? = null,
)

/** One in-progress download tracked by the view model with a progress percent. */
data class ActiveDownload(
    val token: String,
    val kind: String,
    val title: String,
    val percent: Int,
    val done: Boolean,
    val path: String? = null,
)

/**
 * Thin wrapper over the bundled yt-dlp. Python does the hard work (signature deciphering
 * changes every few months), Kotlin only ever sees JSON, so a yt-dlp upgrade can never
 * break a Compose signature.
 */
class YoutubeBridge(context: Context) {

    private val appContext: Context = context.applicationContext

    /**
     * The runtime has to sit on the Android platform before py objects exist; without this
     * Chaquopy throws "Cannot use Generic Platform on Android". Initialised unconditionally,
     * with explicit types (Chaquopy's generic getModule needs the target type spelled out,
     * otherwise Kotlin can't infer it inside a lazy initializer).
     */
    private val python: Python
    private val module: com.chaquo.python.PyObject

    init {
        Python.start(AndroidPlatform(appContext))
        python = Python.getInstance()
        module = python.getModule("ytmusic")
    }

    suspend fun search(query: String, limit: Int = 25): List<YoutubeTrack> =
        withContext(Dispatchers.IO) {
            val payload = JSONObject(call("search", query, limit))
            val error = payload.optString("error")
            if (error.isNotEmpty()) throw YoutubeException(error)

            val items = payload.optJSONArray("results") ?: return@withContext emptyList()
            buildList {
                for (i in 0 until items.length()) {
                    val entry = items.getJSONObject(i)
                    val id = entry.optString("id")
                    if (id.isBlank()) continue
                    add(
                        YoutubeTrack(
                            videoId = id,
                            title = entry.optString("title"),
                            channel = entry.optString("channel"),
                            durationMs = entry.optLong("duration").secondsToMillis(),
                            thumbnailUrl = entry.optString("thumbnail"),
                            views = entry.optLong("view_count").takeIf { !entry.isNull("view_count") },
                        ),
                    )
                }
            }
        }

    suspend fun resolve(videoId: String): ResolvedStream =
        withContext(Dispatchers.IO) {
            val payload = JSONObject(call("resolve", videoId))
            val error = payload.optString("error")
            if (error.isNotEmpty()) throw YoutubeException(error)

            val url = payload.optString("url")
            if (url.isBlank()) throw YoutubeException("No playable audio stream")

            ResolvedStream(
                videoId = videoId,
                url = url,
                title = payload.optString("title"),
                channel = payload.optString("channel"),
                durationMs = payload.optLong("duration").secondsToMillis(),
                thumbnailUrl = payload.optString("thumbnail"),
            )
        }

    /** Video songs play as a muxed mp4, streamed into the full-screen player. */
    suspend fun resolveVideo(videoId: String): ResolvedStream =
        withContext(Dispatchers.IO) {
            val payload = JSONObject(call("resolve_video", videoId))
            val error = payload.optString("error")
            if (error.isNotEmpty()) throw YoutubeException(error)

            val url = payload.optString("url")
            if (url.isBlank()) throw YoutubeException("No playable video stream")

            ResolvedStream(
                videoId = videoId,
                url = url,
                title = payload.optString("title"),
                channel = payload.optString("channel"),
                durationMs = payload.optLong("duration").secondsToMillis(),
                thumbnailUrl = payload.optString("thumbnail"),
            )
        }

    /**
     * Where downloads land: app-external Downloads dir. Needs no storage permission on any
     * API level and the user can reach it via Files > Internal storage > Android/data/AlexaPlayer.
     */
    val downloadsDir: java.io.File
        get() = java.io.File(appContext.applicationContext.getExternalFilesDir(null)!!, "Download")

    suspend fun download(videoId: String, kind: String, token: String): DownloadResult =
        withContext(Dispatchers.IO) {
            downloadsDir.mkdirs()
            val payload = JSONObject(call("download", videoId, kind, token, downloadsDir.absolutePath))
            val error = payload.optString("error")
            if (error.isNotEmpty()) throw YoutubeException(error)
            DownloadResult(
                path = payload.optString("path"),
                title = payload.optString("title"),
                ext = payload.optString("ext"),
            )
        }

    /** Poll this every ~500ms while a download runs; null means "not downloadable yet". */
    suspend fun downloadProgress(token: String): DownloadProgress =
        withContext(Dispatchers.IO) {
            val payload = JSONObject(call("download_progress", token))
            DownloadProgress(
                status = payload.optString("status"),
                downloaded = payload.optLong("downloaded"),
                total = payload.optLong("total"),
                path = payload.optString("path").takeIf { it.isNotBlank() },
            )
        }

    /** Queues are keyed by Long, so synthetic rows live in a negative id space of their own. */
    fun toSong(stream: ResolvedStream): Song {
        val now = System.currentTimeMillis() / 1000
        return Song(
            id = syntheticId(stream.videoId),
            title = stream.title,
            artist = stream.channel,
            album = "YouTube",
            albumId = 0L,
            artistId = 0L,
            durationMs = stream.durationMs,
            trackNumber = 0,
            year = 0,
            uri = stream.url,
            artworkUri = stream.thumbnailUrl.takeIf { it.isNotBlank() },
            dateAdded = now,
            dateModified = now,
            sizeBytes = 0L,
            mimeType = "audio/mp4",
            source = Source.STREAM,
        )
    }

    private fun call(name: String, vararg args: Any?): String =
        runCatching { module.callAttr(name, *args).toString() }
            .getOrElse { throw YoutubeException(it.message ?: "Extraction failed") }

    private fun Long.secondsToMillis(): Long = if (this <= 0L) 0L else this * 1000

    companion object {
        /**
         * Media ids must stay negative so the playback service never treats a stream as a
         * database row when it writes "last played" back. +1 keeps it off zero.
         */
        fun syntheticId(videoId: String): Long = -(abs(videoId.hashCode()).toLong() + 1L)
    }
}
