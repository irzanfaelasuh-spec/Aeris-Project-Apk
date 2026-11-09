package dev.aeris.island.media

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import dev.aeris.island.notifications.AerisNotificationListener

 data class MediaSnapshot(
    val title: String,
    val artist: String,
    val album: String,
    val packageName: String,
    val playing: Boolean,
    val durationMs: Long,
    val positionMs: Long,
    val artwork: Bitmap?
)

object MediaBridge {
    fun current(context: Context): MediaSnapshot? {
        if (!AerisNotificationListener.isEnabled(context)) return null
        return try {
            val manager = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
            val sessions = manager.getActiveSessions(
                ComponentName(context, AerisNotificationListener::class.java)
            )
            val controller = sessions.firstOrNull { isPlaying(it.playbackState?.state) }
                ?: sessions.firstOrNull()
                ?: return null
            val metadata = controller.metadata
            val state = controller.playbackState
            MediaSnapshot(
                title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)?.takeIf { it.isNotBlank() }
                    ?: "Unknown track",
                artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)?.takeIf { it.isNotBlank() }
                    ?: controller.packageName,
                album = metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM).orEmpty(),
                packageName = controller.packageName,
                playing = isPlaying(state?.state),
                durationMs = metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L,
                positionMs = state?.position ?: 0L,
                artwork = metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)
                    ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            )
        } catch (_: SecurityException) {
            null
        } catch (_: RuntimeException) {
            null
        }
    }

    fun toggle(context: Context) = transport(context) { controller ->
        val controls = controller.transportControls
        if (isPlaying(controller.playbackState?.state)) controls.pause() else controls.play()
    }

    fun next(context: Context) = transport(context) { it.transportControls.skipToNext() }
    fun previous(context: Context) = transport(context) { it.transportControls.skipToPrevious() }

    private inline fun transport(context: Context, action: (MediaController) -> Unit) {
        if (!AerisNotificationListener.isEnabled(context)) return
        try {
            val manager = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
            val sessions = manager.getActiveSessions(ComponentName(context, AerisNotificationListener::class.java))
            val controller = sessions.firstOrNull { isPlaying(it.playbackState?.state) }
                ?: sessions.firstOrNull()
            controller?.let(action)
        } catch (_: Exception) {
            // A player may disappear while the user is interacting with the island.
        }
    }

    private fun isPlaying(state: Int?): Boolean = state == PlaybackState.STATE_PLAYING ||
        state == PlaybackState.STATE_BUFFERING || state == PlaybackState.STATE_CONNECTING
}
