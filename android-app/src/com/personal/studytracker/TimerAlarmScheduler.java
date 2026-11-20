package com.personal.studytracker;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

final class TimerAlarmScheduler {
    private static final String TAG = "StudyTrackerAlarm";
    static final String PREFS = "focus_timer";
    static final String KEY_RUNNING = "running";
    static final String KEY_PAUSED = "paused";
    static final String KEY_STOPWATCH = "stopwatch";
    static final String KEY_STOPWATCH_MODE = "stopwatch_mode";
    static final String KEY_ALARM_RINGING = "alarm_ringing";
    static final String KEY_SNOOZE_END_MILLIS = "snooze_end_millis";
    static final String KEY_TOKEN = "token";
    static final String KEY_START_MILLIS = "start_millis";
    static final String KEY_SESSION_START_MILLIS = "session_start_millis";
    static final String KEY_END_MILLIS = "end_millis";
    static final String KEY_DURATION_SECONDS = "duration_seconds";
    static final String KEY_ELAPSED_SECONDS = "elapsed_seconds";
    static final String KEY_PAUSED_REMAINING_MILLIS = "paused_remaining_millis";
    static final String KEY_TOPIC = "topic";
    static final String KEY_TOPIC_ID = "topic_id";
    static final String KEY_INTERRUPTED = "interrupted";
    static final String KEY_SOUND = "sound";
    static final String EXTRA_KIND = "kind";
    static final String EXTRA_TOKEN = "token";
    static final String KIND_TIMER = "timer";
    static final String KIND_SNOOZE = "snooze";
    private static final int TIMER_REQUEST = 5821;
    private static final int SNOOZE_REQUEST = 5822;

    static boolean canScheduleExact(Context context) {
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S || manager.canScheduleExactAlarms();
    }

    static void scheduleTimer(Context context, long endAtMillis, String token) {
        Intent intent = new Intent(context, TimerAlarmReceiver.class)
                .putExtra(EXTRA_KIND, KIND_TIMER)
                .putExtra(EXTRA_TOKEN, token);
        schedule(context, endAtMillis, intent, TIMER_REQUEST, true);
    }

    static void scheduleSnooze(Context context) {
        scheduleSnooze(context, System.currentTimeMillis() + 5 * 60 * 1000L);
    }

    static void scheduleSnooze(Context context, long snoozeAtMillis) {
        Intent intent = new Intent(context, TimerAlarmReceiver.class)
                .putExtra(EXTRA_KIND, KIND_SNOOZE);
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putLong(KEY_SNOOZE_END_MILLIS, snoozeAtMillis).apply();
        schedule(context, Math.max(System.currentTimeMillis(), snoozeAtMillis),
                intent, SNOOZE_REQUEST, true);
    }

    static void cancelTimer(Context context) {
        cancel(context, TIMER_REQUEST);
    }

    static void cancelSnooze(Context context) {
        cancel(context, SNOOZE_REQUEST);
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .remove(KEY_SNOOZE_END_MILLIS).apply();
    }

    private static void cancel(Context context, int requestCode) {
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        PendingIntent pending = PendingIntent.getBroadcast(
                context, requestCode, new Intent(context, TimerAlarmReceiver.class),
                PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
        if (pending != null) manager.cancel(pending);
    }

    private static void schedule(
            Context context, long when, Intent intent, int requestCode, boolean exact) {
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        PendingIntent pending = PendingIntent.getBroadcast(
                context, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        if (exact && canScheduleExact(context)) {
            try {
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, pending);
                return;
            } catch (SecurityException exception) {
                Log.w(TAG, "Exact alarm access changed; scheduling an inexact fallback", exception);
            }
        }
        manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, pending);
    }

    private TimerAlarmScheduler() {}
}
