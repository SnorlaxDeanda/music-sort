package app.harmonium

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.harmonium.ui.components.MiniPlayerBar
import app.harmonium.ui.navigation.Routes
import app.harmonium.ui.screens.AlbumScreen
import app.harmonium.ui.screens.ArtistScreen
import app.harmonium.ui.screens.AudiobooksScreen
import app.harmonium.ui.screens.EqualizerScreen
import app.harmonium.ui.screens.HomeScreen
import app.harmonium.ui.screens.LibraryScreen
import app.harmonium.ui.screens.NowPlayingScreen
import app.harmonium.ui.screens.ProvidersScreen
import app.harmonium.ui.screens.QueuesScreen
import app.harmonium.ui.screens.SearchScreen
import app.harmonium.ui.screens.SettingsScreen
import app.harmonium.ui.theme.HarmoniumTheme
import app.harmonium.viewmodel.AppViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as HarmoniumApp
        setContent {
            HarmoniumTheme {
                val vm: AppViewModel = viewModel(
                    factory = AppViewModel.Factory(app.libraryRepository, app.playerController),
                )
                HarmoniumRoot(vm)
            }
        }
    }
}

private data class Dest(
    val route: String,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
)

@Composable
private fun HarmoniumRoot(vm: AppViewModel) {
    val navController = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route

    val library by vm.library.collectAsStateWithLifecycle()
    val home by vm.home.collectAsStateWithLifecycle()
    val player by vm.playerState.collectAsStateWithLifecycle()
    val providers by vm.providers.collectAsStateWithLifecycle()
    val searchQuery by vm.searchQuery.collectAsStateWithLifecycle()
    val searchResults by vm.searchResults.collectAsStateWithLifecycle()
    val busy by vm.providerBusy.collectAsStateWithLifecycle()
    val status by vm.statusMessage.collectAsStateWithLifecycle()
    val eqProfiles by vm.eqProfiles.collectAsStateWithLifecycle()
    val activeEq by vm.activeEqId.collectAsStateWithLifecycle()
    val queues by vm.queues.collectAsStateWithLifecycle()
    val activeQueue by vm.activeQueueId.collectAsStateWithLifecycle()

    val bottom = listOf(
        Dest(Routes.HOME, "Home", Icons.Default.Home),
        Dest(Routes.LIBRARY, "Library", Icons.Default.LibraryMusic),
        Dest(Routes.SEARCH, "Search", Icons.Default.Search),
        Dest(Routes.PROVIDERS, "Providers", Icons.Default.Storage),
        Dest(Routes.SETTINGS, "Settings", Icons.Default.Settings),
    )
    val showBottom = currentRoute in bottom.map { it.route } ||
        currentRoute == Routes.AUDIOBOOKS
    val showMini = player.current != null && currentRoute != Routes.NOW_PLAYING

    LaunchedEffect(status) {
        status?.let {
            snackbar.showSnackbar(it)
            vm.consumeStatus()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            ColumnBar(
                showMini = showMini,
                showBottom = showBottom,
                player = player,
                bottom = bottom,
                currentRoute = currentRoute,
                onOpenNowPlaying = { navController.navigate(Routes.NOW_PLAYING) },
                onPlayPause = vm::playPause,
                onNext = vm::skipNext,
                onNavigate = { route ->
                    navController.navigate(route) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            NavHost(navController = navController, startDestination = Routes.HOME) {
                composable(Routes.HOME) {
                    HomeScreen(
                        home = home,
                        onAlbum = { navController.navigate(Routes.album(it)) },
                        onPlayTrack = { id ->
                            library.tracks.find { it.id == id }?.let { track ->
                                vm.playTrack(track, library.tracks.filter { it.kind == track.kind })
                            }
                        },
                        onPlayPlaylist = vm::playPlaylist,
                        onOpenAudiobooks = { navController.navigate(Routes.AUDIOBOOKS) },
                    )
                }
                composable(Routes.LIBRARY) {
                    LibraryScreen(
                        library = library,
                        onAlbum = { navController.navigate(Routes.album(it)) },
                        onArtist = { navController.navigate(Routes.artist(it)) },
                        onPlayTrack = { id ->
                            library.tracks.find { it.id == id }?.let { vm.playTrack(it, library.tracks) }
                        },
                        onPlayPlaylist = vm::playPlaylist,
                    )
                }
                composable(Routes.SEARCH) {
                    SearchScreen(
                        query = searchQuery,
                        results = searchResults,
                        onQueryChange = vm::setSearchQuery,
                        onAlbum = { navController.navigate(Routes.album(it)) },
                        onArtist = { navController.navigate(Routes.artist(it)) },
                        onPlayTrack = { id ->
                            library.tracks.find { it.id == id }?.let { vm.playTrack(it, searchResults.tracks) }
                        },
                    )
                }
                composable(Routes.PROVIDERS) {
                    ProvidersScreen(
                        providers = providers,
                        busy = busy,
                        status = status,
                        onAdd = vm::addProvider,
                        onSync = vm::syncProvider,
                        onRemove = vm::removeProvider,
                        onConsumeStatus = vm::consumeStatus,
                    )
                }
                composable(Routes.SETTINGS) {
                    SettingsScreen(
                        onOpenEqualizer = { navController.navigate(Routes.EQUALIZER) },
                        onOpenQueues = { navController.navigate(Routes.QUEUES) },
                        onOpenProviders = { navController.navigate(Routes.PROVIDERS) },
                    )
                }
                composable(Routes.NOW_PLAYING) {
                    NowPlayingScreen(
                        state = player,
                        onBack = { navController.popBackStack() },
                        onPlayPause = vm::playPause,
                        onNext = vm::skipNext,
                        onPrevious = vm::skipPrevious,
                        onSeek = vm::seekTo,
                        onToggleShuffle = vm::toggleShuffle,
                        onCycleRepeat = vm::cycleRepeat,
                        onSpeed = vm::setSpeed,
                        onPlayQueueIndex = { index ->
                            player.queue.getOrNull(index)?.let { track ->
                                vm.playTrack(track, player.queue, player.queueName)
                            }
                        },
                    )
                }
                composable(Routes.EQUALIZER) {
                    EqualizerScreen(
                        profiles = eqProfiles,
                        activeId = activeEq,
                        onSelect = vm::setActiveEq,
                        onBandChange = vm::updateEqBand,
                    )
                }
                composable(Routes.QUEUES) {
                    QueuesScreen(
                        queues = queues,
                        activeId = activeQueue,
                        onSelect = vm::setActiveQueue,
                    )
                }
                composable(Routes.AUDIOBOOKS) {
                    AudiobooksScreen(
                        library = library,
                        onPlayTrack = { vm.playTrack(it, library.tracks.filter { t -> t.kind == it.kind }) },
                        onPlayAlbum = vm::playAlbum,
                    )
                }
                composable(Routes.ALBUM) { entry ->
                    val albumId = entry.arguments?.getString("albumId").orEmpty()
                    AlbumScreen(
                        album = vm.album(albumId),
                        tracks = vm.tracksForAlbum(albumId),
                        onBack = { navController.popBackStack() },
                        onPlayAlbum = { vm.playAlbum(albumId) },
                        onPlayTrack = { track ->
                            vm.playTrack(track, vm.tracksForAlbum(albumId))
                        },
                    )
                }
                composable(Routes.ARTIST) { entry ->
                    val artistId = entry.arguments?.getString("artistId").orEmpty()
                    val artist = vm.artist(artistId)
                    ArtistScreen(
                        artist = artist,
                        albums = library.albums.filter { it.artistId == artistId },
                        tracks = library.tracks.filter { it.artistId == artistId },
                        onBack = { navController.popBackStack() },
                        onAlbum = { navController.navigate(Routes.album(it)) },
                        onPlayTrack = { track ->
                            vm.playTrack(track, library.tracks.filter { it.artistId == artistId })
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ColumnBar(
    showMini: Boolean,
    showBottom: Boolean,
    player: app.harmonium.playback.PlayerUiState,
    bottom: List<Dest>,
    currentRoute: String?,
    onOpenNowPlaying: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onNavigate: (String) -> Unit,
) {
    androidx.compose.foundation.layout.Column {
        AnimatedVisibility(
            visible = showMini,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
        ) {
            MiniPlayerBar(
                state = player,
                onOpen = onOpenNowPlaying,
                onPlayPause = onPlayPause,
                onNext = onNext,
            )
        }
        AnimatedVisibility(visible = showBottom) {
            NavigationBar {
                bottom.forEach { dest ->
                    NavigationBarItem(
                        selected = currentRoute == dest.route,
                        onClick = { onNavigate(dest.route) },
                        icon = { Icon(dest.icon, contentDescription = dest.label) },
                        label = { Text(dest.label) },
                    )
                }
            }
        }
    }
}
