package dev.aeris.island.media

data class LyricWord(val text: String, val startMs: Long)
data class LyricLine(val startMs: Long, val words: List<LyricWord>, val text: String)
data class LyricsFrame(val previous: String, val current: String, val next: String, val words: List<LyricWord>, val activeWordIndex: Int)

object LyricsEngine {
    private val lineStamp = Regex("\\[(\\d{1,2}):(\\d{2})(?:[.:](\\d{1,3}))?\\]")
    private val wordStamp = Regex("<(\\d{1,2}):(\\d{2})(?:[.:](\\d{1,3}))?>")

    fun parse(raw: String?): List<LyricLine> {
        if (raw.isNullOrBlank()) return emptyList()
        val result = mutableListOf<LyricLine>()
        raw.lineSequence().forEach { source ->
            val row = source.trim()
            if (row.isBlank() || row.startsWith("#") || row.startsWith("[ar:", true) ||
                row.startsWith("[ti:", true) || row.startsWith("[al:", true) || row.startsWith("[by:", true)) return@forEach
            val times = lineStamp.findAll(row).map { stamp(it.groupValues) }.toList()
            val body = lineStamp.replace(row, "").trim()
            val matches = wordStamp.findAll(body).toList()
            val words = matches.mapIndexedNotNull { index, match ->
                val end = matches.getOrNull(index + 1)?.range?.first ?: body.length
                val text = body.substring(match.range.last + 1, end).trim()
                if (text.isBlank()) null else LyricWord(text, stamp(match.groupValues))
            }
            val text = if (words.isEmpty()) wordStamp.replace(body, "").trim() else words.joinToString(" ") { it.text }
            if (text.isBlank()) return@forEach
            val starts = times.ifEmpty { words.firstOrNull()?.let { listOf(it.startMs) } ?: emptyList() }
            starts.forEach { result += LyricLine(it, words, text) }
        }
        return result.sortedBy { it.startMs }.distinctBy { it.startMs to it.text }
    }

    fun frame(lines: List<LyricLine>, positionMs: Long): LyricsFrame? {
        if (lines.isEmpty()) return null
        val index = lines.indexOfLast { it.startMs <= positionMs }.coerceAtLeast(0)
        val line = lines[index]
        return LyricsFrame(
            previous = lines.getOrNull(index - 1)?.text.orEmpty(),
            current = line.text,
            next = lines.getOrNull(index + 1)?.text.orEmpty(),
            words = line.words,
            activeWordIndex = line.words.indexOfLast { it.startMs <= positionMs }
        )
    }

    private fun stamp(groups: List<String>): Long {
        val fraction = groups.getOrNull(3).orEmpty()
        val fractionMs = when (fraction.length) {
            1 -> fraction.toLongOrNull()?.times(100L) ?: 0L
            2 -> fraction.toLongOrNull()?.times(10L) ?: 0L
            else -> fraction.take(3).toLongOrNull() ?: 0L
        }
        return (groups.getOrNull(1)?.toLongOrNull() ?: 0L) * 60_000L +
            (groups.getOrNull(2)?.toLongOrNull() ?: 0L) * 1_000L + fractionMs
    }
}
