package dev.aeris.island.media

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/** Optional async LRCLIB lookup. Caller receives cached text immediately or null while fetching. */
object LyricsProvider {
    private const val BASE = "https://lrclib.net/api"
    private val executor = Executors.newSingleThreadExecutor { task ->
        Thread(task, "AERIS-Lyrics").apply { isDaemon = true }
    }
    private val cache = ConcurrentHashMap<String, String>()
    private val inFlight = ConcurrentHashMap.newKeySet<String>()

    fun getOrFetch(title: String, artist: String, durationMs: Long): String? {
        val t = title.trim()
        val a = artist.trim()
        if (t.isBlank() || a.isBlank() || t.equals("Unknown track", true)) return null
        val key = key(t, a, durationMs)
        cache[key]?.let { return it }
        if (inFlight.add(key)) {
            executor.execute {
                try { fetch(t, a, durationMs)?.takeIf(String::isNotBlank)?.let { cache[key] = it } }
                catch (_: Exception) { /* A lyrics lookup must never crash media polling. */ }
                finally { inFlight.remove(key) }
            }
        }
        return null
    }

    private fun fetch(title: String, artist: String, durationMs: Long): String? {
        val duration = durationMs / 1000.0
        val exact = "$BASE/get?track_name=${encode(title)}&artist_name=${encode(artist)}" +
            if (durationMs > 0L) "&duration=$duration" else ""
        lyrics(requestObject(exact))?.let { return it }
        val search = requestArray("$BASE/search?track_name=${encode(title)}&artist_name=${encode(artist)}") ?: return null
        val targetTitle = normalize(title)
        val targetArtist = normalize(artist)
        val best = (0 until search.length()).mapNotNull(search::optJSONObject)
            .filter { row -> lyrics(row) != null }
            .maxByOrNull { row ->
                var score = 0
                val candidateTitle = normalize(row.optString("trackName"))
                val candidateArtist = normalize(row.optString("artistName"))
                if (candidateTitle == targetTitle) score += 5
                else if (candidateTitle.contains(targetTitle) || targetTitle.contains(candidateTitle)) score += 2
                if (candidateArtist == targetArtist) score += 5
                else if (candidateArtist.contains(targetArtist) || targetArtist.contains(candidateArtist)) score += 2
                val candidateDuration = (row.optDouble("duration", 0.0) * 1000).toLong()
                if (durationMs > 0 && candidateDuration > 0) {
                    val diff = kotlin.math.abs(candidateDuration - durationMs)
                    if (diff <= 5_000) score += 3 else if (diff <= 15_000) score += 1
                }
                if (!row.optString("syncedLyrics").isNullOrBlank()) score += 2
                score
            }
        return lyrics(best)
    }

    private fun lyrics(json: JSONObject?): String? = json?.optString("syncedLyrics")?.takeIf { it.isNotBlank() }
        ?: json?.optString("plainLyrics")?.takeIf { it.isNotBlank() }

    private fun requestObject(address: String): JSONObject? {
        val connection = open(address)
        return try {
            if (connection.responseCode !in 200..299) null
            else JSONObject(connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() })
        } catch (_: Exception) { null } finally { connection.disconnect() }
    }

    private fun requestArray(address: String): JSONArray? {
        val connection = open(address)
        return try {
            if (connection.responseCode !in 200..299) null
            else JSONArray(connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() })
        } catch (_: Exception) { null } finally { connection.disconnect() }
    }

    private fun open(address: String): HttpURLConnection = (URL(address).openConnection() as HttpURLConnection).apply {
        requestMethod = "GET"
        connectTimeout = 7_000
        readTimeout = 7_000
        setRequestProperty("Accept", "application/json")
        setRequestProperty("User-Agent", "AERIS-Android/3.0 (optional lyrics lookup)")
    }

    private fun encode(value: String) = URLEncoder.encode(value, "UTF-8")
    private fun normalize(value: String) = value.lowercase(Locale.ROOT)
        .replace(Regex("\\([^)]*\\)|\\[[^]]*\\]"), " ")
        .replace(Regex("[^\\p{L}\\p{N}]"), " ")
        .replace(Regex("\\s+"), " ").trim()
    private fun key(title: String, artist: String, duration: Long) = "${normalize(title)}|${normalize(artist)}|${duration / 5_000L}"
}
