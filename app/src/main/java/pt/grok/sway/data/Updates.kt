package pt.grok.sway.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import pt.grok.sway.BuildConfig
import java.net.HttpURLConnection
import java.net.URL

/** The latest published APK, as described by `downloads/sway.json` next to it on the web app. */
@Serializable
data class AppRelease(
    val versionCode: Int,
    val versionName: String,
    /** Absolute, or relative to the manifest URL. */
    val url: String = "sway.apk",
    val notes: String? = null,
)

/**
 * Checks the web app for a newer sideloaded APK; the browser handles downloading and installing it.
 * Only needed until the app is distributed through Google Play.
 */
class UpdateChecker(private val manifestUrl: String = BuildConfig.UPDATE_MANIFEST_URL) {
    private val json = Json { ignoreUnknownKeys = true }
    private val _available = MutableStateFlow<AppRelease?>(null)
    private val _prompt = MutableStateFlow<AppRelease?>(null)

    /** A release newer than the installed build, once [check] has found one. */
    val available: StateFlow<AppRelease?> = _available.asStateFlow()

    /** The release the update dialog is showing, until the user picks "Later". */
    val prompt: StateFlow<AppRelease?> = _prompt.asStateFlow()

    fun showPrompt() {
        _prompt.value = _available.value
    }

    fun dismissPrompt() {
        _prompt.value = null
    }

    suspend fun check(): AppRelease? = withContext(Dispatchers.IO) {
        val connection = URL(manifestUrl).openConnection() as HttpURLConnection
        val release = try {
            connection.connectTimeout = 15_000
            connection.readTimeout = 15_000
            connection.useCaches = false
            if (connection.responseCode !in 200..299) throw ApiException(connection.responseCode)
            json.decodeFromString(AppRelease.serializer(), connection.inputStream.bufferedReader().use { it.readText() })
        } finally {
            connection.disconnect()
        }
        release.takeIf { it.versionCode > BuildConfig.VERSION_CODE }
            ?.let { it.copy(url = URL(URL(manifestUrl), it.url).toString()) }
            .also {
                _available.value = it
                _prompt.value = it
            }
    }
}
