package com.dnbscanner.app.data

import android.content.Context
import com.dnbscanner.app.model.Genre
import com.dnbscanner.app.model.Period
import com.dnbscanner.app.model.Release
import com.dnbscanner.app.model.Source
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate

/** Letztes Scan-Ergebnis pro Zeitraum als JSON-Datei, damit die Liste beim Start sofort da ist. */
class ScanStore(context: Context) {
    private val dir = File(context.filesDir, "scans").apply { mkdirs() }

    private fun file(period: Period) = File(dir, "${period.name.lowercase()}.json")

    fun save(result: ScanResult) {
        runCatching { file(result.period).writeText(toJson(result).toString()) }
    }

    fun load(period: Period): ScanResult? =
        runCatching { fromJson(period, JSONObject(file(period).readText())) }.getOrNull()

    companion object {
        fun toJson(result: ScanResult): JSONObject {
            val arr = JSONArray()
            result.releases.forEach { r ->
                val urls = JSONObject()
                r.sourceUrls.forEach { (s, u) -> urls.put(s.name, u) }
                arr.put(
                    JSONObject()
                        .put("artist", r.artist)
                        .put("title", r.title)
                        .put("date", r.releaseDate.toString())
                        .put("label", r.label ?: "")
                        .put("type", r.type ?: "")
                        .put("genres", JSONArray(r.genres.map { it.name }))
                        .put("sources", JSONArray(r.sources.map { it.name }))
                        .put("urls", urls)
                        .put("cover", r.coverUrl ?: "")
                        .put("tags", JSONArray(r.tags))
                        .put("dateLabel", r.dateLabel ?: ""),
                )
            }
            return JSONObject()
                .put("version", FORMAT_VERSION)
                .put("rejected", result.rejected)
                .put("range", result.rangeKey)
                .put("scannedAt", result.scannedAt)
                .put("warnings", JSONArray(result.warnings))
                .put("releases", arr)
        }

        /** Version 3: strengere Genre-Prüfung – ältere Ergebnisse werden verworfen. */
        private const val FORMAT_VERSION = 3

        fun fromJson(period: Period, json: JSONObject): ScanResult? {
            if (json.optInt("version") != FORMAT_VERSION) return null
            val arr = json.getJSONArray("releases")
            val releases = (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val urls = o.getJSONObject("urls")
                Release(
                    artist = o.getString("artist"),
                    title = o.getString("title"),
                    releaseDate = LocalDate.parse(o.getString("date")),
                    label = o.optString("label").ifBlank { null },
                    type = o.optString("type").ifBlank { null },
                    genres = o.getJSONArray("genres").let { g -> (0 until g.length()).map { Genre.valueOf(g.getString(it)) }.toSet() },
                    sources = o.getJSONArray("sources").let { s -> (0 until s.length()).map { Source.valueOf(s.getString(it)) }.toSet() },
                    sourceUrls = urls.keys().asSequence().associate { Source.valueOf(it) to urls.getString(it) },
                    coverUrl = o.optString("cover").ifBlank { null },
                    tags = o.optJSONArray("tags")?.let { t -> (0 until t.length()).map { t.getString(it) } } ?: emptyList(),
                    dateLabel = o.optString("dateLabel").ifBlank { null },
                )
            }
            val warnings = json.optJSONArray("warnings")?.let { w -> (0 until w.length()).map { w.getString(it) } } ?: emptyList()
            return ScanResult(period, releases, warnings, json.optLong("scannedAt"), json.optInt("rejected"), json.optString("range"))
        }
    }
}
