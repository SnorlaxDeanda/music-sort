package app.harmonium.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.harmonium.data.cache.CacheKind
import app.harmonium.data.cache.CacheSettings
import app.harmonium.data.cache.OfflineCacheRepository
import app.harmonium.data.cache.OfflineCacheUiState
import app.harmonium.data.model.Album
import app.harmonium.data.model.EqProfile
import app.harmonium.data.model.LibrarySnapshot
import app.harmonium.data.model.MediaKind
import app.harmonium.data.model.MediaProviderConfig
import app.harmonium.data.model.PlaybackQueue
import app.harmonium.data.model.Playlist
import app.harmonium.data.model.ProviderType
import app.harmonium.data.model.Track
import app.harmonium.data.repository.LibraryRepository
import app.harmonium.playback.PlayerController
import app.harmonium.playback.PlayerUiState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class HomeUiModel(
    val continueTracks: List<Track>,
    val mixes: List<Playlist>,
    val recentAlbums: List<Album>,
    val topTracks: List<Track>,
)

class AppViewModel(
    private val repository: LibraryRepository,
    private val player: PlayerController,
    private val offlineCache: OfflineCacheRepository,
) : ViewModel() {

    val library: StateFlow<LibrarySnapshot> = repository.snapshot
    val providers: StateFlow<List<MediaProviderConfig>> = repository.providers
    val queues: StateFlow<List<PlaybackQueue>> = repository.queues
    val activeQueueId: StateFlow<String> = repository.activeQueueId
    val eqProfiles: StateFlow<List<EqProfile>> = repository.eqProfiles
    val activeEqId: StateFlow<String> = repository.activeEqId
    val playerState: StateFlow<PlayerUiState> = player.state
    val offlineCacheState: StateFlow<OfflineCacheUiState> = offlineCache.state

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage = _statusMessage.asStateFlow()

    private val _providerBusy = MutableStateFlow(false)
    val providerBusy = _providerBusy.asStateFlow()

    val home: StateFlow<HomeUiModel> = repository.snapshot
        .combine(repository.queues) { snap, queues ->
            val continueIds = queues.flatMap { it.trackIds.take(1) }
            val continueTracks = continueIds.mapNotNull { id -> snap.tracks.find { it.id == id } }
                .ifEmpty { snap.tracks.filter { it.kind == MediaKind.MUSIC }.take(4) }
            HomeUiModel(
                continueTracks = continueTracks,
                mixes = repository.personalMixes(),
                recentAlbums = snap.albums.filter { it.kind == MediaKind.MUSIC }.take(8),
                topTracks = snap.tracks
                    .filter { it.kind == MediaKind.MUSIC }
                    .sortedByDescending { it.playCount }
                    .take(10),
            )
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            HomeUiModel(emptyList(), emptyList(), emptyList(), emptyList()),
        )

    val searchResults: StateFlow<LibraryRepository.SearchResults> =
        combine(repository.snapshot, _searchQuery) { _, query ->
            repository.search(query)
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            LibraryRepository.SearchResults(),
        )

    init {
        player.connect()
        viewModelScope.launch {
            while (isActive) {
                player.pollPosition()
                delay(500)
            }
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun playTrack(track: Track, queue: List<Track> = listOf(track), queueName: String? = null) {
        val index = queue.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
        player.playTracks(queue, index, queueName, track.kind)
    }

    fun playAlbum(albumId: String) {
        val tracks = repository.tracksForAlbum(albumId)
        val album = repository.albumById(albumId)
        if (tracks.isNotEmpty()) {
            player.playTracks(tracks, 0, album?.title, album?.kind ?: MediaKind.MUSIC)
        }
    }

    fun playPlaylist(playlist: Playlist) {
        val tracks = playlist.trackIds.mapNotNull { repository.trackById(it) }
        if (tracks.isNotEmpty()) {
            player.playTracks(tracks, 0, playlist.name, tracks.first().kind)
        }
    }

    fun playPause() = player.playPause()
    fun skipNext() = player.skipNext()
    fun skipPrevious() = player.skipPrevious()
    fun seekTo(ms: Long) = player.seekTo(ms)
    fun toggleShuffle() = player.toggleShuffle()
    fun cycleRepeat() = player.cycleRepeat()
    fun setSpeed(speed: Float) = player.setSpeed(speed)

    fun setActiveQueue(id: String) = repository.setActiveQueue(id)
    fun setActiveEq(id: String) = repository.setActiveEq(id)
    fun updateEqBand(profileId: String, frequencyHz: Int, gainDb: Float) =
        repository.updateEqBand(profileId, frequencyHz, gainDb)

    fun downloadTrack(track: Track) {
        offlineCache.downloadTrack(track, CacheKind.PERMANENT)
        _statusMessage.value = "Downloading ${track.title}"
    }

    fun downloadAlbum(albumId: String) {
        val tracks = repository.tracksForAlbum(albumId)
        offlineCache.downloadTracks(tracks, CacheKind.PERMANENT)
        _statusMessage.value = "Downloading ${tracks.size} tracks"
    }

    fun downloadPlaylist(playlist: Playlist) {
        val tracks = playlist.trackIds.mapNotNull { repository.trackById(it) }
        offlineCache.downloadTracks(tracks, CacheKind.PERMANENT)
        _statusMessage.value = "Downloading playlist (${tracks.size})"
    }

    fun isCached(trackId: String): Boolean = offlineCache.isCached(trackId)

    fun updateCacheSettings(transform: (CacheSettings) -> CacheSettings) =
        offlineCache.updateSettings(transform)

    fun removeCachedTrack(trackId: String) = offlineCache.removeTrack(trackId)
    fun pinCachedTrack(trackId: String) = offlineCache.pinTrack(trackId)
    fun unpinCachedTrack(trackId: String) = offlineCache.unpinTrack(trackId)
    fun clearRollingCache() {
        offlineCache.clearRolling()
        _statusMessage.value = "Rolling cache cleared"
    }

    fun clearAllCache() {
        offlineCache.clearAll()
        _statusMessage.value = "Offline cache cleared"
    }

    fun addProvider(
        type: ProviderType,
        name: String,
        baseUrl: String,
        username: String,
        password: String,
    ) {
        viewModelScope.launch {
            _providerBusy.value = true
            val result = repository.addProvider(type, name, baseUrl, username, password)
            _statusMessage.value = result.fold(
                onSuccess = { "Added ${it.name}" },
                onFailure = { it.message ?: "Failed to add provider" },
            )
            _providerBusy.value = false
        }
    }

    fun syncProvider(id: String) {
        viewModelScope.launch {
            _providerBusy.value = true
            val result = repository.syncProvider(id)
            _statusMessage.value = result.fold(
                onSuccess = { it },
                onFailure = { it.message ?: "Sync failed" },
            )
            _providerBusy.value = false
        }
    }

    fun removeProvider(id: String) = repository.removeProvider(id)

    fun consumeStatus() {
        _statusMessage.value = null
    }

    fun tracksForAlbum(albumId: String) = repository.tracksForAlbum(albumId)
    fun album(albumId: String) = repository.albumById(albumId)
    fun artist(artistId: String) = repository.artistById(artistId)

    override fun onCleared() {
        player.release()
        super.onCleared()
    }

    class Factory(
        private val repository: LibraryRepository,
        private val player: PlayerController,
        private val offlineCache: OfflineCacheRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return AppViewModel(repository, player, offlineCache) as T
        }
    }
}
