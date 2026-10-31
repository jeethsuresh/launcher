package app.still.launcher

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Posts as the test APK's own UID, rather than the instrumented launcher's UID. */
class BadgeTestReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val manager = context.getSystemService(NotificationManager::class.java)
        when (intent.getStringExtra("operation")) {
            "cancel" -> manager.cancel(intent.getIntExtra("id", 0))
            "cleanup" -> {
                manager.cancelAll()
                manager.deleteNotificationChannel("badge-test")
                manager.deleteNotificationChannel("badge-test-off")
            }
            else -> {
                manager.createNotificationChannel(NotificationChannel("badge-test", "Badge test", NotificationManager.IMPORTANCE_DEFAULT))
                manager.createNotificationChannel(NotificationChannel("badge-test-off", "No badge test", NotificationManager.IMPORTANCE_DEFAULT).apply { setShowBadge(false) })
                val summary = intent.getBooleanExtra("summary", false)
                manager.notify(intent.getIntExtra("id", 0), Notification.Builder(context, intent.getStringExtra("channel") ?: "badge-test")
                    .setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle("Badge integration test")
                    .setOngoing(intent.getBooleanExtra("ongoing", false))
                    .apply { if (summary || intent.getBooleanExtra("group", false)) setGroup("badge-test-group").setGroupSummary(summary) }.build())
            }
        }
    }
}
