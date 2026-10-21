package com.dnbscanner.app.data

import com.dnbscanner.app.model.Genre
import com.dnbscanner.app.model.Release
import com.dnbscanner.app.model.Source
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class SpotifyMatcherTest {

    private val search = JSONObject(
        """
        {"albums":{"items":[
          {"name":"Different Album","artists":[{"name":"Alix Perez"}],"external_urls":{"spotify":"https://open.spotify.com/album/wrong"}},
          {"name":"Night Vision","artists":[{"name":"Alix Perez"},{"name":"Monty"}],"external_urls":{"spotify":"https://open.spotify.com/album/right"}}
        ]},
         "tracks":{"items":[
          {"name":"Night Vision","artists":[{"name":"Alix Perez"}],"external_urls":{"spotify":"https://open.spotify.com/track/t1"}}
        ]}}
        """.trimIndent(),
    )

    @Test
    fun prefersAlbumMatch() {
        val candidates = SpotifyMatcher.parseSearch(search)
        assertEquals(3, candidates.size)
        val best = SpotifyMatcher.bestMatch(candidates, "Alix Perez & Monty", "Night Vision EP")
        assertEquals("https://open.spotify.com/album/right", best?.url)
        assertNull(SpotifyMatcher.bestMatch(candidates, "Goldie", "Timeless"))
    }

    @Test
    fun parsesOdesli() {
        val json = JSONObject("""{"linksByPlatform":{"spotify":{"url":"https://open.spotify.com/album/xyz"},"youtubeMusic":{"url":"https://music.youtube.com/x"}}}""")
        assertEquals("https://open.spotify.com/album/xyz", SpotifyMatcher.parseOdesli(json))
        assertNull(SpotifyMatcher.parseOdesli(JSONObject("""{"linksByPlatform":{}}""")))
    }

    @Test
    fun searchUrlUsesPercentEncoding() {
        val r = Release(
            "Alix Perez", "Night Vision [1985]", LocalDate.of(2026, 10, 1), null, null,
            setOf(Genre.DNB), setOf(Source.BANDCAMP), emptyMap(), null,
        )
        assertEquals("https://open.spotify.com/search/Alix%20Perez%20Night%20Vision", SpotifyMatcher.searchUrl(r))
    }
}
