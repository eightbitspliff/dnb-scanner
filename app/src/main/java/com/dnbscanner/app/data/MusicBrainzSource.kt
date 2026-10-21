package com.dnbscanner.app.data

import com.dnbscanner.app.model.Genre
import com.dnbscanner.app.model.Release
import com.dnbscanner.app.model.Source
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URLEncoder
import java.time.LocalDate

/**
 * Release-Groups mit DnB-/Jungle-Tags aus der offenen MusicBrainz-Datenbank.
 * MusicBrainz erlaubt max. 1 Anfrage pro Sekunde.
 */
class MusicBrainzSource(private val http: Http) {

    data class Result(val releases: List<Release>, val total: Int, val loaded: Int)

    /**
     * [queryFrom]/[queryTo] sind die Grenzen im Such-Index (z.B. "1995" bzw. "2026-10-21"), damit auch
     * Releases mit unvollständigem Datum (nur Jahr) gefunden werden.
     */
    suspend fun fetch(
        from: LocalDate,
        to: LocalDate,
        maxPages: Int,
        queryFrom: String = from.toString(),
        queryTo: String = to.toString(),
    ): Result = withContext(Dispatchers.IO) {
        val query = buildQuery(queryFrom, queryTo)
        val out = mutableListOf<Release>()
        var offset = 0
        var total = 0
        for (page in 0 until maxPages) {
            if (page > 0) delay(1100)
            val url = "https://musicbrainz.org/ws/2/release-group/?fmt=json&limit=$PAGE_SIZE&offset=$offset&query=" +
                URLEncoder.encode(query, "UTF-8")
            val json = JSONObject(http.get(url, mapOf("User-Agent" to Http.APP_UA, "Accept" to "application/json")))
            total = json.optInt("count", 0)
            val parsed = parse(json)
            parsed.filterTo(out) { it.releaseDate in from..to }
            val groups = json.optJSONArray("release-groups")?.length() ?: 0
            offset += groups
            if (groups == 0 || offset >= total) break
        }
        Result(out, total, offset)
    }

    companion object {
        private const val PAGE_SIZE = 100

        val TAGS = listOf(
            "drum and bass", "drum & bass", "drum n bass", "drum'n'bass", "dnb", "jungle",
            "liquid funk", "liquid drum and bass", "neurofunk", "jump up", "techstep", "darkstep",
            "ragga jungle", "atmospheric drum and bass", "jungle drum and bass",
        )

        fun buildQuery(from: String, to: String): String {
            val tags = TAGS.joinToString(" OR ") { "tag:\"$it\"" }
            return "firstreleasedate:[$from TO $to] AND ($tags)"
        }

        /** Akzeptiert "2026-10-15", "2026-10" und "2026" (unvollständige Daten = erster Tag). */
        fun parseDate(raw: String): LocalDate? = runCatching {
            when (raw.length) {
                10 -> LocalDate.parse(raw)
                7 -> LocalDate.parse("$raw-01")
                4 -> LocalDate.of(raw.toInt(), 1, 1)
                else -> null
            }
        }.getOrNull()

        fun dateLabel(raw: String): String? = when (raw.length) {
            7 -> "${raw.substring(5)}.${raw.take(4)}"
            4 -> raw
            else -> null
        }

        fun parse(json: JSONObject): List<Release> {
            val groups = json.optJSONArray("release-groups") ?: return emptyList()
            val out = mutableListOf<Release>()
            for (i in 0 until groups.length()) {
                val rg = groups.optJSONObject(i) ?: continue
                val id = rg.optString("id")
                val title = rg.optString("title")
                if (id.isBlank() || title.isBlank()) continue
                val rawDate = rg.optString("first-release-date")
                val date = parseDate(rawDate) ?: continue

                val credits = rg.optJSONArray("artist-credit")
                val artist = buildString {
                    if (credits != null) for (c in 0 until credits.length()) {
                        val credit = credits.optJSONObject(c) ?: continue
                        append(credit.optString("name").ifBlank { credit.optJSONObject("artist")?.optString("name").orEmpty() })
                        append(credit.optString("joinphrase"))
                    }
                }.trim()
                if (artist.isBlank()) continue

                val tagNames = mutableListOf<String>()
                rg.optJSONArray("tags")?.let { tags ->
                    for (t in 0 until tags.length()) tags.optJSONObject(t)?.optString("name")?.let { tagNames += it.lowercase() }
                }
                val genres = GenreClassifier.classify(tagNames)
                if (genres.isEmpty()) continue // Tags passen nicht (genug) zu DnB/Jungle

                val secondary = rg.optJSONArray("secondary-types")
                val type = buildList {
                    rg.optString("primary-type").takeIf { it.isNotBlank() }?.let(::add)
                    if (secondary != null) for (s in 0 until secondary.length()) add(secondary.optString(s))
                }.joinToString(" / ").ifBlank { null }

                out += Release(
                    artist = artist,
                    title = title,
                    releaseDate = date,
                    label = null,
                    type = type,
                    genres = genres,
                    sources = setOf(Source.MUSICBRAINZ),
                    sourceUrls = mapOf(Source.MUSICBRAINZ to "https://musicbrainz.org/release-group/$id"),
                    coverUrl = "https://coverartarchive.org/release-group/$id/front-250",
                    tags = GenreClassifier.displayTags(tagNames),
                    dateLabel = dateLabel(rawDate),
                )
            }
            return out
        }
    }
}
