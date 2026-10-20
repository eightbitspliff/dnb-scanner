package com.dnbscanner.app.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class YouTubeMusicMatcherTest {

    private fun item(title: String, subtitle: String, browseId: String?, videoId: String?): String {
        val nav = browseId?.let { ""","navigationEndpoint":{"browseEndpoint":{"browseId":"$it"}}""" } ?: ""
        val play = videoId?.let { ""","playlistItemData":{"videoId":"$it"}""" } ?: ""
        return """
          {"musicResponsiveListItemRenderer":{
            "flexColumns":[
              {"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[{"text":"$title"}]}}},
              {"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[{"text":"$subtitle"}]}}}
            ]$nav$play}}
        """
    }

    private val response = JSONObject(
        """
        {"contents":{"tabbedSearchResultsRenderer":{"tabs":[{"tabRenderer":{"content":{"sectionListRenderer":{"contents":[
          {"musicShelfRenderer":{"contents":[
            ${item("Some Other Album", "Album • Random Band • 2019", "MPREb_other", null)},
            ${item("Night Vision", "EP • Alix Perez • 2026", "MPREb_target", null)},
            ${item("Night Vision (Live)", "Song • Unknown • 3:20", null, "vid123")}
          ]}}
        ]}}}}]}}}
        """.trimIndent(),
    )

    @Test
    fun findsAlbumMatch() {
        val candidates = YouTubeMusicMatcher.parseCandidates(response)
        assertEquals(3, candidates.size)
        val best = YouTubeMusicMatcher.bestMatch(candidates, "Alix Perez", "Night Vision EP [1985MUSIC001]")
        assertEquals("https://music.youtube.com/browse/MPREb_target", best?.url)
    }

    @Test
    fun noMatchForUnrelatedRelease() {
        val candidates = YouTubeMusicMatcher.parseCandidates(response)
        assertNull(YouTubeMusicMatcher.bestMatch(candidates, "Goldie", "Timeless"))
    }

    @Test
    fun songUrl() {
        val c = YouTubeMusicMatcher.Candidate(listOf("x"), null, "vid123")
        assertEquals("https://music.youtube.com/watch?v=vid123", c.url)
    }
}
