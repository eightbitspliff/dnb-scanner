package com.dnbscanner.app.data

import com.dnbscanner.app.model.Genre

/**
 * Strenge Genre-Prüfung anhand der Tags eines Releases: Es zählt nur als DnB/Jungle,
 * wenn entsprechende Tags vorhanden sind und nicht von anderen Genres überwogen werden.
 */
object GenreClassifier {
    private val dnbContains = listOf("drum and bass", "drum n bass", "drum bass", "drumnbass", "drumandbass", "drum n b")
    private val dnbExact = setOf(
        "dnb", "d and b", "d n b", "liquid", "liquid funk", "liquid dnb", "neurofunk", "neuro", "jump up", "jumpup",
        "techstep", "darkstep", "drumfunk", "rollers", "crossbreed", "autonomic", "halftime dnb", "deep dnb",
        "minimal dnb", "dancefloor dnb", "intelligent dnb", "atmospheric dnb", "dnb liquid",
    )
    private val jungleExclusions = listOf("jungle terror", "jungle house", "jungle juice")

    private val conflictExact = setOf(
        "pop", "rap", "trap", "edm", "idm", "rnb", "r and b", "soul", "jazz", "funk", "disco", "folk", "indie", "punk",
        "reggae", "dancehall", "classical", "country", "blues", "gospel", "soundtrack", "downtempo", "chillout", "chill",
        "trip hop", "chillwave", "experimental", "breakcore", "hardstyle", "big room", "future bass", "footwork", "juke",
        "uk garage", "garage", "grime", "drill", "phonk", "hyperpop", "noise", "mid tempo", "midtempo", "acid",
        "industrial", "ebm", "darkwave", "post punk", "shoegaze", "emo", "latin", "afrobeat", "amapiano", "kpop",
        "j pop", "city pop", "beats", "barber beats", "glitch", "glitch hop", "moombahton", "riddim", "brostep",
        "electro", "electropop", "synthpop", "new wave", "singer songwriter", "acoustic", "lo fi", "lofi",
    )
    private val conflictContains = listOf("house", "techno", "trance", "dubstep", "hip hop", "hiphop", "metal", "rock", "synthwave", "vaporwave", "ambient")

    fun isDnb(tag: String): Boolean {
        val t = TextNorm.normalize(tag)
        return dnbContains.any { it in t } || t in dnbExact || t.split(' ').contains("dnb")
    }

    fun isJungle(tag: String): Boolean {
        val t = TextNorm.normalize(tag)
        return "jungle" in t && jungleExclusions.none { it in t }
    }

    private fun isConflict(tag: String): Boolean {
        val t = TextNorm.normalize(tag)
        if (isDnb(t) || isJungle(t)) return false
        return t in conflictExact || conflictContains.any { it in t } || jungleExclusions.any { it in t }
    }

    /** Leere Menge = kein DnB/Jungle. */
    fun classify(tags: Collection<String>): Set<Genre> {
        val distinct = tags.map { TextNorm.normalize(it) }.filter { it.isNotBlank() }.distinct()
        val dnb = distinct.count(::isDnb)
        val jungle = distinct.count(::isJungle)
        val conflicts = distinct.count(::isConflict)
        val matching = distinct.count { isDnb(it) || isJungle(it) }
        if (matching == 0 || matching < conflicts) return emptySet()
        return buildSet {
            if (dnb > 0) add(Genre.DNB)
            if (jungle > 0) add(Genre.JUNGLE)
        }
    }

    /** Tags für die Anzeige: DnB/Jungle-Tags zuerst. */
    fun displayTags(tags: Collection<String>): List<String> {
        val distinct = tags.map { it.trim().lowercase() }.filter { it.isNotBlank() }.distinct()
        return distinct.sortedBy { if (isDnb(it) || isJungle(it)) 0 else 1 }.take(12)
    }
}
