package com.dnbscanner.app.data

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

class Http(private val client: OkHttpClient = defaultClient()) {

    fun get(url: String, headers: Map<String, String> = emptyMap()): String {
        val builder = Request.Builder().url(url)
        headers.forEach { (k, v) -> builder.header(k, v) }
        return execute(builder.build())
    }

    fun postJson(url: String, body: String, headers: Map<String, String> = emptyMap()): String {
        val builder = Request.Builder().url(url).post(body.toRequestBody(JSON))
        headers.forEach { (k, v) -> builder.header(k, v) }
        return execute(builder.build())
    }

    private fun execute(request: Request): String {
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code} für ${request.url.host}")
            }
            return response.body?.string() ?: throw IOException("Leere Antwort von ${request.url.host}")
        }
    }

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()

        const val BROWSER_UA =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Mobile Safari/537.36"
        const val APP_UA = "DnBScanner/0.1 ( https://github.com/eightbitspliff/dnb-scanner )"

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }
}
