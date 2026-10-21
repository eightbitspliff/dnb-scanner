package com.dnbscanner.app.data

import com.dnbscanner.app.model.Release
import com.dnbscanner.app.model.MusicLink
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

/**
 * Sucht ein Release in YouTube Music (Web-Client-API, kein API-Key nötig) und liefert
 * einen direkten Album- bzw. Song-Link. Ohne Treffer gibt es einen Such-Link.
 */
class YouTubeMusicMatcher(private val http: Http, private val cache: LinkCache) {

    fun cached(release: Release): MusicLink? = cache.get(release.key)

    suspend fun match(release: Release, force: Boolean = false): MusicLink = withContext(Dispatchers.IO) {
        if (!force) cache.get(release.key)?.let { return@withContext it }
        val query = searchQuery(release)
        var found: Candidate? = null
        for (filter in listOf(FILTER_ALBUMS, FILTER_SONGS)) {
            val json = runCatching { JSONObject(search(query, filter)) }.getOrNull() ?: continue
            found = bestMatch(parseCandidates(json), release.artist, release.title)
            if (found != null) break
        }
        val link = found?.let { MusicLink(it.url, direct = true, matchedText = it.texts.take(3).joinToString(" · ")) }
            ?: MusicLink(searchUrl(release), direct = false)
        cache.put(release.key, link)
        link
    }

    private fun search(query: String, params: String): String {
        val body = JSONObject()
            .put(
                "context", JSONObject().put(
                    "client", JSONObject()
                        .put("clientName", "WEB_REMIX")
                        .put("clientVersion", CLIENT_VERSION)
                        .put("hl", "de")
                        .put("gl", "DE"),
                ),
            )
            .put("query", query)
            .put("params", params)
        return http.postJson(
            "https://music.youtube.com/youtubei/v1/search?prettyPrint=false",
            body.toString(),
            mapOf(
                "User-Agent" to Http.BROWSER_UA,
                "Origin" to "https://music.youtube.com",
                "Referer" to "https://music.youtube.com/",
                "X-YouTube-Client-Name" to "67",
                "X-YouTube-Client-Version" to CLIENT_VERSION,
            ),
        )
    }

    data class Candidate(val texts: List<String>, val browseId: String?, val videoId: String?) {
        val url: String
            get() = if (browseId != null) "https://music.youtube.com/browse/$browseId"
            else "https://music.youtube.com/watch?v=$videoId"
    }

    companion object {
        private const val CLIENT_VERSION = "1.20250929.01.00"
        private const val FILTER_ALBUMS = "EgWKAQIYAWoMEA4QChADEAQQCRAF"
        private const val FILTER_SONGS = "EgWKAQIIAWoMEA4QChADEAQQCRAF"

        fun searchQuery(release: Release): String {
            val title = release.title.replace(Regex("[\\(\\[][^\\)\\]]*[\\)\\]]"), " ").trim()
            return "${release.artist} $title".replace(Regex("\\s+"), " ")
        }

        fun searchUrl(release: Release): String =
            "https://music.youtube.com/search?q=" + URLEncoder.encode(searchQuery(release), "UTF-8")

        fun parseCandidates(json: JSONObject): List<Candidate> {
            val renderers = mutableListOf<JSONObject>()
            collectRenderers(json, renderers)
            return renderers.mapNotNull { r ->
                val texts = mutableListOf<String>()
                collectStrings(r.opt("flexColumns"), "text", texts)
                val ids = mutableListOf<String>()
                collectStrings(r, "browseId", ids)
                val browseId = ids.firstOrNull { it.startsWith("MPREb_") }
                val videos = mutableListOf<String>()
                collectStrings(r, "videoId", videos)
                val videoId = videos.firstOrNull()
                if (texts.isEmpty() || (browseId == null && videoId == null)) null
                else Candidate(texts.filter { it.isNotBlank() && it != " • " }, browseId, videoId)
            }
        }

        fun bestMatch(candidates: List<Candidate>, artist: String, title: String): Candidate? =
            candidates
                .map { it to Matching.score(it.texts, artist, title) }
                .filter { it.second > 0.0 }
                .maxByOrNull { (c, score) -> score + if (c.browseId != null) 0.05 else 0.0 }
                ?.first

        private fun collectRenderers(node: Any?, out: MutableList<JSONObject>) {
            when (node) {
                is JSONObject -> {
                    val keys = node.keys()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        val v = node.opt(k)
                        if (k == "musicResponsiveListItemRenderer" && v is JSONObject) out += v
                        else collectRenderers(v, out)
                    }
                }
                is JSONArray -> for (i in 0 until node.length()) collectRenderers(node.opt(i), out)
            }
        }

        private fun collectStrings(node: Any?, key: String, out: MutableList<String>) {
            when (node) {
                is JSONObject -> {
                    val keys = node.keys()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        val v = node.opt(k)
                        if (k == key && v is String) out += v else collectStrings(v, key, out)
                    }
                }
                is JSONArray -> for (i in 0 until node.length()) collectStrings(node.opt(i), key, out)
            }
        }
    }
}
