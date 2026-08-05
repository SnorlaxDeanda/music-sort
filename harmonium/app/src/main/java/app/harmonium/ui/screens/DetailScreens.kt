package app.harmonium.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.harmonium.data.model.Album
import app.harmonium.data.model.Artist
import app.harmonium.data.model.LibrarySnapshot
import app.harmonium.data.model.MediaKind
import app.harmonium.data.model.PlaybackQueue
import app.harmonium.data.model.Track
import app.harmonium.ui.components.CoverArt
import app.harmonium.ui.components.EmptyState
import app.harmonium.ui.components.SectionHeader
import app.harmonium.ui.components.TrackRow

@Composable
fun AlbumScreen(
    album: Album?,
    tracks: List<Track>,
    isCached: (String) -> Boolean,
    onBack: () -> Unit,
    onPlayAlbum: () -> Unit,
    onDownloadAlbum: () -> Unit,
    onPlayTrack: (Track) -> Unit,
    onDownloadTrack: (Track) -> Unit,
) {
    if (album == null) {
        EmptyState("Album missing", "This album is no longer in the library.")
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 32.dp),
    ) {
        item {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            CoverArt(
                title = album.title,
                seed = album.id.hashCode(),
                modifier = Modifier
                    .padding(horizontal = 48.dp)
                    .fillMaxWidth()
                    .aspectRatio(1f),
            )
            Spacer(Modifier.height(16.dp))
            Text(
                album.title,
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            Text(
                "${album.artistName} · ${album.year ?: "Year unknown"}",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            Spacer(Modifier.height(12.dp))
            Row(modifier = Modifier.padding(horizontal = 20.dp)) {
                Button(onClick = onPlayAlbum) { Text("Play album") }
                Spacer(Modifier.width(10.dp))
                OutlinedButton(onClick = onDownloadAlbum) { Text("Download") }
            }
            Spacer(Modifier.height(8.dp))
        }
        items(tracks, key = { it.id }) { track ->
            TrackRow(
                track = track,
                onClick = { onPlayTrack(track) },
                offline = isCached(track.id),
                onDownload = { onDownloadTrack(track) },
            )
        }
    }
}

@Composable
fun ArtistScreen(
    artist: Artist?,
    albums: List<Album>,
    tracks: List<Track>,
    onBack: () -> Unit,
    onAlbum: (String) -> Unit,
    onPlayTrack: (Track) -> Unit,
) {
    if (artist == null) {
        EmptyState("Artist missing", "This artist is no longer in the library.")
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 32.dp),
    ) {
        item {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text(
                artist.name,
                style = MaterialTheme.typography.displayLarge,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            artist.biography?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
            }
            SectionHeader("Albums")
        }
        items(albums, key = { it.id }) { album ->
            TrackRow(
                track = Track(
                    id = album.id,
                    title = album.title,
                    albumId = album.id,
                    albumTitle = album.title,
                    artistId = artist.id,
                    artistName = artist.name,
                    providerId = album.providerId,
                    durationMs = 0,
                    streamUrl = "",
                    year = album.year,
                ),
                onClick = { onAlbum(album.id) },
                trailing = album.year?.toString(),
            )
        }
        item { SectionHeader("Top tracks") }
        items(tracks.take(8), key = { "at-${it.id}" }) { track ->
            TrackRow(track, onClick = { onPlayTrack(track) })
        }
    }
}

@Composable
fun AudiobooksScreen(
    library: LibrarySnapshot,
    onPlayTrack: (Track) -> Unit,
    onPlayAlbum: (String) -> Unit,
) {
    val books = library.albums.filter { it.kind == MediaKind.AUDIOBOOK }
    val chapters = library.tracks.filter { it.kind == MediaKind.AUDIOBOOK }
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
        item {
            Text(
                "Audiobooks",
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            )
            Text(
                "Separate queue, playback speed, and resume points — dedicated listening mode.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        }
        items(books, key = { it.id }) { book ->
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)) {
                Text(book.title, style = MaterialTheme.typography.titleLarge)
                Text(
                    book.artistName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(onClick = { onPlayAlbum(book.id) }) { Text("Resume / play") }
            }
        }
        item { SectionHeader("Chapters") }
        items(chapters, key = { it.id }) { chapter ->
            TrackRow(chapter, onClick = { onPlayTrack(chapter) })
        }
    }
}

@Composable
fun QueuesScreen(
    queues: List<PlaybackQueue>,
    activeId: String,
    onSelect: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
        Text("Media queues", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            "Keep music and audiobooks (or playlists) in separate now-playing queues with their own speed, shuffle, and position.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        queues.forEach { queue ->
            Column(modifier = Modifier.padding(vertical = 10.dp)) {
                Text(queue.name, style = MaterialTheme.typography.titleLarge)
                Text(
                    "${queue.trackIds.size} tracks · ${queue.kind.name.lowercase()} · ${queue.playbackSpeed}x",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    onClick = { onSelect(queue.id) },
                    enabled = queue.id != activeId,
                ) {
                    Text(if (queue.id == activeId) "Active" else "Switch to this queue")
                }
            }
        }
    }
}
