package com.dnbscanner.app.data

import com.dnbscanner.app.model.Period
import com.dnbscanner.app.model.Release
import com.dnbscanner.app.model.Source
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.time.LocalDate

data class ScanResult(
    val period: Period,
    val releases: List<Release>,
    val warnings: List<String>,
    val scannedAt: Long,
)

class ReleaseRepository(http: Http) {
    private val bandcamp = BandcampSource(http)
    private val musicBrainz = MusicBrainzSource(http)

    suspend fun scan(period: Period, settings: Settings, progress: (String) -> Unit): ScanResult = coroutineScope {
        val to = LocalDate.now()
        val from = to.minusDays(period.days)
        val warnings = mutableListOf<String>()

        val mb = async {
            if (!settings.useMusicBrainz) return@async emptyList()
            try {
                musicBrainz.fetch(from, to, maxPages = 2 + period.pageFactor)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                synchronized(warnings) { warnings += "MusicBrainz: ${e.message ?: e.javaClass.simpleName}" }
                emptyList()
            }
        }

        val bc = mutableListOf<Release>()
        if (settings.useBandcamp) {
            val pages = settings.bandcampPagesPerWeek * period.pageFactor
            for (tag in settings.bandcampTags) {
                try {
                    bc += bandcamp.fetch(tag, from, to, pages) { page -> progress("Bandcamp #$tag – Seite $page/$pages") }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    synchronized(warnings) { warnings += "Bandcamp #$tag: ${e.message ?: e.javaClass.simpleName}" }
                }
            }
        }
        progress("MusicBrainz wird abgefragt …")
        val all = bc + mb.await()
        ScanResult(period, merge(all), warnings.toList(), System.currentTimeMillis())
    }

    companion object {
        /** Führt doppelte Releases (gleicher Artist + Titel) aus verschiedenen Quellen/Tags zusammen. */
        fun merge(releases: List<Release>): List<Release> =
            releases.groupBy { it.key }.values.map { group ->
                group.reduce { a, b ->
                    a.copy(
                        releaseDate = minOf(a.releaseDate, b.releaseDate),
                        label = a.label ?: b.label,
                        type = a.type ?: b.type,
                        genres = a.genres + b.genres,
                        sources = a.sources + b.sources,
                        sourceUrls = b.sourceUrls + a.sourceUrls,
                        // Bandcamp-Cover existieren immer, Cover Art Archive nicht zwingend.
                        coverUrl = if (Source.BANDCAMP in a.sources) a.coverUrl ?: b.coverUrl else b.coverUrl ?: a.coverUrl,
                    )
                }
            }.sortedWith(compareByDescending<Release> { it.releaseDate }.thenBy { it.artist.lowercase() })
    }
}
