package dev.aeris.island.notifications

import android.app.Notification
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.content.ComponentName

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
    val remoteInputs: Array<RemoteInput>,
    val packageName: String
)

object NotificationStore {
    @Volatile var latest: NotificationSnapshot? = null
        private set
    @Volatile private var replyTarget: ReplyTarget? = null

    fun accept(sbn: StatusBarNotification, ownPackage: String) {
        if (sbn.packageName == ownPackage) return
        val notification = sbn.notification ?: return
        val extras = notification.extras ?: Bundle.EMPTY
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
            ?.takeIf { it.isNotBlank() }
            ?: extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val target = notification.actions?.firstOrNull { action ->
            action.actionIntent != null && action.remoteInputs?.any { input -> input.allowFreeFormInput } == true
        }
        val targetIntent = target?.actionIntent
        val textInputs = target?.remoteInputs?.filter { input -> input.allowFreeFormInput }.orEmpty()
        replyTarget = if (targetIntent != null && textInputs.isNotEmpty()) {
            ReplyTarget(targetIntent, textInputs.toTypedArray(), sbn.packageName)
        } else null
        latest = NotificationSnapshot(
            title = title.ifBlank { sbn.packageName },
            text = text,
            packageName = sbn.packageName,
            receivedAt = sbn.postTime,
            key = sbn.key,
            canReply = target != null
        )
    }

    fun remove(key: String) {
        if (latest?.key == key) {
            latest = null
            replyTarget = null
        }
    }

    fun sendReply(context: Context, message: String): Boolean {
        val target = replyTarget ?: return false
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
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        NotificationStore.accept(sbn, packageName)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        NotificationStore.remove(sbn.key)
    }

    companion object {
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
