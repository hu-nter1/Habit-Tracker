package com.personal.studytracker;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;

final class DailyReminderScheduler {
    static final String PREFS = "daily_reminders";
    static final String MORNING = "morning";
    static final String EVENING = "evening";
    static final String EXTRA_KIND = "reminder_kind";
    static final String EXTRA_SOUND = "reminder_sound";
    static final String EXTRA_SNOOZE = "reminder_snooze";

    static final String MORNING_ENABLED = "morning_enabled";
    static final String MORNING_TIME = "morning_time";
    static final String MORNING_SOUND = "morning_sound";
    static final String EVENING_ENABLED = "evening_enabled";
    static final String EVENING_TIME = "evening_time";
    static final String EVENING_SOUND = "evening_sound";

    private static final String TAG = "StudyTrackerReminder";
    private static final int MORNING_REQUEST = 5823;
    private static final int EVENING_REQUEST = 5824;
    private static final int MORNING_SNOOZE_REQUEST = 5825;
    private static final int EVENING_SNOOZE_REQUEST = 5826;

    static void scheduleAll(Context context) {
        scheduleIfEnabled(context, MORNING);
        scheduleIfEnabled(context, EVENING);
        restoreSnooze(context, MORNING);
        restoreSnooze(context, EVENING);
    }

    static void scheduleIfEnabled(Context context, String kind) {
        String enabledKey = MORNING.equals(kind) ? MORNING_ENABLED : EVENING_ENABLED;
        if (!context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(enabledKey, false)) {
            cancel(context, kind);
            return;
        }
        LocalDate today = LocalDate.now();
        ZonedDateTime candidate = ZonedDateTime.of(today, getTime(context, kind),
                ZoneId.systemDefault());
        if (!candidate.isAfter(ZonedDateTime.now())) candidate = candidate.plusDays(1);
        schedule(context, kind, candidate.toInstant().toEpochMilli());
    }

    static void scheduleNextDay(Context context, String kind) {
        String enabledKey = MORNING.equals(kind) ? MORNING_ENABLED : EVENING_ENABLED;
        if (!context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(enabledKey, false)) {
            cancel(context, kind);
            return;
        }
        ZonedDateTime next = ZonedDateTime.of(LocalDate.now().plusDays(1),
                getTime(context, kind), ZoneId.systemDefault());
        schedule(context, kind, next.toInstant().toEpochMilli());
    }

    static void scheduleSnooze(Context context, String kind) {
        if (!MORNING.equals(kind) && !EVENING.equals(kind)) return;
        String enabledKey = MORNING.equals(kind) ? MORNING_ENABLED : EVENING_ENABLED;
        if (!context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(enabledKey, false)
                || !AlarmSoundService.notificationsAvailable(context)) return;
        long snoozeAt = System.currentTimeMillis() + 5 * 60 * 1000L;
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putLong(snoozeKey(kind), snoozeAt).apply();
        schedule(context, kind, snoozeAt, true);
    }

    static void restoreSnooze(Context context, String kind) {
        String enabledKey = MORNING.equals(kind) ? MORNING_ENABLED : EVENING_ENABLED;
        android.content.SharedPreferences prefs =
                context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        long snoozeAt = prefs.getLong(snoozeKey(kind), 0L);
        if (snoozeAt <= 0L) return;
        if (!prefs.getBoolean(enabledKey, false)
                || !AlarmSoundService.notificationsAvailable(context)) {
            clearSnooze(context, kind);
            return;
        }
        schedule(context, kind, Math.max(System.currentTimeMillis(), snoozeAt), true);
    }

    static void clearSnooze(Context context, String kind) {
        if (!MORNING.equals(kind) && !EVENING.equals(kind)) return;
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .remove(snoozeKey(kind)).apply();
        cancelAlarm(context, snoozeRequestCode(kind));
    }

    static LocalTime getTime(Context context, String kind) {
        String key = MORNING.equals(kind) ? MORNING_TIME : EVENING_TIME;
        String defaultValue = MORNING.equals(kind) ? "07:00" : "20:00";
        String value = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(key, defaultValue);
        try {
            return LocalTime.parse(value);
        } catch (DateTimeParseException exception) {
            Log.e(TAG, "Invalid saved " + kind + " reminder time; using " + defaultValue, exception);
            return LocalTime.parse(defaultValue);
        }
    }

    private static void schedule(Context context, String kind, long whenMillis) {
        schedule(context, kind, whenMillis, false);
    }

    private static void schedule(Context context, String kind, long whenMillis, boolean snooze) {
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (manager == null) {
            Log.e(TAG, "AlarmManager is unavailable; could not schedule " + kind + " reminder");
            return;
        }
        Intent intent = new Intent(context, DailyReminderReceiver.class)
                .putExtra(EXTRA_KIND, kind)
                .putExtra(EXTRA_SNOOZE, snooze);
        int requestCode = snooze ? snoozeRequestCode(kind) : requestCode(kind);
        PendingIntent pending = PendingIntent.getBroadcast(context, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && manager.canScheduleExactAlarms()) {
            try {
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, whenMillis, pending);
                return;
            } catch (SecurityException exception) {
                Log.w(TAG, "Exact alarm access changed; scheduling an inexact reminder", exception);
            }
        }
        manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, whenMillis, pending);
    }

    private static void cancel(Context context, String kind) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .remove(snoozeKey(kind)).apply();
        cancelAlarm(context, requestCode(kind));
        cancelAlarm(context, snoozeRequestCode(kind));
    }

    private static void cancelAlarm(Context context, int requestCode) {
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (manager == null) return;
        PendingIntent pending = PendingIntent.getBroadcast(context, requestCode,
                new Intent(context, DailyReminderReceiver.class),
                PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
        if (pending != null) {
            manager.cancel(pending);
            pending.cancel();
        }
    }

    private static String snoozeKey(String kind) {
        return kind + "_snooze_at";
    }

    private static int requestCode(String kind) {
        return MORNING.equals(kind) ? MORNING_REQUEST : EVENING_REQUEST;
    }

    private static int snoozeRequestCode(String kind) {
        return MORNING.equals(kind) ? MORNING_SNOOZE_REQUEST : EVENING_SNOOZE_REQUEST;
    }

    private DailyReminderScheduler() {}
}
