package com.personal.studytracker;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

public final class DailyReminderReceiver extends BroadcastReceiver {
    private static final String TAG = "StudyTrackerReminder";

    @Override public void onReceive(Context context, Intent intent) {
        String kind = intent == null ? "" : intent.getStringExtra(DailyReminderScheduler.EXTRA_KIND);
        if (!DailyReminderScheduler.MORNING.equals(kind)
                && !DailyReminderScheduler.EVENING.equals(kind)) {
            Log.e(TAG, "Received a daily reminder without a valid reminder type");
            return;
        }

        SharedPreferences prefs = context.getSharedPreferences(
                DailyReminderScheduler.PREFS, Context.MODE_PRIVATE);
        String enabledKey = DailyReminderScheduler.MORNING.equals(kind)
                ? DailyReminderScheduler.MORNING_ENABLED
                : DailyReminderScheduler.EVENING_ENABLED;
        if (!prefs.getBoolean(enabledKey, false)) return;
        boolean snooze = intent.getBooleanExtra(DailyReminderScheduler.EXTRA_SNOOZE, false);
        if (snooze) DailyReminderScheduler.clearSnooze(context, kind);
        else DailyReminderScheduler.scheduleNextDay(context, kind);
        if (!AlarmSoundService.notificationsAvailable(context)) {
            Log.w(TAG, "Notifications are disabled; skipping " + kind + " reminder");
            return;
        }

        boolean morning = DailyReminderScheduler.MORNING.equals(kind);
        String title = morning ? "A fresh day" : "Take a moment to check in";
        String message = morning
                ? "Make a simple plan for today."
                : "Look back at the habits and study you managed today.";
        String soundKey = morning ? DailyReminderScheduler.MORNING_SOUND
                : DailyReminderScheduler.EVENING_SOUND;
        Intent service = new Intent(context, AlarmSoundService.class)
                .putExtra(AlarmSoundService.EXTRA_TITLE, title)
                .putExtra(AlarmSoundService.EXTRA_MESSAGE, message)
                .putExtra(AlarmSoundService.EXTRA_REMINDER, true)
                .putExtra(AlarmSoundService.EXTRA_REMINDER_KIND, kind)
                .putExtra(DailyReminderScheduler.EXTRA_SOUND, prefs.getString(soundKey, ""));
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(service);
            } else {
                context.startService(service);
            }
        } catch (RuntimeException exception) {
            Log.e(TAG, "Could not start " + kind + " reminder alarm", exception);
        }
    }
}
