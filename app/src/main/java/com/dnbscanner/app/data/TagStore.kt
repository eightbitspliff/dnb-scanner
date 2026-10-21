package com.dnbscanner.app.data

/** Speicher für bereits geladene Bandcamp-Tags (pro Release-URL). */
interface TagStore {
    fun get(url: String): List<String>?
    fun put(url: String, tags: List<String>)
}

class MemoryTagStore : TagStore {
    private val map = java.util.concurrent.ConcurrentHashMap<String, List<String>>()
    override fun get(url: String) = map[url]
    override fun put(url: String, tags: List<String>) { map[url] = tags }
}
