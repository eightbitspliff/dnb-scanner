package com.dnbscanner.app.data

import com.dnbscanner.app.model.Genre
import com.dnbscanner.app.model.Release
import com.dnbscanner.app.model.Source
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class TextNormAndMergeTest {

    @Test
    fun normalizes() {
        assertEquals("drum and bass", TextNorm.normalize("Drum & Bass!"))
        assertEquals("rollers vol 3", TextNorm.cleanTitle("Rollers Vol. 3 [DIS123]"))
        assertEquals("night vision", TextNorm.cleanTitle("Night Vision EP"))
        assertEquals("beyonce", TextNorm.cleanArtist("Beyoncé feat. Someone"))
    }

    private fun release(source: Source, date: LocalDate, genre: Genre, title: String = "Night Vision EP", cover: String? = null) = Release(
        artist = "Alix Perez", title = title, releaseDate = date, label = if (source == Source.BANDCAMP) "1985" else null,
        type = null, genres = setOf(genre), sources = setOf(source), sourceUrls = mapOf(source to "https://${source.name}"), coverUrl = cover,
    )

    @Test
    fun mergesDuplicatesAcrossSources() {
        val merged = ReleaseRepository.merge(
            listOf(
                release(Source.BANDCAMP, LocalDate.of(2026, 10, 16), Genre.DNB, cover = "bc.jpg"),
                release(Source.MUSICBRAINZ, LocalDate.of(2026, 10, 15), Genre.JUNGLE, title = "Night Vision", cover = "caa.jpg"),
                release(Source.BANDCAMP, LocalDate.of(2026, 10, 18), Genre.DNB, title = "Other"),
            ),
        )
        assertEquals(2, merged.size)
        assertEquals("Other", merged[0].title) // neueste zuerst
        val m = merged[1]
        assertEquals(LocalDate.of(2026, 10, 15), m.releaseDate)
        assertEquals(setOf(Genre.DNB, Genre.JUNGLE), m.genres)
        assertEquals(setOf(Source.BANDCAMP, Source.MUSICBRAINZ), m.sources)
        assertEquals("1985", m.label)
        assertEquals("bc.jpg", m.coverUrl)
    }
}
