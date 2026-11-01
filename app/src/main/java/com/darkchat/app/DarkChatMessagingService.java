package com.darkchat.app;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import java.util.Map;

public final class DarkChatMessagingService extends FirebaseMessagingService {
    static final String CALL_CHANNEL = "darkchat_calls_v1";
    static final String MESSAGE_CHANNEL = "default";
    static final String CALL_TAG = "darkchat_incoming_call";

    @Override public void onNewToken(String token) {
        super.onNewToken(token);
        getSharedPreferences("fcm", MODE_PRIVATE).edit().putString("token", token).apply();
        MainActivity activity = MainActivity.active;
        if (activity != null) activity.deliverToken(token);
    }

    static void createCallChannel(Context context) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager manager = (NotificationManager) context.getSystemService(NOTIFICATION_SERVICE);
        NotificationChannel channel = new NotificationChannel(
            CALL_CHANNEL, "Dark Chat incoming voice and video calls",
            NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription("Ringing alerts for incoming Dark Chat calls");
        channel.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
            new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE).build());
        channel.enableVibration(true);
        channel.setVibrationPattern(new long[]{0, 600, 250, 600, 250, 600});
        channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        manager.createNotificationChannel(channel);
    }

    private PendingIntent callIntent(String inviteId, String action, int extra) {
        Intent launch = new Intent(this, MainActivity.class);
        launch.setAction("com.darkchat.app.INCOMING_CALL_" + action);
        launch.setData(Uri.parse("darkchat://call/" + inviteId + "/" + action));
        launch.putExtra("call_invite_id", inviteId);
        launch.putExtra("call_action", action);
        launch.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(this, inviteId.hashCode() * 31 + extra, launch,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    @Override public void onMessageReceived(RemoteMessage incoming) {
        super.onMessageReceived(incoming);
        Map<String, String> data = incoming.getData();
        if ("incoming_call".equals(data.get("type"))) {
            showIncomingCall(data);
            return;
        }
        // Regular message notifications with a 'notification' payload are
        // displayed automatically by Firebase when the app is in background.
        // This foreground fallback is only used if the app activity is absent.
        RemoteMessage.Notification n = incoming.getNotification();
        if (n != null && MainActivity.active == null) {
            showRegularMessage(n);
        }
    }

    private void showIncomingCall(Map<String,String> data) {
        String id = data.get("call_invite_id");
        if (id == null || !id.matches("[0-9a-fA-F-]{36}")) return;
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return;
        createCallChannel(this);
        String caller = data.get("caller_name");
        if (caller == null || caller.trim().isEmpty()) caller = "Dark Chat";
        if (caller.length() > 60) caller = caller.substring(0,60);
        boolean video = "video".equals(data.get("call_kind"));
        String kind = video ? "Incoming video call" : "Incoming voice call";
        Bitmap logo = BitmapFactory.decodeResource(getResources(), R.drawable.dark_chat_logo);
        PendingIntent open = callIntent(id, "open", 1);
        PendingIntent answer = callIntent(id, "answer", 2);
        PendingIntent decline = callIntent(id, "decline", 3);
        Notification notification = new Notification.Builder(this, CALL_CHANNEL)
            .setContentTitle(caller)
            .setContentText(kind)
            .setSmallIcon(R.drawable.ic_stat_dark_chat)
            .setLargeIcon(logo)
            .setColor(0xFFA629FF)
            .setColorized(false)
            .setCategory(Notification.CATEGORY_CALL)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setPriority(Notification.PRIORITY_MAX)
            .setAutoCancel(true)
            .setOngoing(false)
            .setContentIntent(open)
            .setTimeoutAfter(41000L)
            .addAction(R.drawable.ic_stat_dark_chat, "Answer", answer)
            .addAction(R.drawable.ic_stat_dark_chat, "Decline", decline)
            .build();
        ((NotificationManager)getSystemService(NOTIFICATION_SERVICE))
            .notify(CALL_TAG,id.hashCode(),notification);
    }

    private void showRegularMessage(RemoteMessage.Notification n) {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return;
        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this,MainActivity.class),
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification notification = new Notification.Builder(this, MESSAGE_CHANNEL)
            .setContentTitle(n.getTitle()==null ? "Dark Chat" : n.getTitle())
            .setContentText(n.getBody())
            .setSmallIcon(R.drawable.ic_stat_dark_chat)
            .setLargeIcon(BitmapFactory.decodeResource(getResources(),R.drawable.dark_chat_logo))
            .setCategory(Notification.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build();
        ((NotificationManager)getSystemService(NOTIFICATION_SERVICE))
            .notify((int)(System.currentTimeMillis() % 1000000),notification);
    }
}
