package com.personal.studytracker;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

public final class TimerAlarmReceiver extends BroadcastReceiver {
    private static final String TAG = "StudyTrackerAlarm";

    @Override public void onReceive(Context context, Intent intent) {
        String kind = intent == null ? "" : intent.getStringExtra(TimerAlarmScheduler.EXTRA_KIND);
        if (TimerAlarmScheduler.KIND_SNOOZE.equals(kind)) {
            context.getSharedPreferences(TimerAlarmScheduler.PREFS, Context.MODE_PRIVATE)
                    .edit().remove(TimerAlarmScheduler.KEY_SNOOZE_END_MILLIS)
                    .putBoolean(TimerAlarmScheduler.KEY_ALARM_RINGING, true).apply();
            startAlarmService(context, "Focus timer", "Your 5-minute snooze is over.");
            return;
        }

        SharedPreferences prefs = context.getSharedPreferences(
                TimerAlarmScheduler.PREFS, Context.MODE_PRIVATE);
        String expectedToken = intent == null ? "" :
                intent.getStringExtra(TimerAlarmScheduler.EXTRA_TOKEN);
        if (!prefs.getBoolean(TimerAlarmScheduler.KEY_RUNNING, false)
                || prefs.getBoolean(TimerAlarmScheduler.KEY_PAUSED, false)
                || prefs.getBoolean(TimerAlarmScheduler.KEY_STOPWATCH, false)
                || expectedToken == null
                || !expectedToken.equals(prefs.getString(TimerAlarmScheduler.KEY_TOKEN, ""))) {
            return;
        }

        long startMillis = prefs.getLong(TimerAlarmScheduler.KEY_SESSION_START_MILLIS,
                prefs.getLong(TimerAlarmScheduler.KEY_START_MILLIS, 0L));
        int durationSeconds = prefs.getInt(TimerAlarmScheduler.KEY_DURATION_SECONDS, 0);
        String topic = prefs.getString(TimerAlarmScheduler.KEY_TOPIC, "");
        String topicId = prefs.getString(TimerAlarmScheduler.KEY_TOPIC_ID, "");
        boolean interrupted = prefs.getBoolean(TimerAlarmScheduler.KEY_INTERRUPTED, false);

        boolean saved = true;
        try {
            LocalDateTime start = LocalDateTime.ofInstant(
                    Instant.ofEpochMilli(startMillis), ZoneId.systemDefault());
            LocalDateTime end = LocalDateTime.now();
            TrackerStore store = new TrackerStore(context.getFilesDir());
            store.addSession(start.toLocalDate().toString(),
                    start.format(DateTimeFormatter.ofPattern("HH:mm")),
                    end.format(DateTimeFormatter.ofPattern("HH:mm")),
                    durationSeconds, topicId, topic, interrupted);
        } catch (IOException | RuntimeException exception) {
            saved = false;
            Log.e(TAG, "Could not save completed focus session", exception);
        }

        prefs.edit()
                .putBoolean(TimerAlarmScheduler.KEY_RUNNING, false)
                .putBoolean(TimerAlarmScheduler.KEY_PAUSED, false)
                .putBoolean(TimerAlarmScheduler.KEY_ALARM_RINGING, true)
                .apply();
        TimerAlarmScheduler.cancelTimer(context);
        String message = saved
                ? (topic.isEmpty() ? "Your study session is complete." : "Finished: " + topic)
                : "Timer ended, but the session could not be saved. Please check tracker data.";
        startAlarmService(context, "Focus timer complete", message);
    }

    static void startAlarmService(Context context, String title, String message) {
        Intent service = new Intent(context, AlarmSoundService.class)
                .putExtra(AlarmSoundService.EXTRA_TITLE, title)
                .putExtra(AlarmSoundService.EXTRA_MESSAGE, message)
                .putExtra(DailyReminderScheduler.EXTRA_SOUND,
                        context.getSharedPreferences(TimerAlarmScheduler.PREFS,
                                Context.MODE_PRIVATE)
                                .getString(TimerAlarmScheduler.KEY_SOUND, ""));
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(service);
            } else {
                context.startService(service);
            }
        } catch (RuntimeException exception) {
            Log.e(TAG, "Could not start focus alarm service", exception);
        }
    }
}
