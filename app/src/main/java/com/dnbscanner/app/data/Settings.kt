package com.dnbscanner.app.data

import android.content.Context

data class Settings(
    val useBandcamp: Boolean = true,
    val useMusicBrainz: Boolean = true,
    val bandcampTags: List<String> = DEFAULT_TAGS,
    /** Seiten (à 60 Releases) pro Bandcamp-Tag für "Woche"; Monat/3 Monate skalieren hoch. */
    val bandcampPagesPerWeek: Int = 3,
    val autoMatch: Boolean = true,
    val spotifyClientId: String = "",
    val spotifyClientSecret: String = "",
) {
    val hasSpotifyCredentials: Boolean
        get() = spotifyClientId.isNotBlank() && spotifyClientSecret.isNotBlank()

    companion object {
        val DEFAULT_TAGS = listOf("drum-and-bass", "drum-bass", "jungle")
    }
}

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun load(): Settings = Settings(
        useBandcamp = prefs.getBoolean("bandcamp", true),
        useMusicBrainz = prefs.getBoolean("musicbrainz", true),
        bandcampTags = prefs.getString("tags", null)
            ?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }
            ?.takeIf { it.isNotEmpty() } ?: Settings.DEFAULT_TAGS,
        bandcampPagesPerWeek = prefs.getInt("pages", 3).coerceIn(1, 20),
        autoMatch = prefs.getBoolean("automatch", true),
        spotifyClientId = prefs.getString("spotify_id", "").orEmpty(),
        spotifyClientSecret = prefs.getString("spotify_secret", "").orEmpty(),
    )

    /** Zuletzt gewählter Jahresbereich für den Zeitraum "Jahre". */
    fun loadYears(): IntRange {
        val now = java.time.LocalDate.now().year
        val from = prefs.getInt("year_from", now - 5)
        val to = prefs.getInt("year_to", now)
        return from..to
    }

    fun saveYears(years: IntRange) {
        prefs.edit().putInt("year_from", years.first).putInt("year_to", years.last).apply()
    }

    fun save(s: Settings) {
        prefs.edit()
            .putBoolean("bandcamp", s.useBandcamp)
            .putBoolean("musicbrainz", s.useMusicBrainz)
            .putString("tags", s.bandcampTags.joinToString(","))
            .putInt("pages", s.bandcampPagesPerWeek)
            .putBoolean("automatch", s.autoMatch)
            .putString("spotify_id", s.spotifyClientId.trim())
            .putString("spotify_secret", s.spotifyClientSecret.trim())
            .apply()
    }
}
