package com.personal.studytracker;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

public final class AlarmSoundService extends Service {
    static final String EXTRA_TITLE = "title";
    static final String EXTRA_MESSAGE = "message";
    static final String EXTRA_REMINDER = "daily_reminder";
    static final String EXTRA_REMINDER_KIND = "reminder_kind";
    static final String CHANNEL_ID = "focus_alarm";
    static final int NOTIFICATION_ID = 7304;
    private static final long MAX_RING_DURATION_MS = 10 * 60 * 1000L;
    private static final String TAG = "StudyTrackerAlarm";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable stopAfterTimeout = this::stopSelf;
    private Ringtone ringtone;
    private Vibrator vibrator;
    private boolean reminder;
    private String reminderKind;

    @Override public void onCreate() {
        super.onCreate();
        ensureNotificationChannel(this);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        String title = intent == null ? "Focus timer complete"
                : intent.getStringExtra(EXTRA_TITLE);
        String message = intent == null ? "Your study session is complete."
                : intent.getStringExtra(EXTRA_MESSAGE);
        reminder = intent != null && intent.getBooleanExtra(EXTRA_REMINDER, false);
        reminderKind = intent == null ? null : intent.getStringExtra(EXTRA_REMINDER_KIND);
        String soundValue = intent == null ? null
                : intent.getStringExtra(DailyReminderScheduler.EXTRA_SOUND);
        startForeground(NOTIFICATION_ID, buildNotification(title, message, reminder));
        startAlarmFeedback(soundValue);
        handler.removeCallbacks(stopAfterTimeout);
        handler.postDelayed(stopAfterTimeout, MAX_RING_DURATION_MS);
        return START_NOT_STICKY;
    }

    private Notification buildNotification(String title, String message, boolean reminder) {
        Intent stopIntent = new Intent(this, AlarmActionReceiver.class)
                .setAction(reminder ? AlarmActionReceiver.ACTION_STOP_REMINDER
                        : AlarmActionReceiver.ACTION_STOP);
        PendingIntent stop = PendingIntent.getBroadcast(this, 1, stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Intent openIntent = new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent open = PendingIntent.getActivity(this, 3, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle(title == null ? "Focus timer complete" : title)
                .setContentText(message == null ? "Your study session is complete." : message)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setOngoing(true)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setContentIntent(open)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
                .setForegroundServiceBehavior(
                        NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Dismiss", stop);
        if (!reminder) {
            Intent snoozeIntent = new Intent(this, AlarmActionReceiver.class)
                    .setAction(AlarmActionReceiver.ACTION_SNOOZE);
            PendingIntent snooze = PendingIntent.getBroadcast(this, 2, snoozeIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            notification.addAction(android.R.drawable.ic_lock_idle_alarm, "Snooze 5 min", snooze);
        } else if (reminderKind != null) {
            Intent snoozeIntent = new Intent(this, AlarmActionReceiver.class)
                    .setAction(AlarmActionReceiver.ACTION_SNOOZE_REMINDER)
                    .putExtra(DailyReminderScheduler.EXTRA_KIND, reminderKind);
            int requestCode = DailyReminderScheduler.MORNING.equals(reminderKind) ? 4 : 5;
            PendingIntent snooze = PendingIntent.getBroadcast(this, requestCode, snoozeIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            notification.addAction(android.R.drawable.ic_lock_idle_alarm, "Snooze 5 min", snooze);
        }
        return notification.build();
    }

    static void ensureNotificationChannel(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager manager =
                context.getSystemService(NotificationManager.class);
        if (manager == null) return;
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return;
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, "Focus timer alarms", NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription("Sound and vibration when a focus countdown ends");
        channel.setSound(null, null);
        channel.enableVibration(false);
        manager.createNotificationChannel(channel);
    }

    static boolean notificationsAvailable(Context context) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return true;
        NotificationManager manager =
                context.getSystemService(NotificationManager.class);
        if (manager == null) return false;
        NotificationChannel channel = manager.getNotificationChannel(CHANNEL_ID);
        return channel == null || channel.getImportance() != NotificationManager.IMPORTANCE_NONE;
    }

    static void stop(Context context) {
        context.stopService(new Intent(context, AlarmSoundService.class));
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager != null) manager.cancel(NOTIFICATION_ID);
    }

    private void startAlarmFeedback(String soundValue) {
        stopAlarmFeedback();
        Uri sound = soundValue == null || soundValue.isEmpty() ? null : Uri.parse(soundValue);
        if (sound == null) sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
        if (sound == null) sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        ringtone = createRingtone(sound);
        Uri defaultAlarm = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
        if (ringtone == null && (defaultAlarm == null || !defaultAlarm.equals(sound))) {
            Log.w(TAG, "Selected reminder sound is unavailable; using the device alarm sound");
            ringtone = createRingtone(defaultAlarm);
        }
        if (ringtone != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) ringtone.setLooping(true);
            try {
                ringtone.play();
            } catch (RuntimeException exception) {
                Log.e(TAG, "Could not play the selected alarm sound", exception);
                ringtone = null;
            }
        }
        vibrator = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                ? ((VibratorManager) getSystemService(Context.VIBRATOR_MANAGER_SERVICE)).getDefaultVibrator()
                : (Vibrator) getSystemService(VIBRATOR_SERVICE);
        if (vibrator != null && vibrator.hasVibrator()) {
            long[] pattern = {0, 500, 300, 500, 700};
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0));
            } else {
                vibrator.vibrate(pattern, 0);
            }
        }
    }

    private Ringtone createRingtone(Uri sound) {
        if (sound == null) return null;
        try {
            return RingtoneManager.getRingtone(getApplicationContext(), sound);
        } catch (RuntimeException exception) {
            Log.w(TAG, "Could not load alarm sound " + sound, exception);
            return null;
        }
    }

    private void stopAlarmFeedback() {
        if (ringtone != null) {
            ringtone.stop();
            ringtone = null;
        }
        if (vibrator != null) {
            vibrator.cancel();
            vibrator = null;
        }
    }

    @Override public void onDestroy() {
        handler.removeCallbacks(stopAfterTimeout);
        stopAlarmFeedback();
        stopForeground(STOP_FOREGROUND_REMOVE);
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) manager.cancel(NOTIFICATION_ID);
        if (!reminder) {
            getSharedPreferences(TimerAlarmScheduler.PREFS, MODE_PRIVATE).edit()
                    .putBoolean(TimerAlarmScheduler.KEY_ALARM_RINGING, false)
                    .apply();
        }
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
