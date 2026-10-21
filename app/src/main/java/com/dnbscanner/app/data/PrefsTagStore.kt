package com.dnbscanner.app.data

import android.content.Context

class PrefsTagStore(context: Context) : TagStore {
    private val prefs = context.getSharedPreferences("bandcamp_tags", Context.MODE_PRIVATE)

    override fun get(url: String): List<String>? =
        prefs.getString(url, null)?.split('\n')?.filter { it.isNotBlank() }

    override fun put(url: String, tags: List<String>) {
        prefs.edit().putString(url, tags.joinToString("\n")).apply()
    }
}
