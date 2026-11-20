package com.personal.studytracker;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public final class AlarmActionReceiver extends BroadcastReceiver {
    static final String ACTION_STOP = "com.personal.studytracker.ALARM_STOP";
    static final String ACTION_SNOOZE = "com.personal.studytracker.ALARM_SNOOZE";
    static final String ACTION_STOP_REMINDER = "com.personal.studytracker.ALARM_STOP_REMINDER";
    static final String ACTION_SNOOZE_REMINDER =
            "com.personal.studytracker.ALARM_SNOOZE_REMINDER";

    @Override public void onReceive(Context context, Intent intent) {
        String action = intent == null ? "" : intent.getAction();
        if (ACTION_STOP_REMINDER.equals(action)) {
            DailyReminderScheduler.clearSnooze(context,
                    intent.getStringExtra(DailyReminderScheduler.EXTRA_KIND));
            AlarmSoundService.stop(context);
            return;
        }
        if (ACTION_SNOOZE_REMINDER.equals(action)) {
            String kind = intent.getStringExtra(DailyReminderScheduler.EXTRA_KIND);
            DailyReminderScheduler.scheduleSnooze(context, kind);
            AlarmSoundService.stop(context);
            return;
        }
        if (ACTION_SNOOZE.equals(action)) {
            TimerAlarmScheduler.scheduleSnooze(context);
        } else {
            TimerAlarmScheduler.cancelSnooze(context);
        }
        context.getSharedPreferences(TimerAlarmScheduler.PREFS, Context.MODE_PRIVATE)
                .edit().putBoolean(TimerAlarmScheduler.KEY_ALARM_RINGING, false).apply();
        AlarmSoundService.stop(context);
    }
}
