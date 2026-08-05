package app.harmonium.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.harmonium.data.model.Track
import app.harmonium.data.repository.LibraryRepository
import app.harmonium.ui.components.EmptyState
import app.harmonium.ui.components.SectionHeader
import app.harmonium.ui.components.TrackRow

@Composable
fun SearchScreen(
    query: String,
    results: LibraryRepository.SearchResults,
    onQueryChange: (String) -> Unit,
    onAlbum: (String) -> Unit,
    onArtist: (String) -> Unit,
    onPlayTrack: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            "Search",
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
        )
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            placeholder = { Text("Songs, albums, artists, playlists") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
        )
        if (query.isBlank()) {
            EmptyState("Find anything", "Search across every connected provider.")
        } else {
            LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                if (results.tracks.isNotEmpty()) {
                    item { SectionHeader("Tracks") }
                    items(results.tracks, key = { it.id }) { track ->
                        TrackRow(track, onClick = { onPlayTrack(track.id) })
                    }
                }
                if (results.albums.isNotEmpty()) {
                    item { SectionHeader("Albums") }
                    items(results.albums, key = { it.id }) { album ->
                        TrackRow(
                            track = Track(
                                id = album.id,
                                title = album.title,
                                albumId = album.id,
                                albumTitle = album.title,
                                artistId = album.artistId,
                                artistName = album.artistName,
                                providerId = album.providerId,
                                durationMs = 0,
                                streamUrl = "",
                            ),
                            onClick = { onAlbum(album.id) },
                            trailing = album.year?.toString(),
                        )
                    }
                }
                if (results.artists.isNotEmpty()) {
                    item { SectionHeader("Artists") }
                    items(results.artists, key = { it.id }) { artist ->
                        Text(
                            artist.name,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onArtist(artist.id) }
                                .padding(horizontal = 20.dp, vertical = 12.dp),
                        )
                    }
                }
                if (results.tracks.isEmpty() && results.albums.isEmpty() && results.artists.isEmpty()) {
                    item { EmptyState("No matches", "Try another spelling or artist name.") }
                }
            }
        }
    }
}
