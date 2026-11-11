package dev.aeris.island.media

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.SystemClock
import dev.aeris.island.notifications.AerisNotificationListener

data class MediaCustomAction(val action: String, val name: String)

data class MediaSnapshot(
    val title: String,
    val artist: String,
    val album: String,
    val packageName: String,
    val playing: Boolean,
    val durationMs: Long,
    val positionMs: Long,
    val artwork: Bitmap?,
    val lyrics: String? = null,
    val canToggle: Boolean = false,
    val canPrevious: Boolean = false,
    val canNext: Boolean = false,
    val canSeek: Boolean = false,
    val playbackSpeed: Float = 1f,
    val customActions: List<MediaCustomAction> = emptyList()
)

object MediaBridge {
    private const val LYRICS_KEY = "android.media.metadata.LYRICS"

    fun current(context: Context): MediaSnapshot? {
        val controller = activeController(context) ?: return null
        return try {
            val metadata = controller.metadata
            val state = controller.playbackState
            val duration = metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION)?.coerceAtLeast(0L) ?: 0L
            val rawPosition = state?.position?.coerceAtLeast(0L) ?: 0L
            val speed = state?.playbackSpeed ?: 1f
            val advancedPosition = if (isPlaying(state?.state) && (state?.lastPositionUpdateTime ?: 0L) > 0L) {
                rawPosition + ((SystemClock.elapsedRealtime() - state!!.lastPositionUpdateTime) * speed).toLong().coerceAtLeast(0L)
            } else rawPosition
            val position = if (duration > 0L) advancedPosition.coerceIn(0L, duration) else advancedPosition
            val actions = state?.actions ?: 0L
            val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
                ?.takeIf { it.isNotBlank() } ?: "Unknown track"
            val artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)
                ?.takeIf { it.isNotBlank() } ?: controller.packageName
            val textLyrics = metadata?.getText(LYRICS_KEY)?.toString()
                ?: metadata?.getText("lyrics")?.toString()
                ?: state?.extras?.getCharSequence(LYRICS_KEY)?.toString()
                ?: state?.extras?.getCharSequence("lyrics")?.toString()
            // Use lyrics exposed by the player first. If unavailable, fetch them
            // asynchronously from the existing LRCLIB provider; a later media
            // refresh will pick up the provider's cached result.
            val lyrics = textLyrics?.takeIf { it.isNotBlank() }
                ?: LyricsProvider.getOrFetch(title, artist, duration)
            MediaSnapshot(
                title = title,
                artist = artist,
                album = metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM).orEmpty(),
                packageName = controller.packageName,
                playing = isPlaying(state?.state),
                durationMs = duration,
                positionMs = position,
                artwork = metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)
                    ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART),
                lyrics = lyrics,
                canToggle = (actions and (PlaybackState.ACTION_PLAY_PAUSE or PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE)) != 0L,
                canPrevious = (actions and PlaybackState.ACTION_SKIP_TO_PREVIOUS) != 0L,
                canNext = (actions and PlaybackState.ACTION_SKIP_TO_NEXT) != 0L,
                canSeek = (actions and PlaybackState.ACTION_SEEK_TO) != 0L,
                playbackSpeed = speed,
                customActions = state?.customActions.orEmpty().map { MediaCustomAction(it.action, it.name.toString()) }
            )
        } catch (_: SecurityException) {
            null
        } catch (_: RuntimeException) {
            null
        }
    }

    fun toggle(context: Context): Boolean = transport(context) { controller ->
        val state = controller.playbackState?.state
        val requested = if (isPlaying(state)) PlaybackState.ACTION_PAUSE else PlaybackState.ACTION_PLAY
        if (!supports(controller, requested) && !supports(controller, PlaybackState.ACTION_PLAY_PAUSE)) {
            return@transport false
        }
        if (isPlaying(state)) controller.transportControls.pause() else controller.transportControls.play()
        true
    }

    fun next(context: Context): Boolean = transport(context) { controller ->
        if (!supports(controller, PlaybackState.ACTION_SKIP_TO_NEXT)) return@transport false
        controller.transportControls.skipToNext()
        true
    }

    fun previous(context: Context): Boolean = transport(context) { controller ->
        if (!supports(controller, PlaybackState.ACTION_SKIP_TO_PREVIOUS)) return@transport false
        controller.transportControls.skipToPrevious()
        true
    }

    fun seekTo(context: Context, positionMs: Long): Boolean = transport(context) { controller ->
        if (!supports(controller, PlaybackState.ACTION_SEEK_TO)) return@transport false
        val duration = controller.metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: Long.MAX_VALUE
        controller.transportControls.seekTo(positionMs.coerceIn(0L, duration.coerceAtLeast(0L)))
        true
    }

    fun customAction(context: Context, action: String): Boolean = transport(context) { controller ->
        val advertised = controller.playbackState?.customActions.orEmpty().any { it.action == action }
        if (!advertised) return@transport false
        controller.transportControls.sendCustomAction(action, null)
        true
    }

    fun jumpBy(context: Context, deltaMs: Long): Boolean {
        val snapshot = current(context) ?: return false
        if (!snapshot.canSeek) return false
        return seekTo(context, (snapshot.positionMs + deltaMs).coerceAtLeast(0L))
    }

    private inline fun transport(context: Context, action: (MediaController) -> Boolean): Boolean {
        val controller = activeController(context) ?: return false
        return try { action(controller) } catch (_: Exception) { false }
    }

    private fun activeController(context: Context): MediaController? {
        if (!AerisNotificationListener.isEnabled(context)) return null
        return try {
            val manager = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
            val sessions = manager.getActiveSessions(ComponentName(context, AerisNotificationListener::class.java))
            sessions.maxByOrNull { controller ->
                val state = controller.playbackState
                var score = when (state?.state) {
                    PlaybackState.STATE_PLAYING -> 100
                    PlaybackState.STATE_BUFFERING, PlaybackState.STATE_CONNECTING -> 80
                    PlaybackState.STATE_PAUSED -> 40
                    PlaybackState.STATE_STOPPED -> 10
                    else -> 0
                }
                val available = state?.actions ?: 0L
                if ((available and (PlaybackState.ACTION_PLAY_PAUSE or PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE)) != 0L) score += 5
                if ((available and PlaybackState.ACTION_SKIP_TO_PREVIOUS) != 0L) score += 8
                if ((available and PlaybackState.ACTION_SKIP_TO_NEXT) != 0L) score += 8
                if ((available and PlaybackState.ACTION_SEEK_TO) != 0L) score += 4
                if (controller.metadata?.getString(MediaMetadata.METADATA_KEY_TITLE).isNullOrBlank().not()) score += 10
                score
            }?.takeIf { controller ->
                val playbackState = controller.playbackState?.state
                playbackState == PlaybackState.STATE_PLAYING ||
                    playbackState == PlaybackState.STATE_BUFFERING ||
                    playbackState == PlaybackState.STATE_CONNECTING ||
                    playbackState == PlaybackState.STATE_PAUSED
            }?.takeIf { it.metadata != null }
        } catch (_: Exception) {
            null
        }
    }

    private fun supports(controller: MediaController, action: Long): Boolean =
        ((controller.playbackState?.actions ?: 0L) and action) != 0L

    private fun isPlaying(state: Int?): Boolean = state == PlaybackState.STATE_PLAYING ||
        state == PlaybackState.STATE_BUFFERING || state == PlaybackState.STATE_CONNECTING
}
