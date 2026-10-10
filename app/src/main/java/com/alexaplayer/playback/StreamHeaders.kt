package com.alexaplayer.playback

import java.util.concurrent.ConcurrentHashMap

/**
 * YouTube's googlevideo URLs are only valid for the exact client/headers the extractor
 * used to sign them, so the player has to replay those request headers verbatim. The
 * resolved URL is the only stable key we have by the time Media3 asks for it, hence a
 * tiny URL -> headers map rather than threading a field through the Song model.
 */
object StreamHeaders {

    private val headersByUrl = ConcurrentHashMap<String, Map<String, String>>()

    fun put(url: String, headers: Map<String, String>) {
        if (url.isBlank() || headers.isEmpty()) return
        // The HTTP stack manages framing itself; letting the extractor's values through
        // would break decompression/range handling.
        val safe = headers.filterKeys {
            val key = it.lowercase()
            key != "accept-encoding" && key != "range" && key != "connection"
        }
        if (safe.isEmpty()) return
        headersByUrl[url] = safe
    }

    fun of(url: String): Map<String, String> = headersByUrl[url] ?: emptyMap()
}
