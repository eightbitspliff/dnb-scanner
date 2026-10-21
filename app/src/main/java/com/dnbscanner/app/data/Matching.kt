package com.dnbscanner.app.data

/** Gemeinsame Treffer-Bewertung für YouTube Music und Spotify. */
object Matching {
    /**
     * Bewertet, wie gut ein Suchtreffer (Texte: Titel zuerst, dann Artist/Infos) zum Release passt.
     * 0.0 = kein Treffer.
     */
    fun score(texts: List<String>, artist: String, title: String): Double {
        val titleTokens = TextNorm.tokens(TextNorm.cleanTitle(title)).toSet()
        val artistTokens = TextNorm.tokens(TextNorm.cleanArtist(artist)).toSet()
        if (titleTokens.isEmpty()) return 0.0
        val hay = TextNorm.tokens(texts.joinToString(" ")).toSet()
        val titleCov = titleTokens.count { it in hay }.toDouble() / titleTokens.size
        val artistCov = if (artistTokens.isEmpty()) 1.0 else artistTokens.count { it in hay }.toDouble() / artistTokens.size
        if (titleCov < 0.6 || artistCov < 0.5) return 0.0
        // Erster Text ist der Titel des Treffers: exakte Übereinstimmung bevorzugen.
        val first = TextNorm.tokens(TextNorm.cleanTitle(texts.firstOrNull().orEmpty())).toSet()
        val exact = if (first == titleTokens) 0.2 else 0.0
        return titleCov * 0.5 + artistCov * 0.3 + exact
    }
}
