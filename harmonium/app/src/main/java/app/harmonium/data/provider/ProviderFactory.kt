package app.harmonium.data.provider

import app.harmonium.data.model.MediaProviderConfig
import app.harmonium.data.model.ProviderType

object ProviderFactory {
    fun create(config: MediaProviderConfig, password: String = ""): MediaProvider =
        when (config.type) {
            ProviderType.DEMO, ProviderType.LOCAL -> DemoMediaProvider(config)
            ProviderType.JELLYFIN, ProviderType.EMBY, ProviderType.AUDIOBOOKSHELF ->
                JellyfinMediaProvider(config)
            ProviderType.SUBSONIC,
            ProviderType.OPENSUBSONIC,
            ProviderType.NAVIDROME,
            -> SubsonicMediaProvider(config, password = password)
            ProviderType.PLEX,
            ProviderType.SMB,
            ProviderType.WEBDAV,
            -> StubMediaProvider(config)
        }
}

/** Placeholder for providers not yet fully wired. */
class StubMediaProvider(
    override val config: MediaProviderConfig,
) : MediaProvider {
    override suspend fun testConnection(): Result<String> =
        Result.failure(UnsupportedOperationException("${config.type} connection UI is ready; sync lands next."))

    override suspend fun syncLibrary(): Result<ProviderLibrary> =
        Result.failure(UnsupportedOperationException("${config.type} sync not implemented yet"))

    override suspend fun resolveStreamUrl(track: app.harmonium.data.model.Track): Result<String> =
        Result.failure(UnsupportedOperationException("No stream resolver for ${config.type}"))
}
