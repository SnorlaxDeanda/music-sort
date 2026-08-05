package app.harmonium.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import app.harmonium.data.model.Playlist
import app.harmonium.ui.components.AlbumTile
import app.harmonium.ui.components.SectionHeader
import app.harmonium.ui.components.TrackRow
import app.harmonium.viewmodel.HomeUiModel

@Composable
fun HomeScreen(
    home: HomeUiModel,
    onAlbum: (String) -> Unit,
    onPlayTrack: (String) -> Unit,
    onPlayPlaylist: (Playlist) -> Unit,
    onOpenAudiobooks: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.28f),
                                MaterialTheme.colorScheme.background,
                            ),
                        ),
                    )
                    .padding(horizontal = 20.dp, vertical = 28.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Harmonium", style = MaterialTheme.typography.displayLarge)
                    Text(
                        "All your music. One player.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item { SectionHeader("Continue listening", "Pick up where you left off") }
        items(home.continueTracks, key = { it.id }) { track ->
            TrackRow(track = track, onClick = { onPlayTrack(track.id) })
        }

        item {
            Spacer(Modifier.height(8.dp))
            SectionHeader("Personal mixes", "Built from your listening habits")
            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                home.mixes.forEach { mix ->
                    MixChip(name = mix.name, onClick = { onPlayPlaylist(mix) })
                }
            }
        }

        item {
            Spacer(Modifier.height(16.dp))
            SectionHeader("Albums", "Browse the library")
            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                home.recentAlbums.forEach { album ->
                    AlbumTile(album = album, onClick = { onAlbum(album.id) })
                }
            }
        }

        item {
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    SectionHeader("Top tracks")
                }
                TextButton(onClick = onOpenAudiobooks) { Text("Audiobooks") }
            }
        }
        items(home.topTracks, key = { "top-${it.id}" }) { track ->
            TrackRow(track = track, onClick = { onPlayTrack(track.id) })
        }
    }
}

@Composable
private fun MixChip(name: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .width(180.dp)
            .height(88.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        MaterialTheme.colorScheme.secondary.copy(alpha = 0.35f),
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                    ),
                ),
            )
            .clickable(onClick = onClick)
            .padding(14.dp),
        contentAlignment = Alignment.BottomStart,
    ) {
        Text(name, style = MaterialTheme.typography.titleMedium)
    }
}
