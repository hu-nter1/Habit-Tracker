package com.personal.studytracker;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

public final class FocusTimerActionReceiver extends BroadcastReceiver {
    static final String ACTION_PAUSE = "com.personal.studytracker.FOCUS_TIMER_PAUSE";
    static final String ACTION_RESUME = "com.personal.studytracker.FOCUS_TIMER_RESUME";
    static final String ACTION_STATE_CHANGED =
            "com.personal.studytracker.FOCUS_TIMER_STATE_CHANGED";

    @Override public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        SharedPreferences prefs = context.getSharedPreferences(
                TimerAlarmScheduler.PREFS, Context.MODE_PRIVATE);
        if (!prefs.getBoolean(TimerAlarmScheduler.KEY_RUNNING, false)) {
            FocusTimerNotificationService.stop(context);
            return;
        }

        long now = System.currentTimeMillis();
        boolean paused = prefs.getBoolean(TimerAlarmScheduler.KEY_PAUSED, false);
        boolean stopwatch = prefs.getBoolean(TimerAlarmScheduler.KEY_STOPWATCH, false);
        String action = intent.getAction();
        if (ACTION_PAUSE.equals(action) && !paused) {
            SharedPreferences.Editor edit = prefs.edit()
                    .putBoolean(TimerAlarmScheduler.KEY_PAUSED, true);
            if (stopwatch) {
                long segment = Math.max(0L, now - prefs.getLong(
                        TimerAlarmScheduler.KEY_START_MILLIS, now)) / 1000L;
                long elapsed = prefs.getInt(TimerAlarmScheduler.KEY_ELAPSED_SECONDS, 0)
                        + segment;
                edit.putInt(TimerAlarmScheduler.KEY_ELAPSED_SECONDS,
                        (int) Math.min(Integer.MAX_VALUE, elapsed));
            } else {
                long remaining = Math.max(0L, prefs.getLong(
                        TimerAlarmScheduler.KEY_END_MILLIS, now) - now);
                if (remaining == 0L) return;
                edit.putLong(TimerAlarmScheduler.KEY_PAUSED_REMAINING_MILLIS, remaining);
                TimerAlarmScheduler.cancelTimer(context);
            }
            edit.apply();
        } else if (ACTION_RESUME.equals(action) && paused) {
            long remaining = prefs.getLong(
                    TimerAlarmScheduler.KEY_PAUSED_REMAINING_MILLIS, 0L);
            String token = prefs.getString(TimerAlarmScheduler.KEY_TOKEN, "");
            SharedPreferences.Editor edit = prefs.edit()
                    .putBoolean(TimerAlarmScheduler.KEY_PAUSED, false)
                    .putLong(TimerAlarmScheduler.KEY_START_MILLIS, now);
            if (!stopwatch) {
                long endAt = now + remaining;
                edit.putLong(TimerAlarmScheduler.KEY_END_MILLIS, endAt);
                TimerAlarmScheduler.scheduleTimer(context, endAt, token);
            }
            edit.apply();
        } else {
            return;
        }

        FocusTimerNotificationService.showOrUpdate(context);
        context.sendBroadcast(new Intent(ACTION_STATE_CHANGED)
                .setPackage(context.getPackageName()));
    }
}
