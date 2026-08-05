package app.harmonium.data.repository

import app.harmonium.data.demo.DemoLibrary
import app.harmonium.data.model.Album
import app.harmonium.data.model.Artist
import app.harmonium.data.model.EqBand
import app.harmonium.data.model.EqProfile
import app.harmonium.data.model.LibrarySnapshot
import app.harmonium.data.model.MediaKind
import app.harmonium.data.model.MediaProviderConfig
import app.harmonium.data.model.PlaybackQueue
import app.harmonium.data.model.Playlist
import app.harmonium.data.model.ProviderType
import app.harmonium.data.model.Track
import app.harmonium.data.provider.ProviderFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID

class LibraryRepository {
    private val _snapshot = MutableStateFlow(DemoLibrary.snapshot())
    val snapshot: StateFlow<LibrarySnapshot> = _snapshot.asStateFlow()

    private val _providers = MutableStateFlow(_snapshot.value.providers)
    val providers: StateFlow<List<MediaProviderConfig>> = _providers.asStateFlow()

    private val _queues = MutableStateFlow(
        listOf(
            PlaybackQueue(
                id = "q-music",
                name = "Music",
                trackIds = emptyList(),
                kind = MediaKind.MUSIC,
            ),
            PlaybackQueue(
                id = "q-books",
                name = "Audiobooks",
                trackIds = emptyList(),
                kind = MediaKind.AUDIOBOOK,
                playbackSpeed = 1.25f,
            ),
        ),
    )
    val queues: StateFlow<List<PlaybackQueue>> = _queues.asStateFlow()

    private val _activeQueueId = MutableStateFlow("q-music")
    val activeQueueId: StateFlow<String> = _activeQueueId.asStateFlow()

    private val _eqProfiles = MutableStateFlow(defaultEqProfiles())
    val eqProfiles: StateFlow<List<EqProfile>> = _eqProfiles.asStateFlow()

    private val _activeEqId = MutableStateFlow("eq-flat")
    val activeEqId: StateFlow<String> = _activeEqId.asStateFlow()

    private val providerPasswords = mutableMapOf<String, String>()

    fun trackById(id: String): Track? = _snapshot.value.tracks.find { it.id == id }

    fun albumById(id: String): Album? = _snapshot.value.albums.find { it.id == id }

    fun artistById(id: String): Artist? = _snapshot.value.artists.find { it.id == id }

    fun playlistById(id: String): Playlist? = _snapshot.value.playlists.find { it.id == id }

    fun tracksForAlbum(albumId: String): List<Track> =
        _snapshot.value.tracks.filter { it.albumId == albumId }.sortedBy { it.trackNumber ?: 0 }

    fun search(query: String): SearchResults {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return SearchResults()
        val snap = _snapshot.value
        return SearchResults(
            tracks = snap.tracks.filter {
                it.title.lowercase().contains(q) ||
                    it.artistName.lowercase().contains(q) ||
                    it.albumTitle.lowercase().contains(q)
            },
            albums = snap.albums.filter {
                it.title.lowercase().contains(q) || it.artistName.lowercase().contains(q)
            },
            artists = snap.artists.filter { it.name.lowercase().contains(q) },
            playlists = snap.playlists.filter { it.name.lowercase().contains(q) },
        )
    }

    fun personalMixes(): List<Playlist> {
        val snap = _snapshot.value
        val topRated = snap.tracks
            .filter { it.kind == MediaKind.MUSIC }
            .sortedByDescending { it.rating * 10 + it.playCount }
            .take(8)
        val recentEnergy = snap.tracks
            .filter { it.genre in setOf("Synthwave", "Electronic") }
            .take(6)
        return listOf(
            Playlist("mix-for-you", "Mix for You", DemoLibrary.PROVIDER_ID, topRated.map { it.id }),
            Playlist("mix-energy", "Instant Mix · Energy", DemoLibrary.PROVIDER_ID, recentEnergy.map { it.id }),
            Playlist(
                "mix-jazz",
                "Instant Mix · Late Night",
                DemoLibrary.PROVIDER_ID,
                snap.tracks.filter { it.genre == "Jazz" }.map { it.id },
            ),
        )
    }

    fun setActiveQueue(queueId: String) {
        if (_queues.value.any { it.id == queueId }) {
            _activeQueueId.value = queueId
        }
    }

    fun updateQueue(queue: PlaybackQueue) {
        _queues.update { list -> list.map { if (it.id == queue.id) queue else it } }
    }

    fun setActiveEq(profileId: String) {
        if (_eqProfiles.value.any { it.id == profileId }) {
            _activeEqId.value = profileId
        }
    }

