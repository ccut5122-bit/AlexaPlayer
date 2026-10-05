package com.alexaplayer.core.model

import androidx.annotation.StringRes
import com.alexaplayer.R

/** User theme preference. Persisted by name so reordering the enum is safe. */
enum class ThemeMode(val key: String, @StringRes val labelRes: Int) {
    SYSTEM("system", R.string.settings_theme_system),
    LIGHT("light", R.string.settings_theme_light),
    DARK("dark", R.string.settings_theme_dark),
    ;

    companion object {
        fun fromKey(value: String?): ThemeMode =
            entries.firstOrNull { it.key == value } ?: DARK
    }
}

/** Library sort options. Deliberately few: enough to be useful, not a settings maze. */
enum class SortOrder(val key: String, @StringRes val labelRes: Int) {
    RECENTLY_ADDED("recently_added", R.string.library_sort_recently_added),
    RECENTLY_PLAYED("recently_played", R.string.library_sort_recently_played),
    TITLE("title", R.string.library_sort_alphabetical),
    ARTIST("artist", R.string.library_sort_artist),
    DURATION("duration", R.string.library_sort_duration),
    ;

    companion object {
        fun fromKey(value: String?): SortOrder =
            entries.firstOrNull { it.key == value } ?: TITLE
    }
}

/** Top level library views. */
enum class LibraryTab(val key: String, @StringRes val labelRes: Int) {
    SONGS("songs", R.string.library_tab_songs),
    ALBUMS("albums", R.string.library_tab_albums),
    ARTISTS("artists", R.string.library_tab_artists),
    FOLDERS("folders", R.string.library_tab_folders),
    FAVORITES("favorites", R.string.library_tab_favorites),
    ;

    companion object {
        fun fromKey(value: String?): LibraryTab =
            entries.firstOrNull { it.key == value } ?: SONGS
    }
}

/** What should happen when another app or a call takes audio focus. */
enum class AudioFocusBehaviour(val key: String, @StringRes val labelRes: Int) {
    PAUSE("pause", R.string.settings_audio_focus_pause),
    DUCK("duck", R.string.settings_audio_focus_duck),
    ;

    companion object {
        fun fromKey(value: String?): AudioFocusBehaviour =
            entries.firstOrNull { it.key == value } ?: PAUSE
    }
}
