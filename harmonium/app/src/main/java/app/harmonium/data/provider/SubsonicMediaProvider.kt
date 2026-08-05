package app.harmonium.data.provider

import app.harmonium.data.model.MediaProviderConfig
import app.harmonium.data.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * OpenSubsonic / Subsonic / Navidrome ping client.
 * Uses token auth (md5(password + salt)) when a password is supplied in [config.username]
 * via the conventional "user:password" encoding in username, or password field in baseUrl query.
 *
 * For the MVP, password is taken from [MediaProviderConfig] by splitting username as user|password
 * when a pipe is present; otherwise ping uses the public-friendly empty password path where allowed.
 */
class SubsonicMediaProvider(
    override val config: MediaProviderConfig,
    private val password: String = "",
    private val client: OkHttpClient = defaultClient(),
) : MediaProvider {

    override suspend fun testConnection(): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            require(config.baseUrl.isNotBlank()) { "Server URL required" }
            require(config.username.isNotBlank()) { "Username required" }
            val salt = UUID.randomUUID().toString().take(8)
            val token = md5(password + salt)
            val base = config.baseUrl.trimEnd('/').toHttpUrlOrNull()
                ?: error("Invalid server URL")
            val url = base.newBuilder()
                .addPathSegment("rest")
                .addPathSegment("ping.view")
                .addQueryParameter("u", config.username)
                .addQueryParameter("t", token)
                .addQueryParameter("s", salt)
                .addQueryParameter("v", "1.16.1")
                .addQueryParameter("c", "Harmonium")
                .addQueryParameter("f", "json")
                .build()
            val request = Request.Builder().url(url).get().build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("HTTP ${response.code}")
                val root = JSONObject(response.body?.string().orEmpty())
                    .getJSONObject("subsonic-response")
                val status = root.optString("status")
                if (status != "ok") {
                    val err = root.optJSONObject("error")
                    error(err?.optString("message") ?: "Subsonic ping failed")
                }
                val version = root.optString("version", "?")
                val type = root.optString("type", "Subsonic")
                "Connected to $type ($version)"
            }
        }
    }

    override suspend fun syncLibrary(): Result<ProviderLibrary> = withContext(Dispatchers.IO) {
        testConnection().map { ProviderLibrary() }
    }

    override suspend fun resolveStreamUrl(track: Track): Result<String> =
        Result.success(track.streamUrl)

    companion object {
        private fun defaultClient(): OkHttpClient =
            OkHttpClient.Builder()
                .connectTimeout(8, TimeUnit.SECONDS)
                .readTimeout(12, TimeUnit.SECONDS)
                .build()

        fun md5(value: String): String {
            val digest = MessageDigest.getInstance("MD5").digest(value.toByteArray())
            return digest.joinToString("") { "%02x".format(it) }
        }
    }
}
