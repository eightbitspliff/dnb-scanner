package com.dnbscanner.app.data

import com.dnbscanner.app.model.Genre
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GenreClassifierTest {

    private fun c(vararg tags: String) = GenreClassifier.classify(tags.toList())

    @Test
    fun acceptsRealDnbAndJungle() {
        assertEquals(setOf(Genre.DNB), c("electronic", "drum and bass", "liquid funk"))
        assertEquals(setOf(Genre.DNB), c("Drum & Bass"))
        assertEquals(setOf(Genre.DNB), c("drum'n'bass", "neurofunk"))
        assertEquals(setOf(Genre.JUNGLE), c("jungle"))
        assertEquals(setOf(Genre.DNB, Genre.JUNGLE), c("ragga jungle", "dnb", "london"))
        // Echter Fall aus MusicBrainz: viele DnB-Tags überwiegen Chillwave/Vaporwave
        assertEquals(
            setOf(Genre.DNB, Genre.JUNGLE),
            c("electronic", "downtempo", "drum and bass", "jungle", "dnb", "chillwave", "liquid dnb", "vaporwave", "liquid jungle"),
        )
    }

    @Test
    fun rejectsOtherGenres() {
        assertTrue(c("house", "deep house", "electronic").isEmpty())
        assertTrue(c("techno").isEmpty())
        assertTrue(c().isEmpty())
        assertTrue(c("jungle terror", "big room").isEmpty())
        // Echte Grenzfälle aus MusicBrainz: DnB nur als Nebentag
        assertTrue(c("trance", "drum and bass", "dubstep", "djent", "mid tempo cyberpunk").isEmpty())
        assertTrue(
            c("electronic", "downtempo", "drum and bass", "instrumental", "chillout", "experimental", "breakbeat", "lo-fi", "trip hop", "chillwave", "vaporwave").isEmpty(),
        )
    }

    @Test
    fun rejectsReleasesWhereDnbIsOnlyASideTag() {
        // Unbekannte/fremde Genres verdrängen ein einzelnes DnB-Tag
        assertTrue(c("electronic", "leftfield", "bassline", "drum and bass", "jersey club", "london").isEmpty())
        assertTrue(c("electronic", "experimental", "idm", "drum and bass", "glitch", "footwork", "berlin").isEmpty())
        assertTrue(c("drum and bass", "dubstep", "london").isEmpty())
        // Typische echte DnB-Releases bleiben drin
        assertEquals(setOf(Genre.DNB, Genre.JUNGLE), c("electronic", "drum & bass", "jungle", "liquid", "dnb", "breakbeat", "rollers", "london"))
        assertEquals(setOf(Genre.DNB), c("electronic", "drum and bass", "bristol"))
        assertEquals(setOf(Genre.JUNGLE), c("jungle", "ragga", "amen", "hardcore"))
    }

    @Test
    fun displayTagsPutGenreTagsFirst() {
        assertEquals(listOf("jungle", "drum & bass", "electronic", "london"), GenreClassifier.displayTags(listOf("Electronic", "jungle", "London", "Drum & Bass")))
    }
}
