package app.harmonium.data.cache

enum class CacheKind {
    /** Temporary buffer around active playback; eligible for rolling eviction. */
    PLAYBACK,
    /** Rolling/LRU cache of recently played or auto-cached tracks. */
    ROLLING,
    /** User-pinned offline download; never auto-evicted. */
    PERMANENT,
}

enum class DownloadStatus {
    QUEUED,
    DOWNLOADING,
    COMPLETED,
    FAILED,
    CANCELLED,
}

data class CacheEntry(
    val trackId: String,
    val title: String,
    val artistName: String,
    val sourceUrl: String,
    val fileName: String,
    val bytes: Long,
    val kind: CacheKind,
    val createdAt: Long,
    val lastAccessAt: Long,
    val pinned: Boolean = kind == CacheKind.PERMANENT,
) {
    val isOfflineReady: Boolean get() = bytes > 0L
}

data class DownloadJob(
    val trackId: String,
    val title: String,
    val artistName: String,
    val sourceUrl: String,
    val targetKind: CacheKind,
    val status: DownloadStatus = DownloadStatus.QUEUED,
    val progress: Float = 0f,
    val bytesDownloaded: Long = 0L,
    val error: String? = null,
)

data class CacheSettings(
    val playbackCacheEnabled: Boolean = true,
    val rollingCacheEnabled: Boolean = true,
    val permanentDownloadsEnabled: Boolean = true,
    val rollingCacheMaxBytes: Long = 512L * 1024L * 1024L, // 512 MB
    val playbackLookahead: Int = 2,
    val wifiOnly: Boolean = false,
    val autoCacheStarred: Boolean = true,
    val autoCacheMinPlayCount: Int = 5,
)

data class CacheStats(
    val permanentBytes: Long = 0L,
    val rollingBytes: Long = 0L,
    val playbackBytes: Long = 0L,
    val permanentCount: Int = 0,
    val rollingCount: Int = 0,
    val playbackCount: Int = 0,
    val pendingDownloads: Int = 0,
) {
    val totalBytes: Long get() = permanentBytes + rollingBytes + playbackBytes
    val totalCount: Int get() = permanentCount + rollingCount + playbackCount
}

data class OfflineCacheUiState(
    val settings: CacheSettings = CacheSettings(),
    val stats: CacheStats = CacheStats(),
    val entries: List<CacheEntry> = emptyList(),
    val downloads: List<DownloadJob> = emptyList(),
)
