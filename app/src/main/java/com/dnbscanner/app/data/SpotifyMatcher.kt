package com.dnbscanner.app.data

import com.dnbscanner.app.model.MusicLink
import com.dnbscanner.app.model.Release
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.util.Base64

/**
 * Findet Releases auf Spotify:
 * 1. Spotify Web API (nur mit eigener Client-ID/Secret in den Einstellungen – schnell)
 * 2. song.link/Odesli: wandelt einen gefundenen YouTube-Music-Link in den Spotify-Link um (ohne Key, max. 10/Minute)
 * 3. sonst Spotify-Such-Link
 */
class SpotifyMatcher(private val http: Http, private val cache: LinkCache) {
    private val tokenLock = Mutex()
    private var token: String? = null
    private var tokenFor: String? = null
    private var tokenExpiry = 0L

    private val odesliLock = Mutex()
    private var lastOdesli = 0L

    fun cached(release: Release): MusicLink? = cache.get(release.key)

    /** Liefert null, wenn (noch) kein Abgleich möglich ist – z.B. ohne Zugangsdaten und ohne YT-Music-Treffer. */
    suspend fun match(release: Release, ytLink: MusicLink?, settings: Settings, force: Boolean = false): MusicLink? =
        withContext(Dispatchers.IO) {
            if (!force) cache.get(release.key)?.let { return@withContext it }
            val useApi = settings.hasSpotifyCredentials
            val useOdesli = ytLink?.direct == true
            if (!useApi && !useOdesli) return@withContext null

            var failure: Exception? = null
            var found: MusicLink? = null
            if (useApi) {
                try {
                    found = searchApi(release, settings)?.let { MusicLink(it.url, direct = true, matchedText = it.texts.joinToString(" · ")) }
                } catch (e: IOException) {
                    failure = e
                }
            }
            if (found == null && useOdesli) {
                try {
                    found = odesli(ytLink!!.url)?.let { MusicLink(it, direct = true) }
                } catch (e: IOException) {
                    failure = e
                }
            }
            if (found == null && failure != null) throw failure // nicht cachen, später erneut versuchen
            val link = found ?: MusicLink(searchUrl(release), direct = false)
            cache.put(release.key, link)
            link
        }

    private suspend fun searchApi(release: Release, settings: Settings): Candidate? {
        val bearer = token(settings)
        val q = URLEncoder.encode(YouTubeMusicMatcher.searchQuery(release), "UTF-8")
        val json = JSONObject(
            http.get(
                "https://api.spotify.com/v1/search?type=album,track&limit=5&market=DE&q=$q",
                mapOf("Authorization" to "Bearer $bearer"),
            ),
        )
        return bestMatch(parseSearch(json), release.artist, release.title)
    }

    private suspend fun token(settings: Settings): String = tokenLock.withLock {
        val cachedToken = token
        if (cachedToken != null && tokenFor == settings.spotifyClientId && System.currentTimeMillis() < tokenExpiry) {
            return@withLock cachedToken
        }
        val basic = Base64.getEncoder()
            .encodeToString("${settings.spotifyClientId}:${settings.spotifyClientSecret}".toByteArray())
        val json = JSONObject(
            http.postForm(
                "https://accounts.spotify.com/api/token",
                mapOf("grant_type" to "client_credentials"),
                mapOf("Authorization" to "Basic $basic"),
            ),
        )
        val fresh = json.getString("access_token")
        token = fresh
        tokenFor = settings.spotifyClientId
        tokenExpiry = System.currentTimeMillis() + (json.optLong("expires_in", 3600) - 60) * 1000
        fresh
    }

    private suspend fun odesli(sourceUrl: String): String? = odesliLock.withLock {
        val wait = lastOdesli + ODESLI_SPACING_MS - System.currentTimeMillis()
        if (wait > 0) delay(wait)
        try {
            val url = "https://api.song.link/v1-alpha.1/links?userCountry=DE&songIfSingle=true&url=" +
                URLEncoder.encode(sourceUrl, "UTF-8")
            parseOdesli(JSONObject(http.get(url, mapOf("User-Agent" to Http.APP_UA))))
        } catch (e: HttpStatusException) {
            if (e.code == 404 || e.code == 400) null else throw e
        } finally {
            lastOdesli = System.currentTimeMillis()
        }
    }

    data class Candidate(val name: String, val artists: List<String>, val url: String, val album: Boolean) {
        val texts: List<String> get() = listOf(name) + artists
    }

    companion object {
        private const val ODESLI_SPACING_MS = 6500L

        fun searchUrl(release: Release): String =
            "https://open.spotify.com/search/" +
                URLEncoder.encode(YouTubeMusicMatcher.searchQuery(release), "UTF-8").replace("+", "%20")

        fun parseSearch(json: JSONObject): List<Candidate> {
            val out = mutableListOf<Candidate>()
            for (type in listOf("albums", "tracks")) {
                val items = json.optJSONObject(type)?.optJSONArray("items") ?: continue
                for (i in 0 until items.length()) {
                    val item = items.optJSONObject(i) ?: continue
                    val url = item.optJSONObject("external_urls")?.optString("spotify").orEmpty()
                    val name = item.optString("name")
                    if (url.isBlank() || name.isBlank()) continue
                    val artists = item.optJSONArray("artists")?.let { a ->
                        (0 until a.length()).mapNotNull { a.optJSONObject(it)?.optString("name") }
                    } ?: emptyList()
                    out += Candidate(name, artists, url, album = type == "albums")
                }
            }
            return out
        }

        fun bestMatch(candidates: List<Candidate>, artist: String, title: String): Candidate? =
            candidates
                .map { it to Matching.score(it.texts, artist, title) }
                .filter { it.second > 0.0 }
                .maxByOrNull { (c, score) -> score + if (c.album) 0.05 else 0.0 }
                ?.first

        fun parseOdesli(json: JSONObject): String? =
            json.optJSONObject("linksByPlatform")?.optJSONObject("spotify")?.optString("url")?.takeIf { it.isNotBlank() }
    }
}
