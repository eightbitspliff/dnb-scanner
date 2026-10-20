package com.dnbscanner.app.data

import java.text.Normalizer

object TextNorm {
    private val diacritics = Regex("\\p{M}+")
    private val brackets = Regex("[\\(\\[\\{][^\\)\\]\\}]*[\\)\\]\\}]")
    private val nonAlnum = Regex("[^\\p{L}\\p{N}]+")
    private val featuring = Regex("\\s(feat\\.?|ft\\.?|featuring)\\s.*$", RegexOption.IGNORE_CASE)
    private val releaseSuffix = Regex("\\s(ep|lp|single|album|remixes|vip)$")

    fun normalize(s: String): String {
        val lower = Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(diacritics, "")
        return lower.replace("&", " and ").replace(nonAlnum, " ").trim().replace(Regex("\\s+"), " ")
    }

    /** Entfernt Katalognummern in Klammern, "feat."-Anhänge und EP/LP-Suffixe. */
    fun cleanTitle(title: String): String {
        val noBrackets = title.replace(brackets, " ")
        val cleaned = normalize(noBrackets).replace(releaseSuffix, "").trim()
        return cleaned.ifEmpty { normalize(title) }
    }

    fun cleanArtist(artist: String): String = normalize(artist.replace(featuring, ""))

    fun tokens(s: String): List<String> = normalize(s).split(' ').filter { it.isNotBlank() }

    fun releaseKey(artist: String, title: String): String = cleanArtist(artist) + "|" + cleanTitle(title)
}
