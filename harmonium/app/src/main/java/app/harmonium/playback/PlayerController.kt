package app.harmonium.playback

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import app.harmonium.data.cache.OfflineCacheRepository
import app.harmonium.data.model.MediaKind
import app.harmonium.data.model.PlaybackQueue
import app.harmonium.data.model.RepeatMode
import app.harmonium.data.model.Track
import app.harmonium.data.repository.LibraryRepository
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class PlayerUiState(
    val current: Track? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val queue: List<Track> = emptyList(),
    val queueName: String = "Music",
    val shuffle: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val playbackSpeed: Float = 1f,
    val kind: MediaKind = MediaKind.MUSIC,
    val currentIsOffline: Boolean = false,
)

class PlayerController(
    context: Context,
    private val repository: LibraryRepository,
    private val offlineCache: OfflineCacheRepository,
) {
    private val appContext = context.applicationContext
    private var controller: MediaController? = null

    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _state.update { it.copy(isPlaying = isPlaying) }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val trackId = mediaItem?.mediaId
            val track = trackId?.let { repository.trackById(it) }
            _state.update {
                it.copy(
                    current = track,
                    durationMs = track?.durationMs ?: controller?.duration?.coerceAtLeast(0) ?: 0,
                    currentIsOffline = trackId?.let { offlineCache.isCached(it) } == true,
                )
            }
            persistQueuePosition()
            maybeWarmCache()
            track?.let { offlineCache.autoCacheIfNeeded(it) }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            val duration = controller?.duration ?: 0
            if (duration > 0) {
                _state.update { it.copy(durationMs = duration) }
            }
        }
    }

    fun connect() {
        if (controller != null) return
        val token = SessionToken(
            appContext,
            ComponentName(appContext, PlaybackService::class.java),
        )
        val future = MediaController.Builder(appContext, token).buildAsync()
        future.addListener(
            {
                controller = future.get().also { c ->
                    c.addListener(listener)
                    syncFromController(c)
                }
            },
            MoreExecutors.directExecutor(),
        )
    }

    fun release() {
        controller?.removeListener(listener)
        controller?.release()
        controller = null
    }

    fun playTracks(
        tracks: List<Track>,
        startIndex: Int = 0,
        queueName: String? = null,
        kind: MediaKind = tracks.firstOrNull()?.kind ?: MediaKind.MUSIC,
    ) {
        if (tracks.isEmpty()) return
        val c = controller ?: return
        val targetQueueId = if (kind == MediaKind.AUDIOBOOK) "q-books" else "q-music"
        repository.setActiveQueue(targetQueueId)
        val existing = repository.queues.value.first { it.id == targetQueueId }
        val updated = existing.copy(
            name = queueName ?: existing.name,
            trackIds = tracks.map { it.id },
            currentIndex = startIndex.coerceIn(0, tracks.lastIndex),
            kind = kind,
            positionMs = 0L,
        )
        repository.updateQueue(updated)

        val items = tracks.map { it.toMediaItem() }
        val safeIndex = startIndex.coerceIn(0, items.lastIndex)
        c.setMediaItems(items, safeIndex, 0L)
        c.prepare()
        c.play()
        val current = tracks.getOrNull(safeIndex)
        _state.update {
            it.copy(
                queue = tracks,
                queueName = updated.name,
                current = current,
                kind = kind,
                playbackSpeed = updated.playbackSpeed,
                shuffle = updated.shuffle,
                repeatMode = updated.repeatMode,
                currentIsOffline = current?.let { track -> offlineCache.isCached(track.id) } == true,
            )
        }
        c.setPlaybackSpeed(updated.playbackSpeed)
        applyRepeat(updated.repeatMode)
        c.shuffleModeEnabled = updated.shuffle
        offlineCache.ensurePlaybackCache(tracks, safeIndex)
        current?.let { offlineCache.autoCacheIfNeeded(it) }
    }

    fun playPause() {
        val c = controller ?: return
        if (c.isPlaying) c.pause() else c.play()
    }

    fun skipNext() = controller?.seekToNextMediaItem()

    fun skipPrevious() = controller?.seekToPreviousMediaItem()

    fun seekTo(positionMs: Long) {
        controller?.seekTo(positionMs)
        _state.update { it.copy(positionMs = positionMs) }
    }

    fun toggleShuffle() {
        val c = controller ?: return
        val enabled = !c.shuffleModeEnabled
        c.shuffleModeEnabled = enabled
        _state.update { it.copy(shuffle = enabled) }
        mutateActiveQueue { it.copy(shuffle = enabled) }
    }

    fun cycleRepeat() {
        val next = when (_state.value.repeatMode) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
        applyRepeat(next)
        _state.update { it.copy(repeatMode = next) }
        mutateActiveQueue { it.copy(repeatMode = next) }
    }

    fun setSpeed(speed: Float) {
        val clamped = speed.coerceIn(0.5f, 3f)
        controller?.setPlaybackSpeed(clamped)
        _state.update { it.copy(playbackSpeed = clamped) }
        mutateActiveQueue { it.copy(playbackSpeed = clamped) }
    }

    fun pollPosition() {
        val c = controller ?: return
        if (c.duration > 0 || c.currentPosition >= 0) {
            _state.update {
                it.copy(
                    positionMs = c.currentPosition.coerceAtLeast(0),
                    durationMs = c.duration.coerceAtLeast(it.durationMs),
                    isPlaying = c.isPlaying,
                )
            }
        }
    }

    private fun maybeWarmCache() {
        val queue = _state.value.queue
        if (queue.isEmpty()) return
        val index = controller?.currentMediaItemIndex?.coerceAtLeast(0) ?: return
        offlineCache.ensurePlaybackCache(queue, index)
    }

    private fun syncFromController(c: MediaController) {
        val track = c.currentMediaItem?.mediaId?.let { repository.trackById(it) }
        val queue = buildList {
            for (i in 0 until c.mediaItemCount) {
                val id = c.getMediaItemAt(i).mediaId
                repository.trackById(id)?.let { add(it) }
            }
        }
        _state.value = PlayerUiState(
            current = track,
            isPlaying = c.isPlaying,
            positionMs = c.currentPosition.coerceAtLeast(0),
            durationMs = c.duration.coerceAtLeast(0),
            queue = queue,
            shuffle = c.shuffleModeEnabled,
            repeatMode = when (c.repeatMode) {
                Player.REPEAT_MODE_ONE -> RepeatMode.ONE
                Player.REPEAT_MODE_ALL -> RepeatMode.ALL
                else -> RepeatMode.OFF
            },
            playbackSpeed = c.playbackParameters.speed,
            kind = track?.kind ?: MediaKind.MUSIC,
            currentIsOffline = track?.let { offlineCache.isCached(it.id) } == true,
        )
    }

    private fun applyRepeat(mode: RepeatMode) {
        controller?.repeatMode = when (mode) {
            RepeatMode.OFF -> Player.REPEAT_MODE_OFF
            RepeatMode.ALL -> Player.REPEAT_MODE_ALL
            RepeatMode.ONE -> Player.REPEAT_MODE_ONE
        }
    }

    private fun mutateActiveQueue(transform: (PlaybackQueue) -> PlaybackQueue) {
        val id = repository.activeQueueId.value
        val queue = repository.queues.value.find { it.id == id } ?: return
        repository.updateQueue(transform(queue))
    }

    private fun persistQueuePosition() {
        val c = controller ?: return
        mutateActiveQueue {
            it.copy(
                currentIndex = c.currentMediaItemIndex.coerceAtLeast(0),
                positionMs = c.currentPosition.coerceAtLeast(0),
            )
        }
    }

    private fun Track.toMediaItem(): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artistName)
            .setAlbumTitle(albumTitle)
            .build()
        return MediaItem.Builder()
            .setMediaId(id)
            .setUri(offlineCache.resolvePlaybackUri(this))
            .setMediaMetadata(metadata)
            .build()
    }
}
