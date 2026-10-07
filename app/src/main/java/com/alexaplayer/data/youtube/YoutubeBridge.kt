package com.alexaplayer.data.youtube

import com.chaquo.python.Python
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

/**
 * Thin wrapper over the bundled yt-dlp. Python does the hard work (signature deciphering
 * changes every few months), Kotlin only ever sees JSON, so a yt-dlp upgrade can never
 * break a Compose signature.
 */
class YoutubeBridge {

    private val module by lazy { Python.getInstance().getModule("ytmusic") }

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
