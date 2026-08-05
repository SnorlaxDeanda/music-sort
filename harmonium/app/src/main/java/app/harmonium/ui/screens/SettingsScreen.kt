package app.harmonium.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.harmonium.data.cache.OfflineCacheUiState

@Composable
fun SettingsScreen(
    offlineCache: OfflineCacheUiState,
    onOpenEqualizer: () -> Unit,
    onOpenQueues: () -> Unit,
    onOpenProviders: () -> Unit,
    onOpenOfflineCache: () -> Unit,
    onTogglePlaybackCache: (Boolean) -> Unit,
) {
    var gapless by rememberSaveable { mutableStateOf(true) }
    var replayGain by rememberSaveable { mutableStateOf(true) }
    var smartFades by rememberSaveable { mutableStateOf(false) }
    var skipSilence by rememberSaveable { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 32.dp),
    ) {
        item {
            Text(
                "Settings",
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            )
        }
        item {
            SettingsLink("Equalizer & AutoEQ", "GEQ bands and headphone presets", onOpenEqualizer)
            SettingsLink("Media queues", "Switch music / audiobook queues", onOpenQueues)
            SettingsLink("Providers", "Servers, NAS, cloud, local", onOpenProviders)
            SettingsLink(
                title = "Offline cache",
                subtitle = "${formatBytes(offlineCache.stats.totalBytes)} · ${offlineCache.stats.totalCount} tracks cached",
                onClick = onOpenOfflineCache,
            )
        }
        item {
            Text(
                "Playback",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            )
            SettingsToggle("Gapless playback", gapless) { gapless = it }
            SettingsToggle("ReplayGain", replayGain) { replayGain = it }
            SettingsToggle("Smart fades", smartFades) { smartFades = it }
            SettingsToggle("Skip silence (audiobooks)", skipSilence) { skipSilence = it }
        }
        item {
            Text(
                "Offline",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            )
            SettingsToggle(
                title = "Playback cache",
                checked = offlineCache.settings.playbackCacheEnabled,
                onChecked = onTogglePlaybackCache,
            )
            Text(
                "Prefetches the current track and look-ahead into rolling cache. Open Offline cache for limits, pins, and downloads.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
        }
        item {
            Text(
                "About",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            )
            Text(
                "Harmonium 0.2.0 — an open-source, Symfonium-inspired Android music player. Not affiliated with Symfonium.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        }
    }
}

@Composable
private fun SettingsLink(title: String, subtitle: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(
            subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SettingsToggle(title: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}
