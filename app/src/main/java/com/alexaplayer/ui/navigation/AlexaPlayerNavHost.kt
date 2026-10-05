package com.alexaplayer.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Modifier
import android.net.Uri
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.alexaplayer.R
import com.alexaplayer.core.model.Song
import com.alexaplayer.core.model.SortOrder
import com.alexaplayer.di.AppContainer
import com.alexaplayer.ui.component.MiniPlayer
import com.alexaplayer.ui.component.OptionsSheet
import com.alexaplayer.ui.component.PickSongsSheet
import com.alexaplayer.ui.component.SheetOption
import com.alexaplayer.ui.screen.detail.DetailScreen
import com.alexaplayer.ui.screen.home.HomeScreen
import com.alexaplayer.ui.screen.library.LibraryScreen
import com.alexaplayer.ui.screen.nowplaying.NowPlayingScreen
import com.alexaplayer.ui.screen.playlists.PlaylistDetailScreen
import com.alexaplayer.ui.screen.playlists.PlaylistsScreen
import com.alexaplayer.ui.screen.queue.QueueScreen
import com.alexaplayer.ui.screen.search.SearchScreen
import com.alexaplayer.ui.screen.settings.SettingsScreen
import com.alexaplayer.ui.screen.streams.StreamsScreen
import com.alexaplayer.ui.viewmodel.DetailTarget
import com.alexaplayer.ui.viewmodel.DetailViewModel
import com.alexaplayer.ui.viewmodel.HomeViewModel
import com.alexaplayer.ui.viewmodel.LibraryViewModel
import com.alexaplayer.ui.viewmodel.NowPlayingViewModel
import com.alexaplayer.ui.viewmodel.PlaylistDetailViewModel
import com.alexaplayer.ui.viewmodel.PlaylistsViewModel
import com.alexaplayer.ui.viewmodel.QueueViewModel
import com.alexaplayer.ui.viewmodel.RootViewModel
import com.alexaplayer.ui.viewmodel.SearchViewModel
import com.alexaplayer.ui.viewmodel.SettingsViewModel
import com.alexaplayer.ui.viewmodel.StreamsViewModel

private enum class SongAction { PLAY_NEXT, ADD_QUEUE, ADD_PLAYLIST, REMOVE_FROM_PLAYLIST, FAVOURITE, DELETE_STREAM }

private data class SongSheetTarget(val song: Song, val playlistId: Long?, val isStream: Boolean)

