package dev.aeris.island.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsEngineTest {
    @Test
    fun parsesStandardLineTimedLyrics() {
        val lines = LyricsEngine.parse("[00:01.20]first line\\n[00:03.40]second line")
        assertEquals(2, lines.size)
        assertEquals(1200L, lines[0].startMs)
        assertEquals("first line", lines[0].text)
        assertEquals(0, lines[0].words.size)
    }

    @Test
    fun parsesEnhancedWordTimedLyrics() {
        val lines = LyricsEngine.parse(
            "[00:01.00]<00:01.00>first <00:01.55>second\\n" +
                "[00:02.00]<00:02.00>third <00:02.45>fourth"
        )
        assertEquals(2, lines.size)
        assertTrue(lines[0].words.isNotEmpty())
        val frame = LyricsEngine.frame(lines, 1700L)
        assertNotNull(frame)
        assertEquals("first second", frame!!.current)
        assertEquals(1, frame.activeWordIndex)
        assertTrue(frame.activeWordIndex >= 0)
    }

    @Test
    fun selectsCurrentLineAtPlaybackPosition() {
        val lines = LyricsEngine.parse("[00:01.00]alpha\\n[00:02.00]beta\\n[00:03.00]gamma")
        val frame = LyricsEngine.frame(lines, 2500L)
        assertNotNull(frame)
        assertEquals("beta", frame!!.current)
        assertEquals("alpha", frame.previous)
        assertEquals("gamma", frame.next)
        assertFalse(frame.words.isNotEmpty())
    }
}
