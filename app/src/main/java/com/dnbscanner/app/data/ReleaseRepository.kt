package com.dnbscanner.app.data

import com.dnbscanner.app.model.Period
import com.dnbscanner.app.model.Release
import com.dnbscanner.app.model.Source
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicInteger

data class ScanResult(
    val period: Period,
    val releases: List<Release>,
    val warnings: List<String>,
    val scannedAt: Long,
    /** Kandidaten, die die Genre-Prüfung nicht bestanden haben. */
    val rejected: Int = 0,
)

class ReleaseRepository(http: Http, private val tagStore: TagStore = MemoryTagStore()) {
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

        val candidates = mutableListOf<Release>()
        if (settings.useBandcamp) {
            val pages = settings.bandcampPagesPerWeek * period.pageFactor
            for (tag in settings.bandcampTags) {
                try {
                    candidates += bandcamp.fetch(tag, from, to, pages) { page -> progress("Bandcamp #$tag – Seite $page/$pages") }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    synchronized(warnings) { warnings += "Bandcamp #$tag: ${e.message ?: e.javaClass.simpleName}" }
                }
            }
        }
        val unique = candidates.distinctBy { it.sourceUrls[Source.BANDCAMP] }
        val verified = verifyBandcamp(unique, progress, warnings)

        progress("MusicBrainz wird abgefragt …")
        val all = verified + mb.await()
        ScanResult(period, merge(all), warnings.toList(), System.currentTimeMillis(), rejected = unique.size - verified.size)
    }

    /** Prüft jedes Bandcamp-Release anhand der Tags auf seiner Seite und behält nur echtes DnB/Jungle. */
    private suspend fun verifyBandcamp(
        candidates: List<Release>,
        progress: (String) -> Unit,
        warnings: MutableList<String>,
    ): List<Release> = coroutineScope {
        val permits = Semaphore(4)
        val done = AtomicInteger(0)
        val failures = AtomicInteger(0)
        val blocked = AtomicInteger(0)
        val checked = candidates.map { r ->
            async {
                permits.withPermit {
                    val url = r.sourceUrls[Source.BANDCAMP] ?: return@withPermit null
                    val tags = r.tags.takeIf { it.isNotEmpty() } ?: tagStore.get(url) ?: try {
                        bandcamp.fetchTags(url).also { if (it.isNotEmpty()) tagStore.put(url, it) else blocked.incrementAndGet() }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        failures.incrementAndGet()
                        emptyList()
                    }
                    progress("Genre-Prüfung ${done.incrementAndGet()}/${candidates.size}")
                    val genres = GenreClassifier.classify(tags)
                    if (genres.isEmpty()) null else r.copy(genres = genres, tags = GenreClassifier.displayTags(tags))
                }
            }
        }.awaitAll().filterNotNull()
        if (failures.get() > 0) synchronized(warnings) { warnings += "Bandcamp: ${failures.get()} Seiten nicht ladbar (nicht geprüft)" }
        if (blocked.get() > 0) synchronized(warnings) { warnings += "Bandcamp: ${blocked.get()} Seiten ohne Tags (nicht geprüft)" }
        checked
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
                        tags = (a.tags + b.tags).distinct(),
                    )
                }
            }.sortedWith(compareByDescending<Release> { it.releaseDate }.thenBy { it.artist.lowercase() })
    }
}
