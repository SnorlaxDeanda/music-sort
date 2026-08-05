package app.harmonium.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.harmonium.data.cache.CacheEntry
import app.harmonium.data.cache.CacheKind
import app.harmonium.data.cache.CacheSettings
import app.harmonium.data.cache.DownloadJob
import app.harmonium.data.cache.DownloadStatus
import app.harmonium.data.cache.OfflineCacheUiState

private val SIZE_OPTIONS = listOf(
    256L * 1024 * 1024 to "256 MB",
    512L * 1024 * 1024 to "512 MB",
    1024L * 1024 * 1024 to "1 GB",
    2048L * 1024 * 1024 to "2 GB",
    4096L * 1024 * 1024 to "4 GB",
)

@Composable
fun OfflineCacheScreen(
    state: OfflineCacheUiState,
    onBack: () -> Unit,
    onUpdateSettings: ((CacheSettings) -> CacheSettings) -> Unit,
    onRemove: (String) -> Unit,
    onPin: (String) -> Unit,
    onUnpin: (String) -> Unit,
    onClearRolling: () -> Unit,
    onClearAll: () -> Unit,
) {
    val settings = state.settings
    val stats = state.stats

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 32.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Text("Offline cache", style = MaterialTheme.typography.headlineLarge)
            }
            Text(
                "Playback cache, rolling LRU, and pinned downloads — listen without a connection.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            Spacer(Modifier.height(12.dp))
        }

        item {
            Column(Modifier.padding(horizontal = 20.dp)) {
                Text("Storage", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(6.dp))
                Text(
                    "${formatBytes(stats.totalBytes)} used · ${stats.totalCount} tracks",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    "Pinned ${formatBytes(stats.permanentBytes)} · Rolling ${formatBytes(stats.rollingBytes + stats.playbackBytes)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (stats.pendingDownloads > 0) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "${stats.pendingDownloads} download(s) in progress",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onClearRolling) { Text("Clear rolling") }
                    Button(onClick = onClearAll) { Text("Clear all") }
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        item {
            Text(
                "Settings",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            CacheToggle(
                title = "Playback cache",
                subtitle = "Prefetch current + next tracks while listening",
                checked = settings.playbackCacheEnabled,
                onChecked = { enabled ->
                    onUpdateSettings { it.copy(playbackCacheEnabled = enabled) }
                },
            )
            CacheToggle(
                title = "Rolling cache",
                subtitle = "Keep recently played audio up to the size limit",
                checked = settings.rollingCacheEnabled,
                onChecked = { enabled ->
                    onUpdateSettings { it.copy(rollingCacheEnabled = enabled) }
                },
            )
            CacheToggle(
                title = "Wi‑Fi only downloads",
                subtitle = "Block cellular for new cache fills",
                checked = settings.wifiOnly,
                onChecked = { enabled ->
                    onUpdateSettings { it.copy(wifiOnly = enabled) }
                },
            )
            CacheToggle(
                title = "Auto-cache favorites",
                subtitle = "Pin-worthy 4.5★+ tracks into rolling cache",
                checked = settings.autoCacheStarred,
                onChecked = { enabled ->
                    onUpdateSettings { it.copy(autoCacheStarred = enabled) }
                },
            )
            Text(
                "Rolling size limit",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            Row(
                Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SIZE_OPTIONS.forEach { (bytes, label) ->
                    FilterChip(
                        selected = settings.rollingCacheMaxBytes == bytes,
                        onClick = { onUpdateSettings { it.copy(rollingCacheMaxBytes = bytes) } },
                        label = { Text(label) },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        if (state.downloads.any { it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.FAILED }) {
            item {
                Text(
                    "Downloads",
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
            }
            items(
                state.downloads.filter {
                    it.status == DownloadStatus.DOWNLOADING ||
                        it.status == DownloadStatus.QUEUED ||
                        it.status == DownloadStatus.FAILED
                },
                key = { "dl-${it.trackId}-${it.status}" },
            ) { job ->
                DownloadRow(job)
            }
        }

        item {
            Text(
                "Cached tracks",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            )
            if (state.entries.isEmpty()) {
                Text(
                    "Nothing cached yet. Play music or tap Download on a track/album.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
            }
        }

        items(state.entries, key = { it.trackId }) { entry ->
            CacheEntryRow(
                entry = entry,
                onRemove = { onRemove(entry.trackId) },
                onPin = { onPin(entry.trackId) },
                onUnpin = { onUnpin(entry.trackId) },
            )
        }
    }
}

@Composable
private fun CacheToggle(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChecked: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}

@Composable
private fun DownloadRow(job: DownloadJob) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
        Text(job.title, style = MaterialTheme.typography.titleMedium)
        Text(
            when (job.status) {
                DownloadStatus.DOWNLOADING -> "Downloading ${(job.progress * 100).toInt()}%"
                DownloadStatus.QUEUED -> "Queued"
                DownloadStatus.FAILED -> job.error ?: "Failed"
                else -> job.status.name
            },
            style = MaterialTheme.typography.bodyMedium,
            color = if (job.status == DownloadStatus.FAILED) {
                MaterialTheme.colorScheme.tertiary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
        if (job.status == DownloadStatus.DOWNLOADING) {
            Spacer(Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { job.progress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun CacheEntryRow(
    entry: CacheEntry,
    onRemove: () -> Unit,
    onPin: () -> Unit,
    onUnpin: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (entry.pinned) Icons.Default.PushPin else Icons.Default.DownloadDone,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Column(
            Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
        ) {
            Text(entry.title, style = MaterialTheme.typography.titleMedium)
            Text(
                "${entry.artistName} · ${kindLabel(entry.kind)} · ${formatBytes(entry.bytes)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (entry.pinned) {
            TextButton(onClick = onUnpin) { Text("Unpin") }
        } else {
            TextButton(onClick = onPin) { Text("Pin") }
        }
        IconButton(onClick = onRemove) {
            Icon(Icons.Default.Delete, contentDescription = "Remove")
        }
    }
}

private fun kindLabel(kind: CacheKind): String = when (kind) {
    CacheKind.PERMANENT -> "Pinned"
    CacheKind.ROLLING -> "Rolling"
    CacheKind.PLAYBACK -> "Playback"
}

fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return "%.1f KB".format(kb)
    val mb = kb / 1024.0
    if (mb < 1024) return "%.1f MB".format(mb)
    val gb = mb / 1024.0
    return "%.2f GB".format(gb)
}
