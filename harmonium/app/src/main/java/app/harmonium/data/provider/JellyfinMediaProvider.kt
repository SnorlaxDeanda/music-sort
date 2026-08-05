package app.harmonium.data.provider

import app.harmonium.data.model.MediaProviderConfig
import app.harmonium.data.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Minimal Jellyfin System/Info + Items probe.
 * Full library sync can be expanded against the Jellyfin REST API.
 */
class JellyfinMediaProvider(
    override val config: MediaProviderConfig,
    private val client: OkHttpClient = defaultClient(),
) : MediaProvider {

    override suspend fun testConnection(): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            require(config.baseUrl.isNotBlank()) { "Server URL required" }
            val url = config.baseUrl.trimEnd('/') + "/System/Info/Public"
            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("HTTP ${response.code}")
                val body = response.body?.string().orEmpty()
                val json = JSONObject(body)
                val name = json.optString("ServerName", "Jellyfin")
                val version = json.optString("Version", "?")
                "Connected to $name (Jellyfin $version)"
            }
        }
    }

    override suspend fun syncLibrary(): Result<ProviderLibrary> = withContext(Dispatchers.IO) {
        // Connection-validated stub: returns empty until credentials/user library fetch is configured.
        testConnection().map {
            ProviderLibrary()
        }
    }

    override suspend fun resolveStreamUrl(track: Track): Result<String> =
        Result.success(track.streamUrl)

    companion object {
        private fun defaultClient(): OkHttpClient =
            OkHttpClient.Builder()
                .connectTimeout(8, TimeUnit.SECONDS)
                .readTimeout(12, TimeUnit.SECONDS)
                .build()
    }
}
