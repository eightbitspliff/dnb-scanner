package com.dnbscanner.app.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test
import java.time.LocalDate

class MusicBrainzRetryTest {

    private fun page(count: Int, ids: List<String>) = """
        {"count":$count,"release-groups":[${ids.joinToString(",") { id ->
            """{"id":"$id","title":"T$id","first-release-date":"2026-10-15","artist-credit":[{"name":"A$id"}],"tags":[{"name":"drum and bass"}]}"""
        }}]}
    """

    /** Liefert nacheinander die vorgegebenen Antworten; Int = HTTP-Fehlercode. */
    private class FakeHttp(private val answers: MutableList<Any>) : Http() {
        var calls = 0
        override fun get(url: String, headers: Map<String, String>): String {
            calls++
            return when (val a = answers.removeAt(0)) {
                is Int -> throw HttpStatusException(a, "musicbrainz.org")
                else -> a as String
            }
        }
    }

    private val from = LocalDate.of(2026, 10, 1)
    private val to = LocalDate.of(2026, 10, 21)

    @Test
    fun retriesOn503AndSucceeds() = runBlocking {
        val http = FakeHttp(mutableListOf(503, 503, page(1, listOf("a"))))
        val mb = MusicBrainzSource(http, minIntervalMs = 0, retryBaseMs = 1)
        val result = mb.fetch(from, to, maxPages = 3)
        assertEquals(3, http.calls)
        assertEquals(1, result.releases.size)
        assertNull(result.error)
    }

    @Test
    fun keepsPartialResultWhenLaterPageFails() = runBlocking {
        val ids = (1..100).map { "p$it" }
        val http = FakeHttp(mutableListOf(page(250, ids), 503, 503, 503))
        val mb = MusicBrainzSource(http, minIntervalMs = 0, retryBaseMs = 1, maxRetries = 2)
        val result = mb.fetch(from, to, maxPages = 3)
        assertEquals(100, result.releases.size)
        assertNotNull(result.error)
    }

    @Test
    fun firstPageFailureIsReported() = runBlocking {
        val http = FakeHttp(mutableListOf(503, 503))
        val mb = MusicBrainzSource(http, minIntervalMs = 0, retryBaseMs = 1, maxRetries = 1)
        try {
            mb.fetch(from, to, maxPages = 1)
            fail("Fehler erwartet")
        } catch (e: HttpStatusException) {
            assertEquals(503, e.code)
        }
    }

    @Test
    fun doesNotRetryOtherErrors() = runBlocking {
        val http = FakeHttp(mutableListOf(400))
        val mb = MusicBrainzSource(http, minIntervalMs = 0, retryBaseMs = 1)
        try {
            mb.fetch(from, to, maxPages = 1)
            fail("Fehler erwartet")
        } catch (e: HttpStatusException) {
            assertEquals(1, http.calls)
        }
    }
}
