package com.alexaplayer.ui.navigation

import com.alexaplayer.R

/** Every destination in the app. Kept as constants so no route string is ever duplicated. */
object Routes {
    const val HOME = "home"
    const val YOUTUBE = "youtube"
    const val LIBRARY = "library"
    const val PLAYLISTS = "playlists"
    const val SETTINGS = "settings"

    const val ARG_ALBUM_ID = "albumId"
    const val ARG_ARTIST_ID = "artistId"
    const val ARG_FOLDER_PATH = "folderPath"
    const val ARG_PLAYLIST_ID = "playlistId"

    const val ALBUM = "album/{$ARG_ALBUM_ID}"
    const val ARTIST = "artist/{$ARG_ARTIST_ID}"
    const val FOLDER = "folder/{$ARG_FOLDER_PATH}"
    const val PLAYLIST_DETAIL = "playlist/{$ARG_PLAYLIST_ID}"

    const val SEARCH = "search"
    const val QUEUE = "queue"
    const val NOW_PLAYING = "now_playing"
    const val STREAMS = "streams"

    fun album(albumId: Long): String = "album/$albumId"

    fun artist(artistId: Long): String = "artist/$artistId"

    fun folder(path: String): String = "folder/$path"

    fun playlist(playlistId: Long): String = "playlist/$playlistId"
}

/** Bottom navigation destinations, in the order they appear. */
enum class TopLevelDestination(
    val route: String,
    val iconRes: Int,
    val labelRes: Int,
) {
    HOME(Routes.HOME, R.drawable.ic_home, R.string.nav_home),
    YOUTUBE(Routes.YOUTUBE, R.drawable.ic_headphones, R.string.nav_youtube),
    LIBRARY(Routes.LIBRARY, R.drawable.ic_library, R.string.nav_library),
    PLAYLISTS(Routes.PLAYLISTS, R.drawable.ic_playlists, R.string.nav_playlists),
    SETTINGS(Routes.SETTINGS, R.drawable.ic_settings, R.string.nav_settings),
}