    fun updateEqBand(profileId: String, frequencyHz: Int, gainDb: Float) {
        _eqProfiles.update { profiles ->
            profiles.map { profile ->
                if (profile.id != profileId) profile
                else profile.copy(
                    bands = profile.bands.map {
                        if (it.frequencyHz == frequencyHz) it.copy(gainDb = gainDb.coerceIn(-12f, 12f))
                        else it
                    },
                )
            }
        }
    }

    suspend fun addProvider(
        type: ProviderType,
        name: String,
        baseUrl: String,
        username: String,
        password: String,
    ): Result<MediaProviderConfig> {
        val config = MediaProviderConfig(
            id = UUID.randomUUID().toString(),
            type = type,
            name = name.ifBlank { type.name.lowercase().replaceFirstChar { it.titlecase() } },
            baseUrl = baseUrl.trim(),
            username = username.trim(),
            enabled = true,
        )
        val provider = ProviderFactory.create(config, password)
        val test = provider.testConnection()
        if (test.isFailure && type != ProviderType.LOCAL && type != ProviderType.DEMO) {
            return Result.failure(test.exceptionOrNull() ?: IllegalStateException("Connection failed"))
        }
        providerPasswords[config.id] = password
        _providers.update { it + config }
        return Result.success(config)
    }

    suspend fun syncProvider(providerId: String): Result<String> {
        val config = _providers.value.find { it.id == providerId }
            ?: return Result.failure(IllegalArgumentException("Unknown provider"))
        val provider = ProviderFactory.create(config, providerPasswords[providerId].orEmpty())
        val library = provider.syncLibrary().getOrElse { return Result.failure(it) }
        if (library.tracks.isNotEmpty()) {
            _snapshot.update { current ->
                current.copy(
                    artists = (current.artists.filterNot { it.providerId == providerId } + library.artists),
                    albums = (current.albums.filterNot { it.providerId == providerId } + library.albums),
                    tracks = (current.tracks.filterNot { it.providerId == providerId } + library.tracks),
                    playlists = (current.playlists.filterNot { it.providerId == providerId } + library.playlists),
                    providers = _providers.value,
                )
            }
        }
        _providers.update { list ->
            list.map {
                if (it.id == providerId) it.copy(lastSyncAt = System.currentTimeMillis()) else it
            }
        }
        return Result.success("Synced ${config.name}")
    }

    fun removeProvider(providerId: String) {
        if (providerId == DemoLibrary.PROVIDER_ID) return
        providerPasswords.remove(providerId)
        _providers.update { it.filterNot { p -> p.id == providerId } }
        _snapshot.update { current ->
            current.copy(
                providers = _providers.value,
                artists = current.artists.filterNot { it.providerId == providerId },
                albums = current.albums.filterNot { it.providerId == providerId },
                tracks = current.tracks.filterNot { it.providerId == providerId },
                playlists = current.playlists.filterNot { it.providerId == providerId },
            )
        }
    }

    data class SearchResults(
        val tracks: List<Track> = emptyList(),
        val albums: List<Album> = emptyList(),
        val artists: List<Artist> = emptyList(),
        val playlists: List<Playlist> = emptyList(),
    )

    companion object {
        fun defaultEqProfiles(): List<EqProfile> {
            fun bands(vararg gains: Float): List<EqBand> {
                val freqs = listOf(60, 170, 310, 600, 1000, 3000, 6000, 12000, 14000, 16000)
                return freqs.zip(gains.toList()).map { (f, g) -> EqBand(f, g) }
            }
            return listOf(
                EqProfile("eq-flat", "Flat", bands(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)),
                EqProfile("eq-bass", "Bass Boost", bands(6f, 4f, 2f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)),
                EqProfile("eq-treble", "Treble Boost", bands(0f, 0f, 0f, 0f, 1f, 2f, 3f, 4f, 4f, 3f)),
                EqProfile("eq-vocal", "Vocal", bands(-2f, -1f, 0f, 2f, 3f, 3f, 1f, 0f, -1f, -2f)),
                EqProfile(
                    id = "eq-auto-hd600",
                    name = "AutoEQ · HD 600",
                    bands = bands(-1.2f, 0.4f, 1.1f, 0.8f, -0.5f, 1.6f, 2.2f, 1.4f, 0.6f, -0.8f),
                    isAutoEq = true,
                ),
                EqProfile(
                    id = "eq-auto-xm4",
                    name = "AutoEQ · WH-1000XM4",
                    bands = bands(3.2f, 1.5f, -0.8f, -1.2f, 0.4f, 1.8f, 2.5f, 1.1f, 0.2f, -1.0f),
                    isAutoEq = true,
                ),
            )
        }
    }
}
