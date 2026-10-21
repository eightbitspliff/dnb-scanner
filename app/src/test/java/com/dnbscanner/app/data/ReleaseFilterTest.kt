package com.dnbscanner.app.data

import com.dnbscanner.app.model.Genre
import com.dnbscanner.app.model.Release
import com.dnbscanner.app.model.Source
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class ReleaseFilterTest {

    private fun r(
        artist: String, title: String, day: Int, type: String?, genre: Genre = Genre.DNB,
        source: Source = Source.BANDCAMP, tags: List<String> = emptyList(),
    ) = Release(
        artist, title, LocalDate.of(2026, 10, day), null, type, setOf(genre), setOf(source),
        mapOf(source to "https://$artist/$title"), null, tags,
    )

    private val all = listOf(
        r("Calibre", "Shelflife", 10, "Album", tags = listOf("liquid funk", "drum and bass")),
        r("Various Artists", "Jungle Classics", 12, "Album / Compilation", Genre.JUNGLE, tags = listOf("jungle")),
        r("Noisia", "Tentacles", 15, "Single", source = Source.MUSICBRAINZ, tags = listOf("neurofunk")),
        r("Amen Kid", "Rinse EP", 5, "Album/EP", Genre.JUNGLE, tags = listOf("ragga jungle", "jungle")),
        r("Bou", "Veteran", 20, "Track", tags = listOf("jump up")),
    )

    private fun titles(f: ReleaseFilter) = f.apply(all).map { it.title }

    @Test
    fun defaultSortsNewestFirst() {
        assertEquals(listOf("Veteran", "Tentacles", "Jungle Classics", "Shelflife", "Rinse EP"), titles(ReleaseFilter()))
        assertEquals(listOf("Rinse EP", "Shelflife", "Jungle Classics", "Tentacles", "Veteran"), titles(ReleaseFilter(sort = SortOrder.OLDEST)))
        assertEquals("Amen Kid", ReleaseFilter(sort = SortOrder.ARTIST).apply(all).first().artist)
    }

    @Test
    fun filtersByTypeCompilationSourceAndSubgenre() {
        assertEquals(listOf("Jungle Classics", "Shelflife", "Rinse EP"), titles(ReleaseFilter(type = TypeFilter.ALBUMS)))
        assertEquals(listOf("Veteran", "Tentacles"), titles(ReleaseFilter(type = TypeFilter.SINGLES)))
        assertEquals(4, titles(ReleaseFilter(hideCompilations = true)).size)
        assertEquals(listOf("Tentacles"), titles(ReleaseFilter(sources = setOf(Source.MUSICBRAINZ))))
        assertEquals(listOf("Jungle Classics", "Rinse EP"), titles(ReleaseFilter(genres = setOf(Genre.JUNGLE))))
        assertEquals(listOf("Rinse EP"), titles(ReleaseFilter(subgenres = setOf("ragga jungle"))))
        assertEquals(listOf("Shelflife"), titles(ReleaseFilter(query = "liquid")))
    }

    @Test
    fun countsSubgenresAndActiveFilters() {
        val counts = ReleaseFilter.subgenreCounts(all)
        assertEquals("jungle" to 2, counts.first())
        assertEquals(0, ReleaseFilter().activeCount)
        assertEquals(2, ReleaseFilter(type = TypeFilter.SINGLES, sort = SortOrder.TITLE).activeCount)
    }
}
