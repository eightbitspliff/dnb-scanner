package com.dnbscanner.app.data

import com.dnbscanner.app.model.Genre
import com.dnbscanner.app.model.Release
import com.dnbscanner.app.model.Source

enum class SortOrder(val label: String) {
    NEWEST("Neueste zuerst"),
    OLDEST("Älteste zuerst"),
    ARTIST("Artist A–Z"),
    TITLE("Titel A–Z"),
}

enum class TypeFilter(val label: String) {
    ALL("Alle"),
    ALBUMS("Alben & EPs"),
    SINGLES("Singles & Tracks"),
}

/** Filter und Sortierung für die angezeigte Liste (rein lokal, ohne neuen Scan). */
data class ReleaseFilter(
    val query: String = "",
    val genres: Set<Genre> = Genre.entries.toSet(),
    val sources: Set<Source> = Source.entries.toSet(),
    val type: TypeFilter = TypeFilter.ALL,
    val subgenres: Set<String> = emptySet(),
    val hideCompilations: Boolean = false,
    val onlyYouTube: Boolean = false,
    val onlySpotify: Boolean = false,
    val sort: SortOrder = SortOrder.NEWEST,
) {
    /** Anzahl der vom Standard abweichenden Filter im Filter-Panel (für das Badge). */
    val activeCount: Int
        get() = listOf(
            sources.size != Source.entries.size,
            type != TypeFilter.ALL,
            subgenres.isNotEmpty(),
            hideCompilations,
            sort != SortOrder.NEWEST,
        ).count { it }

    fun apply(
        releases: List<Release>,
        onYouTube: (Release) -> Boolean = { true },
        onSpotify: (Release) -> Boolean = { true },
    ): List<Release> {
        val queryTokens = TextNorm.tokens(query)
        val filtered = releases.filter { r ->
            r.genres.any { it in genres } &&
                r.sources.any { it in sources } &&
                (!onlyYouTube || onYouTube(r)) &&
                (!onlySpotify || onSpotify(r)) &&
                (!hideCompilations || !ReleaseTypes.isCompilation(r)) &&
                when (type) {
                    TypeFilter.ALL -> true
                    TypeFilter.ALBUMS -> ReleaseTypes.isAlbumOrEp(r)
                    TypeFilter.SINGLES -> ReleaseTypes.isSingle(r)
                } &&
                (subgenres.isEmpty() || r.tags.any { it in subgenres }) &&
                (queryTokens.isEmpty() || matchesQuery(r, queryTokens))
        }
        return when (sort) {
            SortOrder.NEWEST -> filtered.sortedWith(compareByDescending<Release> { it.releaseDate }.thenBy { it.artist.lowercase() })
            SortOrder.OLDEST -> filtered.sortedWith(compareBy<Release> { it.releaseDate }.thenBy { it.artist.lowercase() })
            SortOrder.ARTIST -> filtered.sortedWith(compareBy<Release> { it.artist.lowercase() }.thenBy { it.title.lowercase() })
            SortOrder.TITLE -> filtered.sortedWith(compareBy<Release> { it.title.lowercase() }.thenBy { it.artist.lowercase() })
        }
    }

    private fun matchesQuery(r: Release, queryTokens: List<String>): Boolean {
        val hay = TextNorm.tokens("${r.artist} ${r.title} ${r.label.orEmpty()} ${r.tags.joinToString(" ")}")
        return queryTokens.all { t -> hay.any { it.startsWith(t) } }
    }

    companion object {
        /** Häufigste DnB/Jungle-Tags der Ergebnisse – Kandidaten für den Subgenre-Filter. */
        fun subgenreCounts(releases: List<Release>, limit: Int = 16): List<Pair<String, Int>> =
            releases.flatMap { r -> r.tags.filter { GenreClassifier.isDnb(it) || GenreClassifier.isJungle(it) }.distinct() }
                .groupingBy { it }.eachCount()
                .entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
                .take(limit)
                .map { it.key to it.value }
    }
}

object ReleaseTypes {
    private val vaArtists = setOf("various artists", "various", "va", "v a", "verschiedene interpreten")

    fun isSingle(r: Release): Boolean {
        val t = r.type?.lowercase() ?: return false
        return "single" in t || t == "track"
    }

    fun isAlbumOrEp(r: Release): Boolean {
        val t = r.type?.lowercase() ?: return false
        if (isSingle(r)) return false
        return "album" in t || Regex("\\bep\\b").containsMatchIn(t)
    }

    fun isCompilation(r: Release): Boolean {
        if (r.type?.lowercase()?.contains("compilation") == true) return true
        if (TextNorm.cleanArtist(r.artist) in vaArtists) return true
        val title = TextNorm.normalize(r.title)
        return "compilation" in title || "sampler" in title || "various artists" in title
    }
}
