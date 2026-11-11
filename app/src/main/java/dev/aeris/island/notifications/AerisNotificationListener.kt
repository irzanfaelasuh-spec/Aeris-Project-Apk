package dev.aeris.island.notifications

import android.app.Notification
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import java.util.LinkedHashMap

 data class NotificationSnapshot(
    val title: String,
    val text: String,
    val packageName: String,
    val receivedAt: Long,
    val key: String,
    val canReply: Boolean
)

private data class ReplyTarget(
    val pendingIntent: PendingIntent,
    val remoteInputs: Array<RemoteInput>
)

/** In-memory notification queue. Notification text is never written to disk. */
object NotificationStore {
    private const val MAX_RECENT = 8
    private val queue = LinkedHashMap<String, NotificationSnapshot>()
    private val replyTargets = LinkedHashMap<String, ReplyTarget>()

    @Volatile
    var latest: NotificationSnapshot? = null
        private set

    @Synchronized
    fun recent(): List<NotificationSnapshot> = queue.values.toList().asReversed()

    @Synchronized
    fun count(): Int = queue.size

    @Synchronized
    fun accept(sbn: StatusBarNotification, ownPackage: String) {
        if (sbn.packageName == ownPackage) return
        val notification = sbn.notification ?: return
        val extras = notification.extras ?: Bundle.EMPTY
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
            ?.takeIf { it.isNotBlank() }
            ?: extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val action = notification.actions?.firstOrNull { candidate ->
            candidate.actionIntent != null && candidate.remoteInputs?.any { it.allowFreeFormInput } == true
        }
        val intent = action?.actionIntent
        val remoteInputs = action?.remoteInputs?.filter { it.allowFreeFormInput }.orEmpty()
        if (intent != null && remoteInputs.isNotEmpty()) {
            replyTargets[sbn.key] = ReplyTarget(intent, remoteInputs.toTypedArray())
        } else {
            replyTargets.remove(sbn.key)
        }

        queue.remove(sbn.key) // Re-posting a notification moves it to the newest position.
        queue[sbn.key] = NotificationSnapshot(
            title = title.ifBlank { sbn.packageName.substringAfterLast('.') },
            text = text,
            packageName = sbn.packageName,
            receivedAt = sbn.postTime,
            key = sbn.key,
            canReply = intent != null && remoteInputs.isNotEmpty()
        )
        while (queue.size > MAX_RECENT) {
            val first = queue.entries.firstOrNull()?.key ?: break
            queue.remove(first)
            replyTargets.remove(first)
        }
        latest = queue.values.lastOrNull()
    }

    @Synchronized
    fun sendReply(context: Context, message: String, notificationKey: String? = latest?.key): Boolean {
        val target = notificationKey?.let { replyTargets[it] } ?: return false
        if (message.isBlank()) return false
        return try {
            val fillIn = Intent()
            val results = Bundle().apply {
                target.remoteInputs.forEach { input -> putCharSequence(input.resultKey, message) }
            }
            RemoteInput.addResultsToIntent(target.remoteInputs, fillIn, results)
            target.pendingIntent.send(context, 0, fillIn)
            true
        } catch (_: PendingIntent.CanceledException) {
            false
        } catch (_: Exception) {
            false
        }
    }
}

class AerisNotificationListener : NotificationListenerService() {
    override fun onListenerConnected() {
        super.onListenerConnected()
        activeListener = this
    }

    override fun onListenerDisconnected() {
        if (activeListener === this) activeListener = null
        super.onListenerDisconnected()
    }

    override fun onDestroy() {
        if (activeListener === this) activeListener = null
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        NotificationStore.accept(sbn, packageName)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        // Keep the in-memory snapshot so the user can still browse recent history.
        // Reply may no longer work if Android cancels the source PendingIntent.
    }

    companion object {
        @Volatile private var activeListener: AerisNotificationListener? = null

        fun dismissNotification(key: String): Boolean {
            val listener = activeListener ?: return false
            return try {
                listener.cancelNotification(key)
                true
            } catch (_: Exception) {
                false
            }
        }

        fun isEnabled(context: Context): Boolean {
            val enabled = Settings.Secure.getString(
                context.contentResolver,
                "enabled_notification_listeners"
            ) ?: return false
            val expected = ComponentName(context, AerisNotificationListener::class.java)
            return enabled.split(':').any { ComponentName.unflattenFromString(it) == expected }
        }
    }
}
