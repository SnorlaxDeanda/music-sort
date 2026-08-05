package app.harmonium.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.harmonium.data.model.LibrarySnapshot
import app.harmonium.data.model.MediaKind
import app.harmonium.data.model.Playlist
import app.harmonium.ui.components.AlbumTile
import app.harmonium.ui.components.CoverArt
import app.harmonium.ui.components.TrackRow

private val tabs = listOf("Albums", "Artists", "Tracks", "Playlists", "Genres")

@Composable
fun LibraryScreen(
    library: LibrarySnapshot,
    onAlbum: (String) -> Unit,
    onArtist: (String) -> Unit,
    onPlayTrack: (String) -> Unit,
    onPlayPlaylist: (Playlist) -> Unit,
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            "Library",
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
        )
        ScrollableTabRow(selectedTabIndex = tab, edgePadding = 16.dp) {
            tabs.forEachIndexed { index, label ->
                Tab(
                    selected = tab == index,
                    onClick = { tab = index },
                    text = { Text(label) },
                )
            }
        }
        when (tab) {
            0 -> LazyVerticalGrid(
                columns = GridCells.Adaptive(150.dp),
                contentPadding = PaddingValues(16.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(library.albums.filter { it.kind == MediaKind.MUSIC }, key = { it.id }) { album ->
                    AlbumTile(
                        album = album,
                        onClick = { onAlbum(album.id) },
                        modifier = Modifier.padding(8.dp),
                    )
                }
            }
            1 -> LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                items(library.artists, key = { it.id }) { artist ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onArtist(artist.id) }
                            .padding(horizontal = 20.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CoverArt(artist.name, modifier = Modifier.size(52.dp), seed = artist.id.hashCode())
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(artist.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${artist.albumCount} albums",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            2 -> LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                items(library.tracks.filter { it.kind == MediaKind.MUSIC }, key = { it.id }) { track ->
                    TrackRow(track, onClick = { onPlayTrack(track.id) })
                }
            }
            3 -> LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                items(library.playlists, key = { it.id }) { playlist ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPlayPlaylist(playlist) }
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CoverArt(playlist.name, modifier = Modifier.size(52.dp), seed = playlist.id.hashCode())
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(playlist.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                buildString {
                                    append("${playlist.trackIds.size} tracks")
                                    if (playlist.isSmart) append(" · Smart")
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            4 -> {
                val genres = library.tracks.mapNotNull { it.genre }.distinct().sorted()
                LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                    items(genres, key = { it }) { genre ->
                        val count = library.tracks.count { it.genre == genre }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(genre, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            Text(
                                "$count",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}
