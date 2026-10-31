package app.still.launcher;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Posts under the test APK's UID, without relying on the instrumented APK's classes. */
public class BadgeTestReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        String operation = intent.getStringExtra("operation");
        if ("cancel".equals(operation)) {
            manager.cancel(intent.getIntExtra("id", 0));
        } else if ("cleanup".equals(operation)) {
            manager.cancelAll();
            manager.deleteNotificationChannel("badge-test");
            manager.deleteNotificationChannel("badge-test-off");
        } else {
            manager.createNotificationChannel(new NotificationChannel("badge-test", "Badge test", NotificationManager.IMPORTANCE_DEFAULT));
            NotificationChannel off = new NotificationChannel("badge-test-off", "No badge test", NotificationManager.IMPORTANCE_DEFAULT);
            off.setShowBadge(false);
            manager.createNotificationChannel(off);
            String channel = intent.getStringExtra("channel");
            boolean summary = intent.getBooleanExtra("summary", false);
            Notification.Builder builder = new Notification.Builder(context, channel == null ? "badge-test" : channel)
                .setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle("Badge integration test")
                .setOngoing(intent.getBooleanExtra("ongoing", false));
            if (summary || intent.getBooleanExtra("group", false)) builder.setGroup("badge-test-group").setGroupSummary(summary);
            manager.notify(intent.getIntExtra("id", 0), builder.build());
        }
    }
}
