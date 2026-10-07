package com.alexaplayer.di

import android.content.Context
import com.alexaplayer.core.model.AudioFocusBehaviour
import com.alexaplayer.core.util.DefaultDispatcherProvider
import com.alexaplayer.core.util.DispatcherProvider
import com.alexaplayer.data.local.AlexaDatabase
import com.alexaplayer.data.prefs.Settings
import com.alexaplayer.data.prefs.SettingsRepository
import com.alexaplayer.data.media.MediaStoreScanner
import com.alexaplayer.data.repository.LibraryRepository
import com.alexaplayer.data.repository.PlaylistRepository
import com.alexaplayer.data.repository.SearchRepository
import com.alexaplayer.data.youtube.YoutubeBridge
import com.alexaplayer.playback.PlayerConnection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/**
 * Hand written dependency graph.
 *
 * The app has one graph and a handful of singletons, so a container keeps startup fast
 * (no annotation processing at runtime) while still giving every layer constructor
 * injection and making tests trivial to write.
 */
class AppContainer(context: Context) {

    private val appContext: Context = context.applicationContext
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val dispatchers: DispatcherProvider = DefaultDispatcherProvider()

    private val database: AlexaDatabase by lazy { AlexaDatabase.build(appContext) }

    val settingsRepository: SettingsRepository by lazy { SettingsRepository(appContext) }

    val libraryRepository: LibraryRepository by lazy {
        LibraryRepository(
            songDao = database.songDao(),
            scanner = MediaStoreScanner(appContext),
            settingsRepository = settingsRepository,
            dispatchers = dispatchers,
        )
    }

    val playlistRepository: PlaylistRepository by lazy {
        PlaylistRepository(playlistDao = database.playlistDao(), dispatchers = dispatchers)
    }

    val searchRepository: SearchRepository by lazy {
        SearchRepository(
            songDao = database.songDao(),
            playlistDao = database.playlistDao(),
            recentSearchDao = database.recentSearchDao(),
            dispatchers = dispatchers,
        )
    }

    val playerConnection: PlayerConnection by lazy { PlayerConnection(appContext) }

    val youtubeBridge: YoutubeBridge by lazy { YoutubeBridge() }

    val settings: StateFlow<Settings> = settingsRepository.settings
        .stateIn(appScope, SharingStarted.Eagerly, Settings())

    val audioFocusBehaviour: AudioFocusBehaviour get() = settings.value.audioFocusBehaviour
}
