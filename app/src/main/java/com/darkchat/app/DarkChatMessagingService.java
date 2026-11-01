package com.darkchat.app;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

public final class DarkChatMessagingService extends FirebaseMessagingService {
    @Override public void onNewToken(String token) {
        super.onNewToken(token);
        getSharedPreferences("fcm", MODE_PRIVATE).edit().putString("token", token).apply();
        MainActivity activity = MainActivity.active;
        if (activity != null) activity.deliverToken(token);
    }

    @Override public void onMessageReceived(RemoteMessage message) {
        super.onMessageReceived(message);
        // FCM automatically displays notification payloads when the app is in background.
        // In foreground, the web view shows live chat messages, so avoid duplicates.
        if (MainActivity.active != null) return;
        RemoteMessage.Notification n = message.getNotification();
        if (n == null) return;
        Intent launch = new Intent(this, MainActivity.class);
        launch.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent tap = PendingIntent.getActivity(this, 0, launch,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        if (Build.VERSION.SDK_INT >= 26) {
            Notification notification = new Notification.Builder(this, "default")
                .setContentTitle(n.getTitle() == null ? "DARK CHAT" : n.getTitle())
                .setContentText(n.getBody())
                .setSmallIcon(android.R.drawable.stat_notify_chat)
                .setAutoCancel(true)
                .setContentIntent(tap)
                .build();
            ((NotificationManager) getSystemService(NOTIFICATION_SERVICE))
                .notify((int)(System.currentTimeMillis() % 1000000), notification);
        }
    }
}
