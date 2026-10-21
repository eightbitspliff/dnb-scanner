package com.dnbscanner.app.data

import com.dnbscanner.app.model.Genre
import com.dnbscanner.app.model.Source
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class BandcampSourceTest {

    private val sample = """
        {"results":[
          {"title":"Rollers Vol. 3","band_name":"Dispatch Recordings","album_artist":"Ed:it","item_url":"https://dispatch.bandcamp.com/album/rollers-vol-3",
           "release_date":"17 Oct 2026 00:00:00 GMT","item_image_id":123456,"result_type":"a","label_name":null},
          {"title":"Amen Science","band_name":"Junglist Kid","album_artist":null,"item_url":"https://junglistkid.bandcamp.com/track/amen-science",
           "release_date":"2026-10-12","primary_image":{"image_id":42},"result_type":"t"},
          {"title":"no url","band_name":"x","release_date":"17 Oct 2026 00:00:00 GMT"}
        ],"cursor":"abc"}
    """.trimIndent()

    @Test
    fun parsesResults() {
        val releases = BandcampSource.parsePage(JSONObject(sample), Genre.DNB)
        assertEquals(2, releases.size)

        val first = releases[0]
        assertEquals("Ed:it", first.artist)
        assertEquals("Rollers Vol. 3", first.title)
        assertEquals("Dispatch Recordings", first.label)
        assertEquals(LocalDate.of(2026, 10, 17), first.releaseDate)
        assertEquals("https://f4.bcbits.com/img/a123456_9.jpg", first.coverUrl)
        assertEquals(setOf(Source.BANDCAMP), first.sources)

        val second = releases[1]
        assertEquals("Junglist Kid", second.artist)
        assertNull(second.label)
        assertEquals("Track", second.type)
        assertEquals("https://f4.bcbits.com/img/a42_9.jpg", second.coverUrl)
    }

    @Test
    fun genreFromTag() {
        assertEquals(Genre.JUNGLE, BandcampSource.genreForTag("jungle"))
        assertEquals(Genre.DNB, BandcampSource.genreForTag("drum-and-bass"))
    }

    @Test
    fun parsesDates() {
        assertEquals(LocalDate.of(2026, 9, 1), BandcampSource.parseDate("1 Sep 2026 00:00:00 GMT"))
        assertEquals(LocalDate.of(2026, 9, 1), BandcampSource.parseDate("2026-09-01T00:00:00Z"))
        assertNull(BandcampSource.parseDate("garbage"))
    }

    @Test
    fun parsesTagsFromReleasePage() {
        val html = """
            <script type="application/ld+json">{"@type":"MusicAlbum","keywords":["Electronic","drum &amp; bass","jungle"]}</script>
            <div class="tralbum-tags">
              <a class="tag" href="https://bandcamp.com/discover/electronic">electronic</a>
              <a href="https://bandcamp.com/discover/drum-and-bass" class="tag">
                drum &amp; bass
              </a>
              <a class="tag" href="https://bandcamp.com/discover/liquid">liquid</a>
            </div>
        """.trimIndent()
        assertEquals(listOf("electronic", "drum & bass", "liquid", "jungle"), BandcampSource.parseTags(html))
        assertEquals(emptyList<String>(), BandcampSource.parseTags("<html>challenge</html>"))
    }
}
