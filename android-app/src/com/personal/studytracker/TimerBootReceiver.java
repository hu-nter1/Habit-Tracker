package com.personal.studytracker;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

public final class TimerBootReceiver extends BroadcastReceiver {
    private static final String TAG = "StudyTrackerAlarm";

    @Override public void onReceive(Context context, Intent intent) {
        if (intent == null || (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())
                && !Intent.ACTION_MY_PACKAGE_REPLACED.equals(intent.getAction())
                && !Intent.ACTION_TIME_CHANGED.equals(intent.getAction())
                && !Intent.ACTION_TIMEZONE_CHANGED.equals(intent.getAction()))) {
            return;
        }
        DailyReminderScheduler.scheduleAll(context);
        PlanReminderScheduler.scheduleAll(context);
        SharedPreferences prefs = context.getSharedPreferences(
                TimerAlarmScheduler.PREFS, Context.MODE_PRIVATE);
        long snoozeEnd = prefs.getLong(TimerAlarmScheduler.KEY_SNOOZE_END_MILLIS, 0L);
        boolean snoozeExpired = false;
        if (snoozeEnd > 0L) {
            if (snoozeEnd <= System.currentTimeMillis()) {
                snoozeExpired = true;
                prefs.edit().remove(TimerAlarmScheduler.KEY_SNOOZE_END_MILLIS)
                        .putBoolean(TimerAlarmScheduler.KEY_ALARM_RINGING, true).apply();
                TimerAlarmReceiver.startAlarmService(
                        context, "Focus timer", "Your 5-minute snooze is over.");
            } else {
                TimerAlarmScheduler.scheduleSnooze(context, snoozeEnd);
            }
        }
        boolean bootOrReplacement = Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())
                || Intent.ACTION_MY_PACKAGE_REPLACED.equals(intent.getAction());
        if (!snoozeExpired && bootOrReplacement && prefs.getBoolean(
                TimerAlarmScheduler.KEY_ALARM_RINGING, false)) {
            TimerAlarmReceiver.startAlarmService(context,
                    "Focus timer complete", "Your study session is complete.");
        }
        if (!prefs.getBoolean(TimerAlarmScheduler.KEY_RUNNING, false)) return;
        try {
            FocusTimerNotificationService.showOrUpdate(context);
        } catch (RuntimeException exception) {
            Log.e(TAG, "Could not restore the active focus timer notification", exception);
        }
        if (prefs.getBoolean(TimerAlarmScheduler.KEY_PAUSED, false)
                || prefs.getBoolean(TimerAlarmScheduler.KEY_STOPWATCH, false)) return;
        String token = prefs.getString(TimerAlarmScheduler.KEY_TOKEN, "");
        long endAt = prefs.getLong(TimerAlarmScheduler.KEY_END_MILLIS, 0L);
        if (token.isEmpty() || endAt <= 0L) {
            Log.e(TAG, "Cannot restore focus timer after restart: saved timer state is incomplete");
            return;
        }
        TimerAlarmScheduler.scheduleTimer(context,
                Math.max(System.currentTimeMillis(), endAt), token);
    }
}