@Composable
fun AlexaPlayerNavHost(container: AppContainer, modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val rootViewModel: RootViewModel = viewModel(factory = RootViewModel.factory(container))
    val miniPlayer by rootViewModel.miniPlayer.collectAsStateWithLifecycle()
    val playback by container.playerConnection.state.collectAsStateWithLifecycle()
    val currentSongId = playback.currentSongId
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = TopLevelDestination.entries.any { it.route == currentRoute }

    var sheetTarget by remember { mutableStateOf<SongSheetTarget?>(null) }
    var playlistPickerFor by rememberSaveable { mutableStateOf<Long?>(null) }
    var pickerSelection by remember { mutableStateOf<Set<Long>>(emptySet()) }
    val scope = rememberCoroutineScope()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        bottomBar = {
            Column {
                AnimatedVisibility(
                    visible = miniPlayer.song != null && currentRoute != Routes.NOW_PLAYING,
                    enter = slideInVertically { it },
                    exit = slideOutVertically { it },
                ) {
                    miniPlayer.song?.let { song ->
                        MiniPlayer(
                            song = song,
                            isPlaying = miniPlayer.isPlaying,
                            isBuffering = miniPlayer.isBuffering,
                            progressFraction = miniPlayer.progressFraction,
                            onOpenPlayer = { navController.navigate(Routes.NOW_PLAYING) },
                            onTogglePlayPause = rootViewModel::togglePlayPause,
                            onNext = rootViewModel::next,
                            onMore = { sheetTarget = SongSheetTarget(song, playlistId = null, isStream = false) },
                        )
                    }
                }
                if (showBottomBar) {
                    BottomNavBar(
                        currentRoute = currentRoute,
                        onNavigate = { destination -> navController.navigateTopLevel(destination.route) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier.fillMaxSize(),
        ) {
            composable(Routes.HOME) {
                val vm: HomeViewModel = viewModel(factory = HomeViewModel.factory(container))
                val state by vm.uiState.collectAsStateWithLifecycle()
                HomeScreen(
                    state = state,
                    currentSongId = currentSongId,
                    contentPadding = padding,
                    onPlay = { songs, index -> rootViewModel.openSong(songs, index) },
                    onPlayAll = { songs -> vm.playAll(songs) },
                    onShuffleAll = { songs -> vm.shuffleAll(songs) },
                    onToggleFavorite = { song ->
                        scope.launch {
                            container.libraryRepository.setFavorite(song.id, !song.isFavorite)
                        }
                    },
                    onOpenAlbum = { navController.navigate("album/$it") },
                    onOpenPlaylist = { navController.navigate("playlist/$it") },
                    onOpenPlaylists = { navController.navigate(Routes.PLAYLISTS) },
                    onOpenLibrary = { navController.navigate(Routes.LIBRARY) },
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                    onMore = { sheetTarget = SongSheetTarget(it, null, false) },
                )
            }

            composable(Routes.LIBRARY) {
                val vm: LibraryViewModel = viewModel(factory = LibraryViewModel.factory(container))
                val state by vm.uiState.collectAsStateWithLifecycle()
                var query by rememberSaveable { mutableStateOf("") }
                var showSort by remember { mutableStateOf(false) }

                LibraryScreen(
                    state = state,
                    currentSongId = currentSongId,
                    contentPadding = padding,
                    query = query,
                    onQueryChange = { query = it },
                    onClearQuery = { query = "" },
                    onSelectTab = vm::selectTab,
                    onSelectFilter = vm::selectFilter,
                    onOpenSort = { showSort = true },
                    onPlay = { songs, index -> rootViewModel.openSong(songs, index) },
                    onPlayAll = { songs -> vm.playAll(songs) },
                    onShuffleAll = { songs -> vm.shuffleAll(songs) },
                    onToggleFavorite = { song ->
                        scope.launch {
                            container.libraryRepository.setFavorite(song.id, !song.isFavorite)
                        }
                    },
                    onOpenAlbum = { navController.navigate("album/$it") },
                    onOpenArtist = { navController.navigate("artist/$it") },
                    onOpenFolder = { path, name ->
                        navController.navigate(
                            "folder/${Uri.encode(path)}?folderName=${Uri.encode(name)}",
                        )
                    },
                    onMore = { sheetTarget = SongSheetTarget(it, null, false) },
                )

                if (showSort) {
                    OptionsSheet(
                        title = stringResource(R.string.library_sort_title),
                        options = SortOrder.entries.map { order ->
                            SheetOption(
                                label = stringResource(order.labelRes),
                                value = order.key,
                                selected = state.sortOrder == order,
                            )
                        },
                        onSelect = { option ->
                            vm.selectSortOrder(SortOrder.fromKey(option.value))
                            showSort = false
                        },
                        onDismiss = { showSort = false },
                    )
                }
            }

            composable(Routes.PLAYLISTS) {
                val vm: PlaylistsViewModel = viewModel(factory = PlaylistsViewModel.factory(container))
                val rows by vm.playlists.collectAsStateWithLifecycle()
                var playlistSheet by remember { mutableStateOf<Long?>(null) }
                PlaylistsScreen(
                    playlists = rows.map { it.playlist },
                    contentPadding = padding,
                    onBack = null,
                    onCreate = vm::create,
                    onRename = vm::rename,
                    onDelete = vm::delete,
                    onOpen = { navController.navigate("playlist/$it") },
                    onMore = { playlist -> playlistSheet = playlist.id },
                )

                playlistSheet?.let { playlistId ->
                    OptionsSheet(
                        title = stringResource(R.string.playlists_title),
                        options = listOf(
                            SheetOption(
                                label = stringResource(R.string.action_play),
                                iconRes = R.drawable.ic_play,
                                value = "play",
                            ),
                            SheetOption(
                                label = stringResource(R.string.action_shuffle),
                                iconRes = R.drawable.ic_shuffle,
                                value = "shuffle",
                            ),
                        ),
                        onSelect = { option ->
                            vm.playPlaylist(playlistId, option.value == "shuffle")
                            playlistSheet = null
                        },
                        onDismiss = { playlistSheet = null },
                    )
                }
            }

            composable(
                route = Routes.PLAYLIST_DETAIL,
                arguments = listOf(navArgument(Routes.ARG_PLAYLIST_ID) { type = NavType.LongType }),
            ) { entry ->
                val playlistId = entry.arguments?.getLong(Routes.ARG_PLAYLIST_ID) ?: 0L
                val vm: PlaylistDetailViewModel =
                    viewModel(factory = PlaylistDetailViewModel.factory(container, playlistId))
                val state by vm.uiState.collectAsStateWithLifecycle()
                PlaylistDetailScreen(
                    state = state,
                    contentPadding = padding,
                    onBack = { navController.popBackStack() },
                    onPlay = vm::play,
                    onPlayAll = vm::playAll,
                    onShuffle = vm::shuffle,
                    onMove = vm::move,
                    onRemoveSong = vm::removeSong,
                    onToggleFavorite = { song ->
                        scope.launch {
                            container.libraryRepository.setFavorite(song.id, !song.isFavorite)
                        }
                    },
                    onAddSongs = {
                        pickerSelection = emptySet()
                        playlistPickerFor = playlistId
                    },
                    onRename = vm::rename,
                    onDelete = vm::delete,
                    onMore = { sheetTarget = SongSheetTarget(it, playlistId, false) },
                )
            }

            composable(
                route = Routes.ALBUM,
                arguments = listOf(navArgument(Routes.ARG_ALBUM_ID) { type = NavType.LongType }),
            ) { entry ->
                val albumId = entry.arguments?.getLong(Routes.ARG_ALBUM_ID) ?: 0L
                val vm: DetailViewModel = viewModel(
                    factory = DetailViewModel.factory(container, DetailTarget.AlbumTarget(albumId)),
                )
                val state by vm.uiState.collectAsStateWithLifecycle()
                DetailScreen(
                    state = state,
                    contentPadding = padding,
                    onBack = { navController.popBackStack() },
                    onPlay = vm::play,
                    onPlayAll = vm::playAll,
                    onShuffle = vm::shuffle,
                    onToggleFavorite = { song ->
                        scope.launch {
                            container.libraryRepository.setFavorite(song.id, !song.isFavorite)
                        }
                    },
                    onOpenAlbum = { navController.navigate("album/$it") },
                    onMore = { sheetTarget = SongSheetTarget(it, null, false) },
                )
            }

            composable(
                route = Routes.ARTIST,
                arguments = listOf(navArgument(Routes.ARG_ARTIST_ID) { type = NavType.LongType }),
            ) { entry ->
                val artistId = entry.arguments?.getLong(Routes.ARG_ARTIST_ID) ?: 0L
                val vm: DetailViewModel = viewModel(
                    factory = DetailViewModel.factory(container, DetailTarget.ArtistTarget(artistId)),
                )
                val state by vm.uiState.collectAsStateWithLifecycle()
                DetailScreen(
                    state = state,
                    contentPadding = padding,
                    onBack = { navController.popBackStack() },
                    onPlay = vm::play,
                    onPlayAll = vm::playAll,
                    onShuffle = vm::shuffle,
                    onToggleFavorite = { song ->
                        scope.launch {
                            container.libraryRepository.setFavorite(song.id, !song.isFavorite)
                        }
                    },
                    onOpenAlbum = { navController.navigate("album/$it") },
                    onMore = { sheetTarget = SongSheetTarget(it, null, false) },
                )
            }

            composable(
                route = "folder/{${Routes.ARG_FOLDER_PATH}}?folderName={folderName}",
                arguments = listOf(
                    navArgument(Routes.ARG_FOLDER_PATH) { type = NavType.StringType },
                    navArgument("folderName") {
                        type = NavType.StringType
                        defaultValue = ""
                    },
                ),
            ) { entry ->
                val path = Uri.decode(entry.arguments?.getString(Routes.ARG_FOLDER_PATH).orEmpty())
                val name = Uri.decode(entry.arguments?.getString("folderName").orEmpty())
                val vm: DetailViewModel = viewModel(
                    factory = DetailViewModel.factory(
                        container,
                        DetailTarget.FolderTarget(path = path, name = name.ifBlank { path }),
                    ),
                )
                val state by vm.uiState.collectAsStateWithLifecycle()
                DetailScreen(
                    state = state,
                    contentPadding = padding,
                    onBack = { navController.popBackStack() },
                    onPlay = vm::play,
                    onPlayAll = vm::playAll,
                    onShuffle = vm::shuffle,
                    onToggleFavorite = { song ->
                        scope.launch {
                            container.libraryRepository.setFavorite(song.id, !song.isFavorite)
                        }
                    },
                    onOpenAlbum = { navController.navigate("album/$it") },
                    onMore = { sheetTarget = SongSheetTarget(it, null, false) },
                )
            }

            composable(Routes.SEARCH) {
                val vm: SearchViewModel = viewModel(factory = SearchViewModel.factory(container))
                val query by vm.searchQuery.collectAsStateWithLifecycle()
                val results by vm.results.collectAsStateWithLifecycle()
                val recent by vm.recentSearches.collectAsStateWithLifecycle()
                SearchScreen(
                    results = results,
                    recentSearches = recent,
                    currentSongId = currentSongId,
                    contentPadding = padding,
                    query = query,
                    onBack = { navController.popBackStack() },
                    onQueryChange = vm::onQueryChange,
                    onClearQuery = vm::clearQuery,
                    onSubmit = vm::commitQuery,
                    onForget = vm::forget,
                    onClearHistory = vm::clearHistory,
                    onPlay = { songs, index -> rootViewModel.openSong(songs, index) },
                    onOpenAlbum = { navController.navigate("album/$it") },
                    onOpenArtist = { navController.navigate("artist/$it") },
                    onOpenPlaylist = { navController.navigate("playlist/$it") },
                    onMore = { sheetTarget = SongSheetTarget(it, null, false) },
                )
            }

            composable(Routes.QUEUE) {
                val vm: QueueViewModel = viewModel(factory = QueueViewModel.factory(container))
                val state by vm.uiState.collectAsStateWithLifecycle()
                QueueScreen(
                    state = state,
                    contentPadding = padding,
                    onBack = { navController.popBackStack() },
                    onOpenPlayer = { navController.navigate(Routes.NOW_PLAYING) },
                    onPlayIndex = vm::playIndex,
                    onRemove = vm::removeAt,
                    onClearUpcoming = vm::clearUpcoming,
                )
            }

            composable(Routes.NOW_PLAYING) {
                val vm: NowPlayingViewModel = viewModel(factory = NowPlayingViewModel.factory(container))
                val state by vm.uiState.collectAsStateWithLifecycle()
                val upNext by vm.upNext.collectAsStateWithLifecycle()
                val errorRes by vm.errors.collectAsStateWithLifecycle(initialValue = 0)
                NowPlayingScreen(
                    state = state,
                    upNext = upNext,
                    contentPadding = padding,
                    onBack = { navController.popBackStack() },
                    onOpenQueue = { navController.navigate(Routes.QUEUE) },
                    onTogglePlayPause = vm::togglePlayPause,
                    onNext = vm::next,
                    onPrevious = vm::previous,
                    onSeekTo = vm::seekTo,
                    onToggleShuffle = vm::toggleShuffle,
                    onCycleRepeat = vm::cycleRepeatMode,
                    onToggleFavorite = { song ->
                        scope.launch {
                            container.libraryRepository.setFavorite(song.id, !song.isFavorite)
                        }
                    },
                    onPlayUpNext = vm::playUpNextIndex,
                    errorText = if (errorRes == 0) null else stringResource(errorRes),
                )
            }

            composable(Routes.SETTINGS) {
                val vm: SettingsViewModel = viewModel(factory = SettingsViewModel.factory(container))
                val settings by vm.settings.collectAsStateWithLifecycle()
                var showAbout by remember { mutableStateOf(false) }
                SettingsScreen(
                    settings = settings,
                    contentPadding = padding,
                    onBack = null,
                    onThemeMode = vm::setThemeMode,
                    onAmoled = vm::setAmoled,
                    onDynamicColor = vm::setDynamicColor,
                    onGapless = vm::setGapless,
                    onResumePlayback = vm::setResumePlayback,
                    onAudioFocus = vm::setAudioFocusBehaviour,
                    onNotification = vm::setMediaNotificationEnabled,
                    onOpenAbout = { showAbout = true },
                )

                if (showAbout) {
                    AlertDialog(
                        onDismissRequest = { showAbout = false },
                        title = { Text(stringResource(R.string.app_name)) },
                        text = { Text(stringResource(R.string.settings_about_summary)) },
                        confirmButton = {
                            TextButton(onClick = { showAbout = false }) {
                                Text(stringResource(R.string.action_ok))
                            }
                        },
                    )
                }
            }

            composable(Routes.STREAMS) {
                val vm: StreamsViewModel = viewModel(factory = StreamsViewModel.factory(container))
                val streams by vm.streams.collectAsStateWithLifecycle()
                StreamsScreen(
                    streams = streams,
                    contentPadding = padding,
                    onBack = { navController.popBackStack() },
                    onPlay = { songs, index -> rootViewModel.openSong(songs, index) },
                    onAdd = vm::add,
                    onDelete = vm::delete,
                    onMore = { sheetTarget = SongSheetTarget(it, null, true) },
                )
            }
        }
    }

    sheetTarget?.let { target ->
        val playNextLabel = stringResource(R.string.action_play_next)
        val addQueueLabel = stringResource(R.string.action_add_to_queue)
        val addPlaylistLabel = stringResource(R.string.action_add_to_playlist)
        val removeLabel = stringResource(R.string.action_remove_from_playlist)
        val favouriteLabel = stringResource(R.string.action_favourite)
        val deleteStreamLabel = stringResource(R.string.streams_remove)

        OptionsSheet(
            title = target.song.title,
            options = buildList {
                add(SheetOption(label = playNextLabel, iconRes = R.drawable.ic_playlist_add, value = SongAction.PLAY_NEXT.name))
                add(SheetOption(label = addQueueLabel, iconRes = R.drawable.ic_playlists, value = SongAction.ADD_QUEUE.name))
                if (target.playlistId != null) {
                    add(
                        SheetOption(
                            label = removeLabel,
                            iconRes = R.drawable.ic_delete,
                            destructive = true,
                            value = SongAction.REMOVE_FROM_PLAYLIST.name,
                        ),
                    )
                } else {
                    add(
                        SheetOption(
                            label = addPlaylistLabel,
                            iconRes = R.drawable.ic_playlist_add,
                            value = SongAction.ADD_PLAYLIST.name,
                        ),
                    )
                }
                add(SheetOption(label = favouriteLabel, iconRes = R.drawable.ic_favorite, value = SongAction.FAVOURITE.name))
                if (target.isStream) {
                    add(
                        SheetOption(
                            label = deleteStreamLabel,
                            iconRes = R.drawable.ic_delete,
                            destructive = true,
                            value = SongAction.DELETE_STREAM.name,
                        ),
                    )
                }
            },
            onSelect = { option ->
                val action = option.value?.let { name -> SongAction.entries.firstOrNull { it.name == name } }
                sheetTarget = null
                when (action) {
                    SongAction.PLAY_NEXT -> container.playerConnection.playNext(listOf(target.song))
                    SongAction.ADD_QUEUE -> container.playerConnection.addToQueue(listOf(target.song))
                    SongAction.ADD_PLAYLIST -> scope.launch {
                        pickerSelection = emptySet()
                        playlistPickerFor = target.playlistId
                            ?: container.playlistRepository.playlists.first().firstOrNull()?.id
                    }
                    SongAction.REMOVE_FROM_PLAYLIST -> scope.launch {
                        container.playlistRepository.removeSong(target.playlistId ?: 0L, target.song.id)
                    }
                    SongAction.FAVOURITE -> scope.launch {
                        container.libraryRepository.setFavorite(target.song.id, !target.song.isFavorite)
                    }
                    SongAction.DELETE_STREAM -> scope.launch {
                        container.libraryRepository.deleteStream(target.song.id)
                    }
                    null -> Unit
                }
            },
            onDismiss = { sheetTarget = null },
        )
    }

    playlistPickerFor?.let { playlistId ->
        val vm: PlaylistDetailViewModel =
            viewModel(factory = PlaylistDetailViewModel.factory(container, playlistId))
        val state by vm.uiState.collectAsStateWithLifecycle()
        PickSongsSheet(
            title = stringResource(R.string.action_add_to_playlist),
            songs = state.songs,
            selectedIds = pickerSelection,
            onToggle = { song ->
                pickerSelection = if (pickerSelection.contains(song.id)) {
                    pickerSelection - song.id
                } else {
                    pickerSelection + song.id
                }
            },
            onConfirm = {
                vm.addSongs(state.songs.filter { pickerSelection.contains(it.id) })
                pickerSelection = emptySet()
                playlistPickerFor = null
            },
            onDismiss = {
                pickerSelection = emptySet()
                playlistPickerFor = null
            },
        )
    }
}

private fun NavHostController.navigateTopLevel(route: String) {
    navigate(route) {
        popUpTo(Routes.HOME) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}