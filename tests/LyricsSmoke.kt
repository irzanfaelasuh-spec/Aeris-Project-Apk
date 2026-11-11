import dev.aeris.island.media.LyricsEngine
fun main() {
    val standard = LyricsEngine.parse("[00:01.20]Hello\n[00:02.40]World")
    check(standard.size == 2)
    check(standard.first().startMs == 1200L)
    check(standard.first().text == "Hello")
    val enhanced = LyricsEngine.parse("[00:03.00]<00:03.00>Hello <00:03.50>world")
    check(enhanced.size == 1)
    check(enhanced.first().words.size == 2)
    check(enhanced.first().words.last().startMs == 3500L)
    check(LyricsEngine.frame(standard, 2500L)?.current == "World")
    println("Lyrics smoke tests passed")
}
