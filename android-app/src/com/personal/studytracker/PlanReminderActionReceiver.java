package com.personal.studytracker;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public final class PlanReminderActionReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        String planId = intent.getStringExtra(PlanReminderScheduler.EXTRA_PLAN_ID);
        if (planId == null || planId.isEmpty()) return;

        android.app.NotificationManager manager =
                context.getSystemService(android.app.NotificationManager.class);
        if (manager != null) {
            manager.cancel(PlanReminderScheduler.notificationId(context, planId));
        }

        if (PlanReminderReceiver.ACTION_SNOOZE.equals(intent.getAction())) {
            String title = intent.getStringExtra(PlanReminderScheduler.EXTRA_PLAN_TITLE);
            PlanReminderScheduler.scheduleSnooze(context, planId,
                    title == null ? "Planned activity" : title);
        } else {
            PlanReminderScheduler.cancelPlan(context, planId);
        }
    }
}
