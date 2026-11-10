package dev.aeris.island.media

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * Fetches synced/plain lyrics from LRCLIB without blocking the UI/media polling thread.
 *
 * getOrFetch() returns a cached result immediately (or null while the first request runs).
 * MediaBridge is polled by the app, so a later poll picks up the downloaded lyrics.
 */
object LyricsProvider {
    private const val API_BASE = "https://lrclib.net/api"
    private const val CONNECT_TIMEOUT_MS = 7_000
    private const val READ_TIMEOUT_MS = 7_000

    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "AERIS-Lyrics").apply { isDaemon = true }
    }
    private val cache = ConcurrentHashMap<String, String>()
    private val inFlight = ConcurrentHashMap.newKeySet<String>()

    fun getOrFetch(title: String, artist: String, durationMs: Long): String? {
        val cleanTitle = title.trim()
        val cleanArtist = artist.trim()
        if (cleanTitle.isBlank() || cleanArtist.isBlank() ||
            cleanTitle.equals("Unknown track", ignoreCase = true)) return null

        val key = cacheKey(cleanTitle, cleanArtist, durationMs)
        cache[key]?.let { return it }

        if (inFlight.add(key)) {
            executor.execute {
                try {
                    val lyrics = fetch(cleanTitle, cleanArtist, durationMs)
                    if (!lyrics.isNullOrBlank()) cache[key] = lyrics
                } finally {
                    inFlight.remove(key)
                }
            }
        }
        return null
    }

    private fun fetch(title: String, artist: String, durationMs: Long): String? {
        // First try the exact LRCLIB lookup, which can return syncedLyrics.
        val exact = requestObject(
            "$API_BASE/get?track_name=${encode(title)}" +
                "&artist_name=${encode(artist)}" +
                if (durationMs > 0L) "&duration=${durationMs / 1000.0}" else ""
        )
        lyricsFrom(exact)?.let { return it }

        // Search fallback helps when the music app reports slightly different metadata.
        val searchUrl = "$API_BASE/search?track_name=${encode(title)}&artist_name=${encode(artist)}"
        val results = requestArray(searchUrl) ?: return null
        val normalizedTitle = normalize(title)
        val normalizedArtist = normalize(artist)

        val candidates = (0 until results.length()).mapNotNull { index ->
            results.optJSONObject(index)
        }
        val best = candidates.maxByOrNull { item ->
            var score = 0
            val candidateTitle = normalize(item.optString("trackName"))
            val candidateArtist = normalize(item.optString("artistName"))
            if (candidateTitle == normalizedTitle) score += 5
            else if (candidateTitle.contains(normalizedTitle) || normalizedTitle.contains(candidateTitle)) score += 2
            if (candidateArtist == normalizedArtist) score += 5
            else if (candidateArtist.contains(normalizedArtist) || normalizedArtist.contains(candidateArtist)) score += 2
            val candidateDuration = (item.optDouble("duration", 0.0) * 1000).toLong()
            if (durationMs > 0 && candidateDuration > 0) {
                val difference = kotlin.math.abs(candidateDuration - durationMs)
                if (difference <= 5_000) score += 3
                else if (difference <= 15_000) score += 1
            }
            if (!item.optString("syncedLyrics").isNullOrBlank()) score += 2
            score
        }
        return lyricsFrom(best)
    }

    private fun lyricsFrom(json: JSONObject?): String? {
        if (json == null) return null
        // Prefer synchronized LRC; plain lyrics are a usable fallback.
        return json.optString("syncedLyrics").takeIf { it.isNotBlank() }
            ?: json.optString("plainLyrics").takeIf { it.isNotBlank() }
    }

    private fun requestObject(url: String): JSONObject? {
        val connection = open(url)
        return try {
            if (connection.responseCode !in 200..299) return null
            JSONObject(connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() })
        } catch (_: Exception) {
            null
        } finally {
            connection.disconnect()
        }
    }

    private fun requestArray(url: String): JSONArray? {
        val connection = open(url)
        return try {
            if (connection.responseCode !in 200..299) return null
            JSONArray(connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() })
        } catch (_: Exception) {
            null
        } finally {
            connection.disconnect()
        }
    }

    private fun open(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "AERIS-Android/2.0 (lyrics lookup)")
        }

    private fun encode(value: String): String =
        URLEncoder.encode(value, "UTF-8")

    private fun normalize(value: String): String =
        value.lowercase(Locale.ROOT)
            .replace(Regex("\\\\([^)]*\\\\)|\\\\[[^]]*\\\\]"), " ")
            .replace(Regex("[^\\\\p{L}\\\\p{N}]"), " ")
            .replace(Regex("\\\\s+"), " ")
            .trim()

    private fun cacheKey(title: String, artist: String, durationMs: Long): String =
        "${normalize(title)}|${normalize(artist)}|${durationMs / 5_000}"
}
