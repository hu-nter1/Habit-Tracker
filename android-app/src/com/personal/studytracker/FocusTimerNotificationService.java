package com.personal.studytracker;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

public final class FocusTimerNotificationService extends Service {
    static final String CHANNEL_ID = "focus_timer_progress";
    static final int NOTIFICATION_ID = 7305;

    static void ensureNotificationChannel(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null || manager.getNotificationChannel(CHANNEL_ID) != null) return;
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, "Active focus timer", NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("Shows the remaining focus time or elapsed stopwatch time");
        channel.setSound(null, null);
        channel.enableVibration(false);
        manager.createNotificationChannel(channel);
    }

    static boolean notificationsAvailable(Context context) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return true;
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null) return false;
        NotificationChannel channel = manager.getNotificationChannel(CHANNEL_ID);
        return channel != null && channel.getImportance() != NotificationManager.IMPORTANCE_NONE;
    }

    static void showOrUpdate(Context context) {
        Intent service = new Intent(context, FocusTimerNotificationService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(service);
        } else {
            context.startService(service);
        }
    }

    static void stop(Context context) {
        context.stopService(new Intent(context, FocusTimerNotificationService.class));
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager != null) manager.cancel(NOTIFICATION_ID);
    }

    @Override public void onCreate() {
        super.onCreate();
        ensureNotificationChannel(this);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        SharedPreferences prefs = getSharedPreferences(
                TimerAlarmScheduler.PREFS, MODE_PRIVATE);
        if (!prefs.getBoolean(TimerAlarmScheduler.KEY_RUNNING, false)) {
            stopSelf(startId);
            return START_NOT_STICKY;
        }

        Notification notification = buildNotification(prefs);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
        return START_NOT_STICKY;
    }

    private Notification buildNotification(SharedPreferences prefs) {
        boolean stopwatch = prefs.getBoolean(TimerAlarmScheduler.KEY_STOPWATCH, false);
        boolean paused = prefs.getBoolean(TimerAlarmScheduler.KEY_PAUSED, false);
        long now = System.currentTimeMillis();
        long startMillis = prefs.getLong(TimerAlarmScheduler.KEY_START_MILLIS, now);
        long when;
        String text;
        if (stopwatch) {
            int elapsedSeconds = prefs.getInt(TimerAlarmScheduler.KEY_ELAPSED_SECONDS, 0);
            when = startMillis - elapsedSeconds * 1000L;
            text = paused ? "Stopwatch paused" : "Stopwatch";
        } else {
            long endMillis = prefs.getLong(TimerAlarmScheduler.KEY_END_MILLIS, now);
            long remainingMillis = prefs.getLong(
                    TimerAlarmScheduler.KEY_PAUSED_REMAINING_MILLIS,
                    Math.max(0L, endMillis - now));
            when = paused ? remainingMillis : endMillis;
            text = paused ? "Timer paused" : "Focus timer";
        }

        Intent openIntent = new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent open = PendingIntent.getActivity(this, 7310, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        String action = paused ? FocusTimerActionReceiver.ACTION_RESUME
                : FocusTimerActionReceiver.ACTION_PAUSE;
        String actionLabel = paused ? "Resume" : "Pause";
        Intent controlIntent = new Intent(this, FocusTimerActionReceiver.class)
                .setAction(action);
        PendingIntent control = PendingIntent.getBroadcast(this, 7311, controlIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        String topic = prefs.getString(TimerAlarmScheduler.KEY_TOPIC, "").trim();
        NotificationCompat.Builder notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle(topic.isEmpty() ? "Day by Day" : topic)
                .setContentText(text)
                .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .setContentIntent(open)
                .setShowWhen(true)
                .addAction(android.R.drawable.ic_media_pause, actionLabel, control);

        if (!paused) {
            notification.setWhen(when).setUsesChronometer(true);
            if (!stopwatch) notification.setChronometerCountDown(true);
        } else {
            notification.setShowWhen(false);
            if (!stopwatch) {
                notification.setContentText("Timer paused · " + formatDuration(when));
            } else {
                int elapsedSeconds = prefs.getInt(TimerAlarmScheduler.KEY_ELAPSED_SECONDS, 0);
                notification.setContentText("Stopwatch paused · "
                        + formatDuration(elapsedSeconds * 1000L));
            }
        }
        return notification.build();
    }

    private String formatDuration(long millis) {
        long seconds = Math.max(0L, (millis + 999L) / 1000L);
        long hours = seconds / 3600L;
        long minutes = (seconds % 3600L) / 60L;
        long remainder = seconds % 60L;
        return hours > 0
                ? String.format("%d:%02d:%02d", hours, minutes, remainder)
                : String.format("%02d:%02d", minutes, remainder);
    }

    @Override public void onDestroy() {
        stopForeground(STOP_FOREGROUND_REMOVE);
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) {
        return null;
    }
}
