package com.dnbscanner.app.ui

import android.app.Application
import androidx.compose.runtime.mutableStateMapOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dnbscanner.app.data.Http
import com.dnbscanner.app.data.MatchCache
import com.dnbscanner.app.data.ReleaseRepository
import com.dnbscanner.app.data.ScanStore
import com.dnbscanner.app.data.Settings
import com.dnbscanner.app.data.SettingsStore
import com.dnbscanner.app.data.YouTubeMusicMatcher
import com.dnbscanner.app.model.Genre
import com.dnbscanner.app.model.Period
import com.dnbscanner.app.model.Release
import com.dnbscanner.app.model.YtLink
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
    private val matchCache = MatchCache(app)
    private val matcher = YouTubeMusicMatcher(http, matchCache)
    private val scanStore = ScanStore(app)
    private val settingsStore = SettingsStore(app)

    private val _state = MutableStateFlow(UiState(settings = settingsStore.load()))
    val state: StateFlow<UiState> = _state

    /** YouTube-Music-Links pro Release-Key. */
    val links = mutableStateMapOf<String, YtLink>()
    val matching = mutableStateMapOf<String, Boolean>()

    private var scanJob: Job? = null
    private var matchJob: Job? = null
    private val matchPermits = Semaphore(3)

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
        releases.forEach { r -> matcher.cached(r)?.let { links[r.key] = it } }
        matchJob?.cancel()
        if (!_state.value.settings.autoMatch) return
        matchJob = viewModelScope.launch {
            releases.filter { it.key !in links }.forEach { r ->
                launch { matchOne(r) }
            }
        }
    }

    /** Sucht einen einzelnen Release (auch manuell per Button, wenn Auto-Match aus ist). */
    fun match(release: Release) {
        if (matching[release.key] == true) return
        viewModelScope.launch { matchOne(release, force = true) }
    }

    private suspend fun matchOne(release: Release, force: Boolean = false) {
        matchPermits.withPermit {
            if (links[release.key]?.direct == true) return
            matching[release.key] = true
            try {
                links[release.key] = matcher.match(release, force)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                links[release.key] = YtLink(YouTubeMusicMatcher.searchUrl(release), direct = false)
            } finally {
                matching.remove(release.key)
            }
        }
    }

    fun linkFor(release: Release): YtLink =
        links[release.key] ?: YtLink(YouTubeMusicMatcher.searchUrl(release), direct = false)

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
        matchCache.clear()
        links.clear()
        startMatching(_state.value.releases)
    }

    private companion object {
        const val STALE_MS = 6L * 60 * 60 * 1000
    }
}
