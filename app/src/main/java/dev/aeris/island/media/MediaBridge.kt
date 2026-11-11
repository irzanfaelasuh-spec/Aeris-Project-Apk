package dev.aeris.island.media

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.SystemClock
import dev.aeris.island.AerisPrefs
import dev.aeris.island.notifications.AerisNotificationListener

data class MediaSnapshot(
    val title: String,
    val artist: String,
    val packageName: String,
    val playing: Boolean,
    val durationMs: Long,
    val positionMs: Long,
    val artwork: Bitmap?,
    val lyrics: String?,
    val canToggle: Boolean,
    val canPrevious: Boolean,
    val canNext: Boolean,
    val canSeek: Boolean
)

object MediaBridge {
    fun current(context: Context): MediaSnapshot? {
        if (!AerisNotificationListener.isEnabled(context)) return null
        return try {
            val manager = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
            val component = ComponentName(context, AerisNotificationListener::class.java)
            val controller = manager.getActiveSessions(component)
                .maxByOrNull { c -> if (c.playbackState?.state == PlaybackState.STATE_PLAYING) 10 else 0 }
                ?.takeIf { it.metadata != null && it.playbackState?.state != PlaybackState.STATE_NONE }
                ?: return null
            val meta = controller.metadata
            val state = controller.playbackState
            val duration = meta?.getLong(MediaMetadata.METADATA_KEY_DURATION)?.coerceAtLeast(0L) ?: 0L
            val rawPosition = state?.position?.coerceAtLeast(0L) ?: 0L
            val position = if (state?.state == PlaybackState.STATE_PLAYING && (state.lastPositionUpdateTime) > 0L) {
                rawPosition + ((SystemClock.elapsedRealtime() - state.lastPositionUpdateTime) * state.playbackSpeed).toLong().coerceAtLeast(0L)
            } else rawPosition
            val actions = state?.actions ?: 0L
            val title = meta?.getString(MediaMetadata.METADATA_KEY_TITLE)?.takeIf { it.isNotBlank() } ?: "Unknown track"
            val artist = meta?.getString(MediaMetadata.METADATA_KEY_ARTIST)?.takeIf { it.isNotBlank() } ?: controller.packageName
            val suppliedLyrics = meta?.getText("android.media.metadata.LYRICS")?.toString()
                ?: meta?.getText("lyrics")?.toString()
            val lyrics = suppliedLyrics?.takeIf { it.isNotBlank() } ?: if (AerisPrefs.bool(context, "online_lyrics", false)) {
                LyricsProvider.getOrFetch(title, artist, duration)
            } else null
            MediaSnapshot(
                title = title,
                artist = artist,
                packageName = controller.packageName,
                playing = state?.state == PlaybackState.STATE_PLAYING || state?.state == PlaybackState.STATE_BUFFERING,
                durationMs = duration,
                positionMs = if (duration > 0L) position.coerceIn(0L, duration) else position,
                artwork = meta?.getBitmap(MediaMetadata.METADATA_KEY_ART) ?: meta?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART),
                lyrics = lyrics,
                canToggle = (actions and (PlaybackState.ACTION_PLAY_PAUSE or PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE)) != 0L,
                canPrevious = (actions and PlaybackState.ACTION_SKIP_TO_PREVIOUS) != 0L,
                canNext = (actions and PlaybackState.ACTION_SKIP_TO_NEXT) != 0L,
                canSeek = (actions and PlaybackState.ACTION_SEEK_TO) != 0L
            )
        } catch (_: Exception) { null }
    }

    fun toggle(context: Context) = withController(context) { c ->
        if (c.playbackState?.state == PlaybackState.STATE_PLAYING) c.transportControls.pause() else c.transportControls.play()
    }
    fun previous(context: Context) = withController(context) { it.transportControls.skipToPrevious() }
    fun next(context: Context) = withController(context) { it.transportControls.skipToNext() }
    fun seek(context: Context, ms: Long) = withController(context) { it.transportControls.seekTo(ms.coerceAtLeast(0L)) }

    private inline fun withController(context: Context, action: (MediaController) -> Unit): Boolean {
        if (!AerisNotificationListener.isEnabled(context)) return false
        return try {
            val manager = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
            val controller = manager.getActiveSessions(ComponentName(context, AerisNotificationListener::class.java)).firstOrNull() ?: return false
            action(controller)
            true
        } catch (_: Exception) { false }
    }
}
