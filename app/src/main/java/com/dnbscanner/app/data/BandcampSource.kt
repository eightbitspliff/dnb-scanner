package com.dnbscanner.app.data

import com.dnbscanner.app.model.Genre
import com.dnbscanner.app.model.Release
import com.dnbscanner.app.model.Source
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Neue Releases über die (inoffizielle) Discover-API von Bandcamp, sortiert nach "neu".
 */
class BandcampSource(private val http: Http) {

    suspend fun fetch(
        tag: String,
        from: LocalDate,
        to: LocalDate,
        maxPages: Int,
        onPage: (Int) -> Unit = {},
    ): List<Release> = withContext(Dispatchers.IO) {
        val genre = genreForTag(tag)
        val out = mutableListOf<Release>()
        var cursor = "*"
        for (page in 0 until maxPages) {
            onPage(page + 1)
            val body = JSONObject()
                .put("category_id", 0)
                .put("tag_norm_names", JSONArray().put(tag))
                .put("geoname_id", 0)
                .put("slice", "new")
                .put("cursor", cursor)
                .put("size", PAGE_SIZE)
                .put("include_result_types", JSONArray().put("a").put("s"))
            val json = JSONObject(
                http.postJson(
                    URL, body.toString(),
                    mapOf("User-Agent" to Http.BROWSER_UA, "Origin" to "https://bandcamp.com", "Referer" to "https://bandcamp.com/discover"),
                ),
            )
            val parsed = parsePage(json, genre)
            parsed.filterTo(out) { it.releaseDate in from..to }
            val older = parsed.count { it.releaseDate < from }
            cursor = json.optString("cursor", "")
            if (parsed.isEmpty() || cursor.isEmpty() || older * 2 > parsed.size) break
            delay(350)
        }
        out
    }

    /** Lädt die Tags von der Release-Seite. Leere Liste = Seite ohne Tags (z.B. Bot-Sperre). */
    suspend fun fetchTags(itemUrl: String): List<String> = withContext(Dispatchers.IO) {
        val html = try {
            http.get(itemUrl, mapOf("User-Agent" to Http.BROWSER_UA))
        } catch (e: HttpStatusException) {
            if (e.code != 429) throw e
            delay(2500)
            http.get(itemUrl, mapOf("User-Agent" to Http.BROWSER_UA))
        }
        parseTags(html)
    }

    companion object {
        const val URL = "https://bandcamp.com/api/discover/1/discover_web"
        private const val PAGE_SIZE = 60
        private val DATE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)

        private val TAG_LINK = Regex("<a[^>]*class=\"tag\"[^>]*>\\s*([^<]+?)\\s*</a>")
        private val KEYWORDS = Regex("\"keywords\"\\s*:\\s*\\[(.*?)]", RegexOption.DOT_MATCHES_ALL)
        private val QUOTED = Regex("\"((?:[^\"\\\\]|\\\\.)*)\"")

        fun parseTags(html: String): List<String> {
            val tags = LinkedHashSet<String>()
            TAG_LINK.findAll(html).forEach { tags += unescape(it.groupValues[1]).trim().lowercase() }
            KEYWORDS.find(html)?.let { m ->
                QUOTED.findAll(m.groupValues[1]).forEach { tags += unescape(it.groupValues[1]).trim().lowercase() }
            }
            return tags.filter { it.isNotBlank() }
        }

        private fun unescape(s: String) = s
            .replace("&amp;", "&").replace("&#39;", "'").replace("&quot;", "\"").replace("\\u0026", "&")

        fun genreForTag(tag: String): Genre = if (tag.contains("jungle")) Genre.JUNGLE else Genre.DNB

        fun parsePage(json: JSONObject, genre: Genre): List<Release> {
            val results = json.optJSONArray("results") ?: return emptyList()
            val out = ArrayList<Release>(results.length())
            for (i in 0 until results.length()) {
                val item = results.optJSONObject(i) ?: continue
                parseItem(item, genre)?.let(out::add)
            }
            return out
        }

        private fun parseItem(item: JSONObject, genre: Genre): Release? {
            val title = item.str("title") ?: return null
            val bandName = item.str("band_name")
            val albumArtist = item.str("album_artist")
            val artist = albumArtist ?: bandName ?: return null
            val url = item.str("item_url") ?: return null
            val date = parseDate(item.str("release_date") ?: item.str("publish_date")) ?: return null
            val label = item.str("label_name") ?: bandName?.takeIf { albumArtist != null && it != albumArtist }
            val imageId = item.optLong("item_image_id", 0L).takeIf { it > 0 }
                ?: item.optJSONObject("primary_image")?.optLong("image_id", 0L)?.takeIf { it > 0 }
            val type = when (item.str("result_type")) {
                "t" -> "Track"
                "a" -> "Album/EP"
                else -> null
            }
            val tags = mutableListOf<String>()
            for (key in listOf("tags", "tag_names", "genre_tags")) {
                val arr = item.optJSONArray(key) ?: continue
                for (t in 0 until arr.length()) {
                    val v = arr.opt(t)
                    val name = if (v is JSONObject) v.optString("name").ifBlank { v.optString("norm_name") } else v?.toString()
                    if (!name.isNullOrBlank()) tags += name.lowercase()
                }
            }
            return Release(
                artist = artist,
                title = title,
                releaseDate = date,
                label = label,
                type = type,
                genres = setOf(genre),
                sources = setOf(Source.BANDCAMP),
                sourceUrls = mapOf(Source.BANDCAMP to url),
                coverUrl = imageId?.let { "https://f4.bcbits.com/img/a${it}_9.jpg" },
                tags = tags,
            )
        }

        /** Format z.B. "20 Oct 2026 00:00:00 GMT"; ISO-Daten werden ebenfalls akzeptiert. */
        fun parseDate(raw: String?): LocalDate? {
            if (raw.isNullOrBlank()) return null
            val trimmed = raw.trim()
            runCatching { return LocalDate.parse(trimmed.take(10)) }
            val parts = trimmed.split(' ')
            if (parts.size < 3) return null
            return runCatching { LocalDate.parse(parts.take(3).joinToString(" "), DATE) }.getOrNull()
        }

        private fun JSONObject.str(key: String): String? =
            if (isNull(key)) null else optString(key, "").takeIf { it.isNotBlank() }
    }
}
