package com.dnbscanner.app.data

import android.content.Context
import com.dnbscanner.app.model.MusicLink
import org.json.JSONObject

/** Speichert gefundene Links dauerhaft; Fehlschläge werden nach 3 Tagen erneut versucht. */
class LinkCache(context: Context, name: String) {
    private val prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)

    fun get(key: String): MusicLink? {
        val raw = prefs.getString(key, null) ?: return null
        val json = runCatching { JSONObject(raw) }.getOrNull() ?: return null
        val direct = json.optBoolean("direct")
        if (!direct && System.currentTimeMillis() - json.optLong("ts") > RETRY_MS) return null
        return MusicLink(json.optString("url"), direct, json.optString("text").ifBlank { null })
    }

    fun put(key: String, link: MusicLink) {
        val json = JSONObject()
            .put("url", link.url)
            .put("direct", link.direct)
            .put("text", link.matchedText ?: "")
            .put("ts", System.currentTimeMillis())
        prefs.edit().putString(key, json.toString()).apply()
    }

    fun clear() = prefs.edit().clear().apply()

    private companion object {
        const val RETRY_MS = 3L * 24 * 60 * 60 * 1000
    }
}
