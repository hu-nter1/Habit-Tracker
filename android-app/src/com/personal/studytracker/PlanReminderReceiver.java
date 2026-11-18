package com.personal.studytracker;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.os.Build;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import java.io.IOException;
import java.time.LocalDate;

public final class PlanReminderReceiver extends BroadcastReceiver {
    static final String CHANNEL_ID = "planned_activity_reminders";
    static final String ACTION_DISMISS = "com.personal.studytracker.PLAN_REMINDER_DISMISS";
    static final String ACTION_SNOOZE = "com.personal.studytracker.PLAN_REMINDER_SNOOZE";
    private static final String TAG = "StudyTrackerPlans";

    @Override public void onReceive(Context context, Intent intent) {
        String planId = intent == null ? null
                : intent.getStringExtra(PlanReminderScheduler.EXTRA_PLAN_ID);
        if (planId == null || planId.isEmpty()) {
            Log.e(TAG, "Received a plan reminder without a plan ID");
            return;
        }
        try {
            TrackerStore.PlanItem plan = PlanReminderScheduler.findPlan(context, planId);
            boolean snooze = intent.getBooleanExtra(PlanReminderScheduler.EXTRA_SNOOZE, false);
            if (plan == null || plan.done || (!snooze
                    && !LocalDate.now().toString().equals(plan.date))) {
                PlanReminderScheduler.cancelPlan(context, planId);
                return;
            }
            if (!notificationsAvailable(context)) {
                Log.w(TAG, "Notifications are disabled; could not show plan reminder " + planId);
                return;
            }
            ensureNotificationChannel(context);
            postNotification(context, plan.id, plan.text);
        } catch (IOException exception) {
            Log.e(TAG, "Could not load the plan for its reminder", exception);
        }
    }

    static void ensureNotificationChannel(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null || manager.getNotificationChannel(CHANNEL_ID) != null) return;
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                "Planned activity reminders", NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription("A notification at the time you scheduled an activity");
        channel.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build());
        manager.createNotificationChannel(channel);
    }

    static boolean notificationsAvailable(Context context) {
        if (!androidx.core.app.NotificationManagerCompat.from(context)
                .areNotificationsEnabled()) return false;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return true;
        ensureNotificationChannel(context);
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null) return false;
        NotificationChannel channel = manager.getNotificationChannel(CHANNEL_ID);
        return channel != null && channel.getImportance() != NotificationManager.IMPORTANCE_NONE;
    }

    private static void postNotification(Context context, String planId, String title) {
        int notificationId = PlanReminderScheduler.notificationId(planId);
        Intent open = new Intent(context, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent content = PendingIntent.getActivity(context, notificationId, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent dismissIntent = new Intent(context, PlanReminderActionReceiver.class)
                .setAction(ACTION_DISMISS)
                .setData(android.net.Uri.parse("daybyday://plan-action/" + planId + "/dismiss"))
                .putExtra(PlanReminderScheduler.EXTRA_PLAN_ID, planId);
        PendingIntent dismiss = PendingIntent.getBroadcast(context, notificationId,
                dismissIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent snoozeIntent = new Intent(context, PlanReminderActionReceiver.class)
                .setAction(ACTION_SNOOZE)
                .setData(android.net.Uri.parse("daybyday://plan-action/" + planId + "/snooze"))
                .putExtra(PlanReminderScheduler.EXTRA_PLAN_ID, planId)
                .putExtra(PlanReminderScheduler.EXTRA_PLAN_TITLE, title);
        PendingIntent snooze = PendingIntent.getBroadcast(context, notificationId,
                snoozeIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder notification = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle(title)
                .setContentText("It's time for the activity you planned.")
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(content)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Dismiss", dismiss)
                .addAction(android.R.drawable.ic_lock_idle_alarm, "Snooze 5 min", snooze);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            notification.setSound(RingtoneManager.getDefaultUri(
                    RingtoneManager.TYPE_NOTIFICATION));
        }
        NotificationManagerCompat.from(context).notify(notificationId, notification.build());
    }
}
