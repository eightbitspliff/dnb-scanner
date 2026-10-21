package com.dnbscanner.app.model

import com.dnbscanner.app.data.TextNorm
import java.time.LocalDate

enum class Genre(val label: String) {
    DNB("Drum & Bass"),
    JUNGLE("Jungle"),
}

enum class Source(val label: String) {
    BANDCAMP("Bandcamp"),
    MUSICBRAINZ("MusicBrainz"),
}

enum class Period(val days: Long, val label: String, val pageFactor: Int) {
    WEEK(7, "Woche", 1),
    MONTH(30, "Monat", 3),
    QUARTER(90, "3 Mon.", 6),
    YEAR(365, "1 Jahr", 8),

    /** Eigener Zeitraum: Release-Jahr von–bis. */
    CUSTOM(0, "Von–Bis", 6),
}

data class Release(
    val artist: String,
    val title: String,
    val releaseDate: LocalDate,
    val label: String?,
    val type: String?,
    val genres: Set<Genre>,
    val sources: Set<Source>,
    val sourceUrls: Map<Source, String>,
    val coverUrl: String?,
    /** Genre-Tags der Quelle (DnB/Jungle-Tags zuerst). */
    val tags: List<String> = emptyList(),
    /** Anzeige für unvollständige Daten, z.B. "1996" oder "05.1996"; null = genaues Datum. */
    val dateLabel: String? = null,
) {
    val key: String get() = TextNorm.releaseKey(artist, title)
}

/** Link zu YouTube Music / Spotify: [direct] = konkretes Album/Song gefunden, sonst Such-Link. */
data class MusicLink(
    val url: String,
    val direct: Boolean,
    val matchedText: String? = null,
)
