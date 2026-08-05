package app.harmonium.data.provider

import app.harmonium.data.demo.DemoLibrary
import app.harmonium.data.model.MediaProviderConfig
import app.harmonium.data.model.Track
import kotlinx.coroutines.delay

class DemoMediaProvider(
    override val config: MediaProviderConfig,
) : MediaProvider {
    override suspend fun testConnection(): Result<String> {
        delay(200)
        return Result.success("Demo library ready (${DemoLibrary.snapshot().tracks.size} tracks)")
    }

    override suspend fun syncLibrary(): Result<ProviderLibrary> {
        delay(350)
        val snap = DemoLibrary.snapshot()
        return Result.success(
            ProviderLibrary(
                artists = snap.artists,
                albums = snap.albums,
                tracks = snap.tracks,
                playlists = snap.playlists,
            ),
        )
    }

    override suspend fun resolveStreamUrl(track: Track): Result<String> =
        Result.success(track.streamUrl)
}
