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
    val key: String,
    val receivedAt: Long,
    val canReply: Boolean
)

private data class ReplyTarget(val pendingIntent: PendingIntent, val inputs: Array<RemoteInput>)

/** Notification text and reply targets are held in memory only. */
object NotificationStore {
    private const val MAX_ITEMS = 12
    private val items = LinkedHashMap<String, NotificationSnapshot>()
    private val replyTargets = LinkedHashMap<String, ReplyTarget>()

    @Volatile var latest: NotificationSnapshot? = null
        private set

    @Synchronized fun recent(): List<NotificationSnapshot> = items.values.toList().asReversed()
    @Synchronized fun count(): Int = items.size

    @Synchronized
    fun accept(sbn: StatusBarNotification, ownPackage: String) {
        if (sbn.packageName == ownPackage) return
        val n = sbn.notification ?: return
        val extras = n.extras ?: Bundle.EMPTY
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val body = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.takeIf { it.isNotBlank() }
            ?: extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val action = n.actions?.firstOrNull { a ->
            a.actionIntent != null && a.remoteInputs?.any { input -> input.allowFreeFormInput } == true
        }
        val inputs = action?.remoteInputs?.filter { it.allowFreeFormInput }.orEmpty()
        if (action?.actionIntent != null && inputs.isNotEmpty()) {
            replyTargets[sbn.key] = ReplyTarget(action.actionIntent, inputs.toTypedArray())
        } else replyTargets.remove(sbn.key)

        items.remove(sbn.key)
        items[sbn.key] = NotificationSnapshot(
            title = title.ifBlank { sbn.packageName.substringAfterLast('.') },
            text = body,
            packageName = sbn.packageName,
            key = sbn.key,
            receivedAt = sbn.postTime,
            canReply = action?.actionIntent != null && inputs.isNotEmpty()
        )
        while (items.size > MAX_ITEMS) {
            val oldest = items.keys.firstOrNull() ?: break
            items.remove(oldest)
            replyTargets.remove(oldest)
        }
        latest = items.values.lastOrNull()
    }

    @Synchronized fun remove(key: String) {
        items.remove(key)
        replyTargets.remove(key)
        latest = items.values.lastOrNull()
    }

    @Synchronized fun markSourceRemoved(key: String) {
        replyTargets.remove(key)
        items[key]?.let { items[key] = it.copy(canReply = false) }
        latest = items.values.lastOrNull()
    }

    @Synchronized
    fun sendReply(context: Context, key: String, message: String): Boolean {
        if (message.isBlank()) return false
        val target = replyTargets[key] ?: return false
        return try {
            val fillIn = Intent()
            val data = Bundle().apply { target.inputs.forEach { putCharSequence(it.resultKey, message) } }
            RemoteInput.addResultsToIntent(target.inputs, fillIn, data)
            target.pendingIntent.send(context, 0, fillIn)
            true
        } catch (_: PendingIntent.CanceledException) { false }
        catch (_: Exception) { false }
    }
}

class AerisNotificationListener : NotificationListenerService() {
    override fun onListenerConnected() {
        super.onListenerConnected()
        active = this
    }

    override fun onListenerDisconnected() {
        if (active === this) active = null
        super.onListenerDisconnected()
    }

    override fun onDestroy() {
        if (active === this) active = null
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        NotificationStore.accept(sbn, packageName)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        // Keep the in-app history entry, but invalidate its inline-reply target.
        NotificationStore.markSourceRemoved(sbn.key)
    }

    companion object {
        @Volatile private var active: AerisNotificationListener? = null

        fun dismiss(key: String): Boolean {
            val listener = active ?: return false
            return try { listener.cancelNotification(key); true } catch (_: Exception) { false }
        }

        fun isEnabled(context: Context): Boolean {
            val enabled = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners") ?: return false
            val expected = ComponentName(context, AerisNotificationListener::class.java)
            return enabled.split(':').any { ComponentName.unflattenFromString(it) == expected }
        }
    }
}
