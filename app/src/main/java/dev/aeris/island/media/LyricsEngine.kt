package dev.aeris.island.media

/**
 * Parses lyrics exposed by the active media app as ordinary or enhanced LRC.
 * Enhanced LRC word timestamps (for example <00:12.30>) enable word-by-word
 * highlighting. Standard LRC safely falls back to line-by-line highlighting.
 */
data class LyricWord(val text: String, val startMs: Long)
data class LyricLine(val startMs: Long, val words: List<LyricWord>, val plainText: String)
data class LyricsFrame(
    val previous: String,
    val current: String,
    val next: String,
    val words: List<LyricWord>,
    val activeWordIndex: Int,
    val wordSynced: Boolean
)

object LyricsEngine {
    private val lineTime = Regex("\\[(\\d{1,2}):(\\d{2})(?:[.:](\\d{1,3}))?\\]")
    private val wordTime = Regex("<(\\d{1,2}):(\\d{2})(?:[.:](\\d{1,3}))?>")

    fun parse(raw: String?): List<LyricLine> {
        if (raw.isNullOrBlank()) return emptyList()
        val result = ArrayList<LyricLine>()
        raw.lineSequence().forEach { original ->
            val row = original.trim()
            if (row.isBlank() || row.startsWith("#") || row.startsWith("[ar:", true) ||
                row.startsWith("[ti:", true) || row.startsWith("[al:", true) ||
                row.startsWith("[by:", true) || row.startsWith("[re:", true)) return@forEach

            val lineTimes = lineTime.findAll(row).map { match ->
                timestamp(match.groupValues[1], match.groupValues[2], match.groupValues[3])
            }.toList()
            val body = lineTime.replace(row, "").trim()
            val wordMatches = wordTime.findAll(body).toList()
            val words = if (wordMatches.isNotEmpty()) {
                wordMatches.mapIndexedNotNull { index, match ->
                    val start = timestamp(match.groupValues[1], match.groupValues[2], match.groupValues[3])
                    val end = if (index + 1 < wordMatches.size) wordMatches[index + 1].range.first else body.length
                    val text = body.substring(match.range.last + 1, end).trim()
                    if (text.isBlank()) null else LyricWord(text, start)
                }
            } else emptyList()

            val cleanBody = if (words.isNotEmpty()) words.joinToString(" ") { it.text }
                else wordTime.replace(body, "").trim()
            if (cleanBody.isBlank()) return@forEach
            val starts = lineTimes.ifEmpty { words.firstOrNull()?.let { listOf(it.startMs) } ?: emptyList() }
            starts.forEach { start -> result += LyricLine(start, words, cleanBody) }
        }
        return result.sortedBy { it.startMs }.distinctBy { it.startMs to it.plainText }
    }

    fun frame(lines: List<LyricLine>, positionMs: Long): LyricsFrame? {
        if (lines.isEmpty()) return null
        val index = lines.indexOfLast { it.startMs <= positionMs }.let { if (it < 0) 0 else it }
        val line = lines[index]
        val wordSynced = line.words.isNotEmpty()
        val activeWordIndex = if (wordSynced) line.words.indexOfLast { it.startMs <= positionMs } else -1
        return LyricsFrame(
            previous = lines.getOrNull(index - 1)?.plainText.orEmpty(),
            current = line.plainText,
            next = lines.getOrNull(index + 1)?.plainText.orEmpty(),
            words = line.words,
            activeWordIndex = activeWordIndex,
            wordSynced = wordSynced
        )
    }

    private fun timestamp(minutes: String, seconds: String, fraction: String): Long {
        val fractionMs = when (fraction.length) {
            1 -> fraction.toLongOrNull()?.times(100L) ?: 0L
            2 -> fraction.toLongOrNull()?.times(10L) ?: 0L
            else -> fraction.take(3).toLongOrNull() ?: 0L
        }
        return ((minutes.toLongOrNull() ?: 0L) * 60_000L) +
            ((seconds.toLongOrNull() ?: 0L) * 1_000L) + fractionMs
    }
}
