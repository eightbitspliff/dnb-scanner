package com.dnbscanner.app.ui

import android.app.Application
import androidx.compose.runtime.mutableStateMapOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dnbscanner.app.data.Http
import com.dnbscanner.app.data.LinkCache
import com.dnbscanner.app.data.ReleaseRepository
import com.dnbscanner.app.data.ScanStore
import com.dnbscanner.app.data.Settings
import com.dnbscanner.app.data.SettingsStore
import com.dnbscanner.app.data.SpotifyMatcher
import com.dnbscanner.app.data.YouTubeMusicMatcher
import com.dnbscanner.app.model.Genre
import com.dnbscanner.app.model.Period
import com.dnbscanner.app.model.Release
import com.dnbscanner.app.model.MusicLink
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

data class UiState(
    val period: Period = Period.WEEK,
    val releases: List<Release> = emptyList(),
    val scannedAt: Long? = null,
    val loading: Boolean = false,
    val progress: String? = null,
    val warnings: List<String> = emptyList(),
    val error: String? = null,
    val genres: Set<Genre> = Genre.entries.toSet(),
    val query: String = "",
    val onlyMatched: Boolean = false,
    val settings: Settings = Settings(),
)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val http = Http()
    private val repository = ReleaseRepository(http)
    private val ytCache = LinkCache(app, "yt_matches")
    private val spotifyCache = LinkCache(app, "spotify_matches")
    private val ytMatcher = YouTubeMusicMatcher(http, ytCache)
    private val spotifyMatcher = SpotifyMatcher(http, spotifyCache)
    private val scanStore = ScanStore(app)
    private val settingsStore = SettingsStore(app)

    private val _state = MutableStateFlow(UiState(settings = settingsStore.load()))
    val state: StateFlow<UiState> = _state

    /** Gefundene Links pro Release-Key. */
    val ytLinks = mutableStateMapOf<String, MusicLink>()
    val spotifyLinks = mutableStateMapOf<String, MusicLink>()
    val ytMatching = mutableStateMapOf<String, Boolean>()
    val spotifyMatching = mutableStateMapOf<String, Boolean>()

    private var scanJob: Job? = null
    private var matchJob: Job? = null
    private val ytPermits = Semaphore(3)
    private val spotifyPermits = Semaphore(2)

    init {
        selectPeriod(Period.WEEK)
    }

    fun selectPeriod(period: Period) {
        scanJob?.cancel()
        _state.update { it.copy(period = period, loading = false, progress = null, error = null) }
        val cached = scanStore.load(period)
        if (cached != null) {
            _state.update { it.copy(releases = cached.releases, scannedAt = cached.scannedAt, warnings = cached.warnings) }
            startMatching(cached.releases)
            if (System.currentTimeMillis() - cached.scannedAt > STALE_MS) scan()
        } else {
            _state.update { it.copy(releases = emptyList(), scannedAt = null, warnings = emptyList()) }
            scan()
        }
    }

    fun scan() {
        scanJob?.cancel()
        val period = _state.value.period
        val settings = _state.value.settings
        scanJob = viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null, progress = "Scan startet …") }
            try {
                val result = repository.scan(period, settings) { msg -> _state.update { it.copy(progress = msg) } }
                scanStore.save(result)
                _state.update {
                    it.copy(
                        releases = result.releases,
                        scannedAt = result.scannedAt,
                        warnings = result.warnings,
                        loading = false,
                        progress = null,
                        error = if (result.releases.isEmpty() && result.warnings.isNotEmpty()) "Keine Daten – Netzwerk prüfen." else null,
                    )
                }
                startMatching(result.releases)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, progress = null, error = e.message ?: e.javaClass.simpleName) }
            }
        }
    }

    private fun startMatching(releases: List<Release>) {
        releases.forEach { r ->
            ytMatcher.cached(r)?.let { ytLinks[r.key] = it }
            spotifyMatcher.cached(r)?.let { spotifyLinks[r.key] = it }
        }
        matchJob?.cancel()
        if (!_state.value.settings.autoMatch) return
        matchJob = viewModelScope.launch {
            releases.forEach { r -> launch { matchRelease(r, force = false) } }
        }
    }

    /** Erneuter Abgleich eines einzelnen Release (Button in der Karte). */
    fun retryMatch(release: Release) {
        viewModelScope.launch { matchRelease(release, force = true) }
    }

    private suspend fun matchRelease(release: Release, force: Boolean) {
        matchYouTube(release, force)
        matchSpotify(release, force)
    }

    private suspend fun matchYouTube(release: Release, force: Boolean) {
        ytPermits.withPermit {
            if (!force && ytLinks[release.key]?.direct == true) return
            if (ytMatching[release.key] == true) return
            ytMatching[release.key] = true
            try {
                ytLinks[release.key] = ytMatcher.match(release, force)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Fallback ist der Such-Link (siehe ytLinkFor)
            } finally {
                ytMatching.remove(release.key)
            }
        }
    }

    private suspend fun matchSpotify(release: Release, force: Boolean) {
        spotifyPermits.withPermit {
            if (!force && spotifyLinks[release.key]?.direct == true) return
            if (spotifyMatching[release.key] == true) return
            spotifyMatching[release.key] = true
            try {
                spotifyMatcher.match(release, ytLinks[release.key], _state.value.settings, force)
                    ?.let { spotifyLinks[release.key] = it }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Fallback ist der Such-Link (siehe spotifyLinkFor)
            } finally {
                spotifyMatching.remove(release.key)
            }
        }
    }

    fun ytLinkFor(release: Release): MusicLink =
        ytLinks[release.key] ?: MusicLink(YouTubeMusicMatcher.searchUrl(release), direct = false)

    fun spotifyLinkFor(release: Release): MusicLink =
        spotifyLinks[release.key] ?: MusicLink(SpotifyMatcher.searchUrl(release), direct = false)

    fun hasDirectLink(release: Release): Boolean =
        ytLinks[release.key]?.direct == true || spotifyLinks[release.key]?.direct == true

    fun setQuery(q: String) = _state.update { it.copy(query = q) }

    fun toggleGenre(g: Genre) = _state.update {
        val next = if (g in it.genres) it.genres - g else it.genres + g
        it.copy(genres = next.ifEmpty { Genre.entries.toSet() })
    }

    fun toggleOnlyMatched() = _state.update { it.copy(onlyMatched = !it.onlyMatched) }

    fun updateSettings(s: Settings) {
        settingsStore.save(s)
        _state.update { it.copy(settings = s) }
        if (s.autoMatch) startMatching(_state.value.releases)
    }

    fun clearMatchCache() {
        ytCache.clear()
        spotifyCache.clear()
        ytLinks.clear()
        spotifyLinks.clear()
        startMatching(_state.value.releases)
    }

    private companion object {
        const val STALE_MS = 6L * 60 * 60 * 1000
    }
}
