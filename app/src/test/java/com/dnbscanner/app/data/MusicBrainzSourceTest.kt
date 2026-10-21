package com.dnbscanner.app.data

import com.dnbscanner.app.model.Genre
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class MusicBrainzSourceTest {

    @Test
    fun parsesReleaseGroups() {
        val json = JSONObject(
            """
            {"count":3,"offset":0,"release-groups":[
              {"id":"aaa","title":"Night Vision EP","first-release-date":"2026-10-15","primary-type":"EP",
               "artist-credit":[{"name":"Alix Perez","joinphrase":" & "},{"name":"Monty"}],
               "tags":[{"count":1,"name":"drum and bass"},{"count":1,"name":"Ragga Jungle"}]},
              {"id":"bbb","title":"Only year","first-release-date":"2026","artist-credit":[{"name":"X"}]},
              {"id":"ddd","title":"Trance thing","first-release-date":"2026-10-01","artist-credit":[{"name":"Y"}],
               "tags":[{"name":"trance"},{"name":"drum and bass"},{"name":"dubstep"}]},
              {"id":"ccc","title":"Liquid","first-release-date":"2026-09-30","primary-type":"Single",
               "secondary-types":["Remix"],"artist-credit":[{"name":"Calibre"}],"tags":[{"name":"liquid funk"}]}
            ]}
            """.trimIndent(),
        )
        val releases = MusicBrainzSource.parse(json)
        assertEquals(2, releases.size)
        assertEquals("Alix Perez & Monty", releases[0].artist)
        assertEquals(setOf(Genre.DNB, Genre.JUNGLE), releases[0].genres)
        assertEquals(LocalDate.of(2026, 10, 15), releases[0].releaseDate)
        assertEquals("Single / Remix", releases[1].type)
        assertEquals(setOf(Genre.DNB), releases[1].genres)
    }

    @Test
    fun queryContainsDateRangeAndTags() {
        val q = MusicBrainzSource.buildQuery(LocalDate.of(2026, 10, 13), LocalDate.of(2026, 10, 20))
        assertTrue(q.startsWith("firstreleasedate:[2026-10-13 TO 2026-10-20] AND ("))
        assertTrue(q.contains("tag:\"jungle\""))
    }
}
