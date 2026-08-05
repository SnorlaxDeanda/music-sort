package app.harmonium.data.model

enum class ProviderType {
    LOCAL,
    JELLYFIN,
    SUBSONIC,
    OPENSUBSONIC,
    NAVIDROME,
    PLEX,
    EMBY,
    AUDIOBOOKSHELF,
    SMB,
    WEBDAV,
    DEMO,
}

enum class MediaKind {
    MUSIC,
    AUDIOBOOK,
    RADIO,
    PODCAST,
}

data class MediaProviderConfig(
    val id: String,
    val type: ProviderType,
    val name: String,
    val baseUrl: String = "",
    val username: String = "",
    val enabled: Boolean = true,
    val lastSyncAt: Long? = null,
)

data class Artist(
    val id: String,
    val name: String,
    val providerId: String,
    val albumCount: Int = 0,
    val imageUrl: String? = null,
    val biography: String? = null,
)

data class Album(
    val id: String,
    val title: String,
    val artistId: String,
    val artistName: String,
    val providerId: String,
    val year: Int? = null,
    val genre: String? = null,
    val coverUrl: String? = null,
    val trackCount: Int = 0,
    val kind: MediaKind = MediaKind.MUSIC,
)

data class Track(
    val id: String,
    val title: String,
    val albumId: String,
    val albumTitle: String,
    val artistId: String,
    val artistName: String,
    val providerId: String,
    val durationMs: Long,
    val trackNumber: Int? = null,
    val discNumber: Int? = null,
    val year: Int? = null,
    val genre: String? = null,
    val rating: Float = 0f,
    val playCount: Int = 0,
    val streamUrl: String,
    val coverUrl: String? = null,
    val lyrics: String? = null,
    val syncedLyrics: String? = null,
    val kind: MediaKind = MediaKind.MUSIC,
    val bitrate: Int? = null,
    val format: String? = null,
)

data class Playlist(
    val id: String,
    val name: String,
    val providerId: String,
    val trackIds: List<String> = emptyList(),
    val isSmart: Boolean = false,
    val smartRules: List<SmartRule> = emptyList(),
    val coverUrl: String? = null,
)

data class SmartRule(
    val field: String,
    val op: String,
    val value: String,
)

data class PlaybackQueue(
    val id: String,
    val name: String,
    val trackIds: List<String>,
    val currentIndex: Int = 0,
    val shuffle: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val playbackSpeed: Float = 1f,
    val kind: MediaKind = MediaKind.MUSIC,
    val positionMs: Long = 0L,
)

enum class RepeatMode {
    OFF,
    ALL,
    ONE,
}

data class EqBand(
    val frequencyHz: Int,
    val gainDb: Float,
)

data class EqProfile(
    val id: String,
    val name: String,
    val bands: List<EqBand>,
    val preAmpDb: Float = 0f,
    val isAutoEq: Boolean = false,
)

data class LibrarySnapshot(
    val providers: List<MediaProviderConfig>,
    val artists: List<Artist>,
    val albums: List<Album>,
    val tracks: List<Track>,
    val playlists: List<Playlist>,
)
