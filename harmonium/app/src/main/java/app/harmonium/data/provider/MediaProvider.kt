package app.harmonium.data.provider

import app.harmonium.data.model.Album
import app.harmonium.data.model.Artist
import app.harmonium.data.model.MediaProviderConfig
import app.harmonium.data.model.Playlist
import app.harmonium.data.model.Track

interface MediaProvider {
    val config: MediaProviderConfig

    suspend fun testConnection(): Result<String>

    suspend fun syncLibrary(): Result<ProviderLibrary>

    suspend fun resolveStreamUrl(track: Track): Result<String>
}

data class ProviderLibrary(
    val artists: List<Artist> = emptyList(),
    val albums: List<Album> = emptyList(),
    val tracks: List<Track> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
)
