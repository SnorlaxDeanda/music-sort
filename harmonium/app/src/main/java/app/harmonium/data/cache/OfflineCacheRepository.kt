package app.harmonium.data.cache

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import app.harmonium.data.model.Track
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

/**
 * Offline-first cache: permanent downloads, rolling LRU, and playback lookahead.
 * Files live under app-private storage — no storage permission required.
 */
class OfflineCacheRepository(
    context: Context,
    private val client: OkHttpClient = defaultClient(),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    rootDir: File = File(context.filesDir, "offline-cache"),
) {
    private val appContext = context.applicationContext
    private val mediaDir = File(rootDir, "media").also { it.mkdirs() }
    private val indexFile = File(rootDir, "index.txt")
    private val settingsFile = File(rootDir, "settings.txt")

    private val mutex = Mutex()
    private var index = loadIndex()
    private val downloadJobs = linkedMapOf<String, Job>()
    private val activeDownloads = linkedMapOf<String, DownloadJob>()

    private val _state = MutableStateFlow(buildUiState(loadSettings(), emptyList()))
    val state: StateFlow<OfflineCacheUiState> = _state.asStateFlow()

    fun isCached(trackId: String): Boolean =
        index.get(trackId)?.isOfflineReady == true && localFile(trackId)?.exists() == true

    fun cachedFile(trackId: String): File? {
        val entry = index.get(trackId) ?: return null
        val file = File(mediaDir, entry.fileName)
        return file.takeIf { it.exists() && it.length() > 0L }
    }

    /** Prefer local file URI when available; otherwise original stream URL. */
    fun resolvePlaybackUri(track: Track): String {
        val local = cachedFile(track.id)
        if (local != null) {
            scope.launch { touch(track.id) }
            return local.toURI().toString()
        }
        return track.streamUrl
    }

    fun updateSettings(transform: (CacheSettings) -> CacheSettings) {
        scope.launch {
            mutex.withLock {
                val next = transform(_state.value.settings)
                persistSettings(next)
                enforceLimitLocked(next)
                persistIndexLocked()
                publishLocked(next)
            }
        }
    }

    fun downloadTrack(track: Track, kind: CacheKind = CacheKind.PERMANENT) {
        if (track.streamUrl.isBlank()) return
        scope.launch { enqueueDownload(track, kind) }
    }

    fun downloadTracks(tracks: List<Track>, kind: CacheKind = CacheKind.PERMANENT) {
        tracks.forEach { downloadTrack(it, kind) }
    }

    fun removeTrack(trackId: String) {
        scope.launch {
            mutex.withLock {
                downloadJobs.remove(trackId)?.cancel()
                val removed = index.remove(trackId)
                removed?.let { File(mediaDir, it.fileName).delete() }
                persistIndexLocked()
                publishLocked()
            }
        }
    }

    fun pinTrack(trackId: String) {
        scope.launch {
            mutex.withLock {
                index.promoteToPermanent(trackId)
                persistIndexLocked()
                publishLocked()
            }
        }
    }

    fun unpinTrack(trackId: String) {
        scope.launch {
            mutex.withLock {
                index.demoteToRolling(trackId)
                enforceLimitLocked()
                persistIndexLocked()
                publishLocked()
            }
        }
    }

    fun clearRolling() {
        scope.launch {
            mutex.withLock {
                index.clearEvictable().forEach { File(mediaDir, it.fileName).delete() }
                persistIndexLocked()
                publishLocked()
            }
        }
    }

    fun clearAll() {
        scope.launch {
            mutex.withLock {
                downloadJobs.values.forEach { it.cancel() }
                downloadJobs.clear()
                index.clearAll().forEach { File(mediaDir, it.fileName).delete() }
                persistIndexLocked()
                publishLocked()
            }
        }
    }

    /**
     * Playback cache: ensure current + lookahead tracks are on disk as ROLLING/PLAYBACK.
     */
    fun ensurePlaybackCache(queue: List<Track>, currentIndex: Int) {
        val settings = _state.value.settings
        if (!settings.playbackCacheEnabled) return
        if (queue.isEmpty()) return
        val start = currentIndex.coerceIn(0, queue.lastIndex)
        val end = (start + settings.playbackLookahead).coerceAtMost(queue.lastIndex)
        for (i in start..end) {
            val track = queue[i]
            if (track.streamUrl.isBlank()) continue
            if (isCached(track.id)) {
                scope.launch { touch(track.id) }
                continue
            }
            val kind = if (i == start) CacheKind.PLAYBACK else CacheKind.ROLLING
            downloadTrack(track, kind)
        }
    }

    fun autoCacheIfNeeded(track: Track) {
        val settings = _state.value.settings
        if (!settings.rollingCacheEnabled) return
        if (isCached(track.id)) return
        val starred = settings.autoCacheStarred && track.rating >= 4.5f
        val frequent = track.playCount >= settings.autoCacheMinPlayCount
        if (starred || frequent) {
            downloadTrack(track, CacheKind.ROLLING)
        }
    }

    private suspend fun touch(trackId: String) {
        mutex.withLock {
            index.touch(trackId)
            persistIndexLocked()
            publishLocked()
        }
    }

    private suspend fun enqueueDownload(track: Track, kind: CacheKind) {
        if (!canDownloadNow()) {
            publishDownload(
                DownloadJob(
                    trackId = track.id,
                    title = track.title,
                    artistName = track.artistName,
                    sourceUrl = track.streamUrl,
                    targetKind = kind,
                    status = DownloadStatus.FAILED,
                    error = "Downloads limited to Wi‑Fi in settings",
                ),
            )
            return
        }

        mutex.withLock {
            if (index.get(track.id)?.isOfflineReady == true && localFile(track.id)?.exists() == true) {
                if (kind == CacheKind.PERMANENT) {
                    index.promoteToPermanent(track.id)
                    persistIndexLocked()
                    publishLocked()
                }
                return
            }
            if (downloadJobs[track.id]?.isActive == true) return
        }

        val job = scope.launch {
            runDownload(track, kind)
        }
        mutex.withLock { downloadJobs[track.id] = job }
    }

    private suspend fun runDownload(track: Track, kind: CacheKind) {
        publishDownload(
            DownloadJob(
                trackId = track.id,
                title = track.title,
                artistName = track.artistName,
                sourceUrl = track.streamUrl,
                targetKind = kind,
                status = DownloadStatus.DOWNLOADING,
                progress = 0f,
            ),
        )

        val ext = guessExtension(track.streamUrl, track.format)
        val fileName = "${track.id.replace('/', '_')}.$ext"
        val partial = File(mediaDir, "$fileName.part")
        val target = File(mediaDir, fileName)

        val result = withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder().url(track.streamUrl).get().build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) error("HTTP ${response.code}")
                    val body = response.body ?: error("Empty body")
                    val total = body.contentLength().takeIf { it > 0 } ?: -1L
                    body.byteStream().use { input ->
                        FileOutputStream(partial).use { output ->
                            val buffer = ByteArray(DEFAULT_BUFFER)
                            var read: Int
                            var downloaded = 0L
                            while (input.read(buffer).also { read = it } >= 0) {
                                currentCoroutineContext().ensureActive()
                                output.write(buffer, 0, read)
                                downloaded += read
                                val progress = if (total > 0) downloaded.toFloat() / total.toFloat() else 0f
                                publishDownload(
                                    DownloadJob(
                                        trackId = track.id,
                                        title = track.title,
                                        artistName = track.artistName,
                                        sourceUrl = track.streamUrl,
                                        targetKind = kind,
                                        status = DownloadStatus.DOWNLOADING,
                                        progress = progress.coerceIn(0f, 1f),
                                        bytesDownloaded = downloaded,
                                    ),
                                )
                            }
                            downloaded
                        }
                    }
                }
            }
        }

        result.fold(
            onSuccess = { bytes ->
                if (target.exists()) target.delete()
                if (!partial.renameTo(target)) {
                    partial.copyTo(target, overwrite = true)
                    partial.delete()
                }
                val now = System.currentTimeMillis()
                val entry = CacheEntry(
                    trackId = track.id,
                    title = track.title,
                    artistName = track.artistName,
                    sourceUrl = track.streamUrl,
                    fileName = fileName,
                    bytes = bytes,
                    kind = kind,
                    createdAt = now,
                    lastAccessAt = now,
                    pinned = kind == CacheKind.PERMANENT,
                )
                mutex.withLock {
                    index.put(entry)
                    enforceLimitLocked()
                    persistIndexLocked()
                    downloadJobs.remove(track.id)
                    publishLocked()
                }
                publishDownload(
                    DownloadJob(
                        trackId = track.id,
                        title = track.title,
                        artistName = track.artistName,
                        sourceUrl = track.streamUrl,
                        targetKind = kind,
                        status = DownloadStatus.COMPLETED,
                        progress = 1f,
                        bytesDownloaded = bytes,
                    ),
                )
            },
            onFailure = { error ->
                partial.delete()
                mutex.withLock { downloadJobs.remove(track.id) }
                if (error is CancellationException) {
                    publishDownload(
                        DownloadJob(
                            trackId = track.id,
                            title = track.title,
                            artistName = track.artistName,
                            sourceUrl = track.streamUrl,
                            targetKind = kind,
                            status = DownloadStatus.CANCELLED,
                            error = "Cancelled",
                        ),
                    )
                    publishLocked()
                    throw error
                }
                publishDownload(
                    DownloadJob(
                        trackId = track.id,
                        title = track.title,
                        artistName = track.artistName,
                        sourceUrl = track.streamUrl,
                        targetKind = kind,
                        status = DownloadStatus.FAILED,
                        error = error.message,
                    ),
                )
                publishLocked()
            },
        )
    }

    private fun canDownloadNow(): Boolean {
        val settings = _state.value.settings
        if (!settings.wifiOnly) return true
        val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }

    private fun localFile(trackId: String): File? {
        val entry = index.get(trackId) ?: return null
        return File(mediaDir, entry.fileName)
    }

    private fun publishDownload(job: DownloadJob) {
        when (job.status) {
            DownloadStatus.COMPLETED, DownloadStatus.CANCELLED -> activeDownloads.remove(job.trackId)
            else -> activeDownloads[job.trackId] = job
        }
        // Keep a short history of failed jobs for the UI
        if (job.status == DownloadStatus.FAILED) {
            activeDownloads[job.trackId] = job
        }
        _state.update { current ->
            val downloads = activeDownloads.values
                .sortedByDescending {
                    when (it.status) {
                        DownloadStatus.DOWNLOADING -> 2
                        DownloadStatus.QUEUED -> 1
                        else -> 0
                    }
                }
                .take(30)
            current.copy(
                downloads = downloads,
                stats = current.stats.copy(
                    pendingDownloads = downloads.count {
                        it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.QUEUED
                    },
                ),
            )
        }
    }

    private fun publishLocked(settings: CacheSettings = _state.value.settings) {
        _state.value = buildUiState(settings, activeDownloads.values.toList())
    }

    private fun buildUiState(
        settings: CacheSettings,
        downloads: List<DownloadJob>,
    ): OfflineCacheUiState {
        val stats = index.stats().copy(
            pendingDownloads = downloads.count {
                it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.QUEUED
            },
        )
        return OfflineCacheUiState(
            settings = settings,
            stats = stats,
            entries = index.all(),
            downloads = downloads,
        )
    }

    private fun enforceLimitLocked(settings: CacheSettings = _state.value.settings) {
        if (!settings.rollingCacheEnabled) return
        index.enforceRollingLimit(settings.rollingCacheMaxBytes).forEach { entry ->
            File(mediaDir, entry.fileName).delete()
        }
    }

    private fun persistIndexLocked() {
        indexFile.parentFile?.mkdirs()
        indexFile.writeText(index.serialize())
    }

    private fun loadIndex(): CacheIndex {
        if (!indexFile.exists()) return CacheIndex()
        return runCatching { CacheIndex.deserialize(indexFile.readText()) }.getOrElse { CacheIndex() }
    }

    private fun persistSettings(settings: CacheSettings) {
        settingsFile.parentFile?.mkdirs()
        settingsFile.writeText(
            listOf(
                "playbackCacheEnabled=${settings.playbackCacheEnabled}",
                "rollingCacheEnabled=${settings.rollingCacheEnabled}",
                "permanentDownloadsEnabled=${settings.permanentDownloadsEnabled}",
                "rollingCacheMaxBytes=${settings.rollingCacheMaxBytes}",
                "playbackLookahead=${settings.playbackLookahead}",
                "wifiOnly=${settings.wifiOnly}",
                "autoCacheStarred=${settings.autoCacheStarred}",
                "autoCacheMinPlayCount=${settings.autoCacheMinPlayCount}",
            ).joinToString("\n"),
        )
    }

    private fun loadSettings(): CacheSettings {
        if (!settingsFile.exists()) return CacheSettings()
        val map = settingsFile.readLines()
            .mapNotNull { line ->
                val idx = line.indexOf('=')
                if (idx <= 0) null else line.substring(0, idx) to line.substring(idx + 1)
            }
            .toMap()
        val base = CacheSettings()
        return base.copy(
            playbackCacheEnabled = map["playbackCacheEnabled"]?.toBooleanStrictOrNull() ?: base.playbackCacheEnabled,
            rollingCacheEnabled = map["rollingCacheEnabled"]?.toBooleanStrictOrNull() ?: base.rollingCacheEnabled,
            permanentDownloadsEnabled = map["permanentDownloadsEnabled"]?.toBooleanStrictOrNull() ?: base.permanentDownloadsEnabled,
            rollingCacheMaxBytes = map["rollingCacheMaxBytes"]?.toLongOrNull() ?: base.rollingCacheMaxBytes,
            playbackLookahead = map["playbackLookahead"]?.toIntOrNull() ?: base.playbackLookahead,
            wifiOnly = map["wifiOnly"]?.toBooleanStrictOrNull() ?: base.wifiOnly,
            autoCacheStarred = map["autoCacheStarred"]?.toBooleanStrictOrNull() ?: base.autoCacheStarred,
            autoCacheMinPlayCount = map["autoCacheMinPlayCount"]?.toIntOrNull() ?: base.autoCacheMinPlayCount,
        )
    }

    private fun guessExtension(url: String, format: String?): String {
        format?.lowercase()?.let { if (it in KNOWN_EXTS) return it }
        val path = url.substringBefore('?').substringAfterLast('/')
        val ext = path.substringAfterLast('.', "")
        return if (ext.lowercase() in KNOWN_EXTS) ext.lowercase() else "mp3"
    }

    companion object {
        private const val DEFAULT_BUFFER = 64 * 1024
        private val KNOWN_EXTS = setOf("mp3", "flac", "m4a", "aac", "ogg", "opus", "wav", "wma")

        private fun defaultClient(): OkHttpClient =
            OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .followRedirects(true)
                .build()
    }
}
