package com.personal.studytracker;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

final class PlanReminderScheduler {
    static final String EXTRA_PLAN_ID = "plan_id";
    static final String EXTRA_PLAN_TITLE = "plan_title";
    static final String EXTRA_SNOOZE = "plan_snooze";
    private static final String SNOOZE_PREFS = "plan_reminder_snoozes";
    private static final String NOTIFICATION_ID_PREFS = "plan_notification_ids";
    private static final String TAG = "StudyTrackerPlans";

    static void scheduleAll(Context context) {
        try {
            TrackerStore store = new TrackerStore(context.getFilesDir());
            for (TrackerStore.PlanItem plan : store.getPlans()) {
                schedulePlan(context, plan, true);
            }
        } catch (IOException exception) {
            Log.e(TAG, "Could not load plans to schedule reminders", exception);
        }
    }

    static void schedulePlan(Context context, TrackerStore.PlanItem plan) {
        schedulePlan(context, plan, false);
    }

    private static void schedulePlan(
            Context context, TrackerStore.PlanItem plan, boolean restoreSnooze) {
        cancelScheduled(context, plan.id);
        if (!restoreSnooze) clearSnoozeState(context, plan.id);
        if (!plan.done) {
            if (plan.time != null && !plan.time.trim().isEmpty()
                    && PlanReminderReceiver.notificationsAvailable(context)) {
                try {
                    LocalDate day = LocalDate.parse(plan.date);
                    LocalTime time = LocalTime.parse(plan.time);
                    ZonedDateTime due = ZonedDateTime.of(day, time, ZoneId.systemDefault());
                    if (due.isAfter(ZonedDateTime.now())) {
                        schedule(context, plan.id, plan.text,
                                due.toInstant().toEpochMilli(), false);
                    }
                } catch (RuntimeException exception) {
                    Log.e(TAG, "Could not schedule reminder for plan " + plan.id, exception);
                }
            }
            if (restoreSnooze) restoreSnooze(context, plan);
        }
    }

    static void scheduleSnooze(Context context, String planId, String title) {
        if (!PlanReminderReceiver.notificationsAvailable(context)) return;
        long snoozeAt = System.currentTimeMillis() + 5 * 60 * 1000L;
        context.getSharedPreferences(SNOOZE_PREFS, Context.MODE_PRIVATE).edit()
                .putLong(snoozeKey(planId), snoozeAt).apply();
        schedule(context, planId, title,
                snoozeAt, true);
    }

    static void cancelPlan(Context context, String planId) {
        cancelScheduled(context, planId);
        clearSnoozeState(context, planId);
        android.app.NotificationManager notifications =
                context.getSystemService(android.app.NotificationManager.class);
        if (notifications != null) notifications.cancel(notificationId(context, planId));
    }

    private static void cancelScheduled(Context context, String planId) {
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (manager == null) return;
        for (boolean snooze : new boolean[]{false, true}) {
            PendingIntent pending = pendingIntent(context, planId, "", snooze,
                    PendingIntent.FLAG_NO_CREATE);
            if (pending != null) {
                manager.cancel(pending);
                pending.cancel();
            }
        }
    }

    static void clearSnooze(Context context, String planId) {
        clearSnoozeState(context, planId);
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (manager == null) return;
        PendingIntent pending = pendingIntent(context, planId, "", true,
                PendingIntent.FLAG_NO_CREATE);
        if (pending != null) {
            manager.cancel(pending);
            pending.cancel();
        }
    }

    private static void clearSnoozeState(Context context, String planId) {
        context.getSharedPreferences(SNOOZE_PREFS, Context.MODE_PRIVATE).edit()
                .remove(snoozeKey(planId)).apply();
    }

    private static void restoreSnooze(Context context, TrackerStore.PlanItem plan) {
        long snoozeAt = context.getSharedPreferences(SNOOZE_PREFS, Context.MODE_PRIVATE)
                .getLong(snoozeKey(plan.id), 0L);
        if (snoozeAt <= 0L) return;
        if (!PlanReminderReceiver.notificationsAvailable(context)) {
            clearSnoozeState(context, plan.id);
            return;
        }
        schedule(context, plan.id, plan.text,
                Math.max(System.currentTimeMillis(), snoozeAt), true);
    }

    private static String snoozeKey(String planId) {
        return "snooze_" + planId;
    }

    static TrackerStore.PlanItem findPlan(Context context, String planId) throws IOException {
        TrackerStore store = new TrackerStore(context.getFilesDir());
        List<TrackerStore.PlanItem> plans = store.getPlans();
        for (TrackerStore.PlanItem plan : plans) {
            if (plan.id.equals(planId)) return plan;
        }
        return null;
    }

    static synchronized int notificationId(Context context, String planId) {
        android.content.SharedPreferences prefs = context.getSharedPreferences(
                NOTIFICATION_ID_PREFS, Context.MODE_PRIVATE);
        int saved = prefs.getInt(planId, 0);
        if (saved != 0) return saved;

        java.util.Set<Integer> used = new java.util.HashSet<>();
        for (Object value : prefs.getAll().values()) {
            if (value instanceof Integer) used.add((Integer) value);
        }
        int candidate = planId.hashCode() & Integer.MAX_VALUE;
        if (candidate == 0) candidate = 1;
        while (used.contains(candidate)) {
            candidate = candidate == Integer.MAX_VALUE ? 1 : candidate + 1;
        }
        prefs.edit().putInt(planId, candidate).apply();
        return candidate;
    }

    private static void schedule(
            Context context, String planId, String title, long when, boolean snooze) {
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (manager == null) {
            Log.e(TAG, "AlarmManager unavailable; plan reminder was not scheduled");
            return;
        }
        PendingIntent pending = pendingIntent(context, planId, title, snooze,
                PendingIntent.FLAG_UPDATE_CURRENT);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                    && manager.canScheduleExactAlarms()) {
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, pending);
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, pending);
            } else {
                manager.setExact(AlarmManager.RTC_WAKEUP, when, pending);
            }
        } catch (SecurityException exception) {
            Log.w(TAG, "Exact alarm access unavailable; using an inexact plan reminder", exception);
            manager.set(AlarmManager.RTC_WAKEUP, when, pending);
        }
    }

    private static PendingIntent pendingIntent(
            Context context, String planId, String title, boolean snooze, int flag) {
        Intent intent = new Intent(context, PlanReminderReceiver.class)
                .setData(android.net.Uri.parse("daybyday://plan/" + planId
                        + (snooze ? "/snooze" : "/scheduled")))
                .putExtra(EXTRA_PLAN_ID, planId)
                .putExtra(EXTRA_PLAN_TITLE, title)
                .putExtra(EXTRA_SNOOZE, snooze);
        return PendingIntent.getBroadcast(context, 0, intent,
                flag | PendingIntent.FLAG_IMMUTABLE);
    }

    private PlanReminderScheduler() {}
}
