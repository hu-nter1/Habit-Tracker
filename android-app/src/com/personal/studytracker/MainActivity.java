package com.personal.studytracker;

import android.app.AlertDialog;
import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.app.AlarmManager;
import android.app.TimePickerDialog;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.media.RingtoneManager;
import android.os.Bundle;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.CheckBox;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.ArrayAdapter;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.app.DatePickerDialog;

import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;

import java.io.IOException;
import java.time.Instant;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class MainActivity extends AppCompatActivity {
    private static final String TAG = "StudyTracker";
    private static final String GREEN = "#21594D";
    private static final String MUTED = "#61746B";
    private static final String PAGE_TEXT = "#E9F3E9";
    private static final String PAGE_MUTED = "#B6CCBD";
    private static final String LINE = "#CAD8CE";
    private static final String WHITE = "#F7FAF6";
    private static final String AMBER = "#D97757";
    private static final String[] TABS = {
            "Today", "Habits", "Study", "Review", "Learn"
    };
    private static final int[] TAB_ICONS = {
            R.drawable.ic_tab_today, R.drawable.ic_tab_daily,
            R.drawable.ic_tab_sessions, R.drawable.ic_tab_reviews, R.drawable.ic_tab_topics
    };
    private static final int[] TOPIC_ACCENTS = {
            R.color.topic_accent_1, R.color.topic_accent_2, R.color.topic_accent_3,
            R.color.topic_accent_4, R.color.topic_accent_5, R.color.topic_accent_6,
            R.color.topic_accent_7, R.color.topic_accent_8, R.color.topic_accent_9
    };

    private TrackerStore store;
    private LinearLayout content;
    private TabLayout tabs;
    private ScrollView scroll;
    private String activeTab = "Today";
    private LocalDate reviewWeekStart = LocalDate.now().with(DayOfWeek.MONDAY);
    private YearMonth reviewMonth = YearMonth.now();
    private LocalDate habitDate = LocalDate.now();
    private boolean showHabitWeek = false;
    private boolean showAllTodayStreaks;
    private boolean showTargets = false;
    private int sessionHistoryLimit = 20;
    private boolean remindersExpanded;
    private String pendingReminderKind;
    private boolean timerRunning;
    private boolean resetScrollOnRender;
    private long timerStartMillis;
    private long timerEndMillis;
    private int timerDurationSeconds;
    private String timerToken = "";
    private String timerTopicValue = "";
    private String timerTopicIdValue = "";
    private boolean timerInterruptedValue;
    private final Set<String> expandedTopics = new HashSet<>();
    private MaterialAutoCompleteTextView timerTopic;
    private EditText timerMinutes;
    private CheckBox timerInterrupted;
    private TextView timerStatus;
    private TextView timerCountdown;
    private CircularProgressIndicator timerProgress;
    private MaterialButton timerActionButton;
    private FocusSproutView focusSprout;
    private TextView headerDateTime;
    private TextView headerLocation;
    private LocationListener locationListener;
    private final Handler handler = new Handler();
    private static final int REQUEST_LOCATION = 4101;
    private static final int REQUEST_NOTIFICATIONS = 4102;
    private static final int REQUEST_REMINDER_NOTIFICATIONS = 4103;
    private static final int REQUEST_MORNING_RINGTONE = 4104;
    private static final int REQUEST_EVENING_RINGTONE = 4105;
    private static final int REQUEST_PLAN_NOTIFICATIONS = 4106;
    private final Runnable clockTick = new Runnable() {
        @Override public void run() {
            if (headerDateTime != null) {
                headerDateTime.setText(LocalDateTime.now().format(
                        DateTimeFormatter.ofPattern("EEE, MMM d · h:mm a", Locale.getDefault())));
                handler.postDelayed(this, 1000);
            }
        }
    };

    private String reminderEnabledKey(String kind) {
        return DailyReminderScheduler.MORNING.equals(kind)
                ? DailyReminderScheduler.MORNING_ENABLED
                : DailyReminderScheduler.EVENING_ENABLED;
    }

    private boolean alarmNotificationsAvailable() {
        return AlarmSoundService.notificationsAvailable(this);
    }

    private void openAlarmNotificationSettings() {
        Intent settings;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            settings = new Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName())
                    .putExtra(Settings.EXTRA_CHANNEL_ID, AlarmSoundService.CHANNEL_ID);
        } else {
            settings = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
        }
        try {
            startActivity(settings);
        } catch (RuntimeException exception) {
            Log.e(TAG, "Could not open alarm notification settings", exception);
            Toast.makeText(this, "Open Day by Day's notification settings to enable alarms.",
                    Toast.LENGTH_LONG).show();
        }
    }

    private void dismissAlarm() {
        getSharedPreferences(TimerAlarmScheduler.PREFS, MODE_PRIVATE).edit()
                .putBoolean(TimerAlarmScheduler.KEY_ALARM_RINGING, false)
                .apply();
        TimerAlarmScheduler.cancelSnooze(this);
        AlarmSoundService.stop(this);
        render();
    }

    private final Runnable timerTick = new Runnable() {
        @Override public void run() {
            if (!timerRunning || timerStatus == null) return;
            long remaining = Math.max(0,
                    (timerEndMillis - System.currentTimeMillis() + 999) / 1000);
            if (remaining == 0) {
                boolean stillRunning = getSharedPreferences(
                        TimerAlarmScheduler.PREFS, MODE_PRIVATE)
                        .getBoolean(TimerAlarmScheduler.KEY_RUNNING, false);
                if (!stillRunning) {
                    reloadStore();
                    restoreTimerState();
                    render();
                    return;
                }
                if (!alarmNotificationsAvailable()) {
                    stopTimer();
                    Toast.makeText(MainActivity.this,
                            "Notifications are off. Your session was saved without an alarm.",
                            Toast.LENGTH_LONG).show();
                    return;
                }
                timerStatus.setText("Finishing your session…");
                handler.postDelayed(this, 300);
                return;
            }
            timerStatus.setText("Focus session in progress");
            if (timerCountdown != null) {
                String countdown = formatClock((int) remaining);
                timerCountdown.setText(countdown);
                timerCountdown.setContentDescription("Time remaining " + countdown);
            }
            if (timerProgress != null) {
                timerProgress.setProgress(Math.max(0,
                        timerDurationSeconds - (int) remaining));
            }
            if (focusSprout != null) {
                focusSprout.setProgress(1f - (float) remaining
                        / Math.max(1, timerDurationSeconds));
            }
            handler.postDelayed(this, 500);
        }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        try {
            store = new TrackerStore(getFilesDir());
        } catch (IOException exception) {
            Toast.makeText(this, exception.getMessage(), Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        setContentView(R.layout.activity_main);
        AlarmSoundService.ensureNotificationChannel(this);
        PlanReminderReceiver.ensureNotificationChannel(this);
        content = findViewById(R.id.content);
        tabs = findViewById(R.id.tabs);
        scroll = findViewById(R.id.main_scroll);
        scroll.setBackground(new AnimatedPageBackground(
                getDrawable(R.drawable.bg_botanical_overlay)));
        headerDateTime = findViewById(R.id.header_datetime);
        headerLocation = findViewById(R.id.header_location);
        headerLocation.setOnClickListener(view -> requestLocation());
        remindersExpanded = getSharedPreferences(
                DailyReminderScheduler.PREFS, MODE_PRIVATE)
                .getBoolean("settings_expanded", false);
        restoreTimerState();
        clockTick.run();
        requestLocation();
        createNavigation();
        render();
        if (store.isCurriculumMigrationPending()) {
            content.post(this::showCurriculumMigrationPrompt);
        }
    }

    private void showCurriculumMigrationPrompt() {
        if (isFinishing() || !store.isCurriculumMigrationPending()) return;
        new AlertDialog.Builder(this)
                .setTitle("Keep your learning progress?")
                .setMessage("Some curriculum steps are already checked off. Replace all current "
                        + "topics with the new 13-topic plan, or append the new topics and keep "
                        + "your existing checklists?")
                .setPositiveButton("Replace topics", (dialog, which) -> runStoreAction(() -> {
                    store.migrateCurriculum(false);
                    render();
                }))
                .setNegativeButton("Append topics", (dialog, which) -> runStoreAction(() -> {
                    store.migrateCurriculum(true);
                    render();
                }))
                .setNeutralButton("Not now", null)
                .show();
    }

    @Override protected void onResume() {
        super.onResume();
        if (headerDateTime != null) {
            handler.removeCallbacks(clockTick);
            clockTick.run();
        }
        if (content != null) {
            DailyReminderScheduler.scheduleAll(this);
            PlanReminderScheduler.scheduleAll(this);
            restoreTimerState();
            if ("Study".equals(activeTab) || "Today".equals(activeTab)) {
                reloadStore();
                render();
                if (timerRunning && "Study".equals(activeTab)) {
                    handler.removeCallbacks(timerTick);
                    handler.post(timerTick);
                }
            }
        }
    }

    @Override protected void onPause() {
        handler.removeCallbacks(clockTick);
        handler.removeCallbacks(timerTick);
        removeLocationUpdates();
        super.onPause();
    }

    @Override public void onRequestPermissionsResult(
            int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_LOCATION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                locateDeviceArea();
            } else if (headerLocation != null) {
                headerLocation.setText("Location unavailable · tap to allow");
            }
        } else if (requestCode == REQUEST_NOTIFICATIONS
                && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startTimer();
        } else if (requestCode == REQUEST_NOTIFICATIONS) {
            Toast.makeText(this, "Allow notifications to use focus timer alarms.",
                    Toast.LENGTH_LONG).show();
        } else if (requestCode == REQUEST_REMINDER_NOTIFICATIONS) {
            if (grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (pendingReminderKind != null) {
                    getSharedPreferences(DailyReminderScheduler.PREFS, MODE_PRIVATE)
                            .edit().putBoolean(reminderEnabledKey(pendingReminderKind), true)
                            .apply();
                    pendingReminderKind = null;
                }
                prepareEnabledReminders();
            } else {
                if (pendingReminderKind != null) {
                    getSharedPreferences(DailyReminderScheduler.PREFS, MODE_PRIVATE)
                            .edit().putBoolean(reminderEnabledKey(pendingReminderKind), false)
                            .apply();
                    DailyReminderScheduler.scheduleIfEnabled(this, pendingReminderKind);
                    pendingReminderKind = null;
                    render();
                }
                Toast.makeText(this, "Allow notifications so your reminder can show its check-in.",
                        Toast.LENGTH_LONG).show();
            }
        } else if (requestCode == REQUEST_PLAN_NOTIFICATIONS) {
            if (grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                PlanReminderScheduler.scheduleAll(this);
                requestExactAlarmAccessIfNeeded();
            } else {
                Toast.makeText(this,
                        "Plan saved. Turn on notifications to get its scheduled reminder.",
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if ((requestCode == REQUEST_MORNING_RINGTONE
                || requestCode == REQUEST_EVENING_RINGTONE) && resultCode == RESULT_OK) {
            Uri sound = data == null ? null
                    : data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI);
            String kind = requestCode == REQUEST_MORNING_RINGTONE
                    ? DailyReminderScheduler.MORNING : DailyReminderScheduler.EVENING;
            String key = DailyReminderScheduler.MORNING.equals(kind)
                    ? DailyReminderScheduler.MORNING_SOUND
                    : DailyReminderScheduler.EVENING_SOUND;
            getSharedPreferences(DailyReminderScheduler.PREFS, MODE_PRIVATE).edit()
                    .putString(key, sound == null ? "" : sound.toString()).apply();
            Toast.makeText(this, "Reminder sound saved", Toast.LENGTH_SHORT).show();
            if ("Today".equals(activeTab)) render();
        }
    }

    private void requestLocation() {
        if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            locateDeviceArea();
        } else {
            headerLocation.setText("Tap to show your area");
            requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION},
                    REQUEST_LOCATION);
        }
    }

    private void locateDeviceArea() {
        if (headerLocation == null) return;
        headerLocation.setText("Finding your area…");
        LocationManager manager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (manager == null || !manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            headerLocation.setText("Location services are off · tap to retry");
            return;
        }
        try {
            Location last = manager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            if (last != null) showLocationName(last);
            locationListener = new LocationListener() {
                @Override public void onLocationChanged(Location location) {
                    removeLocationUpdates();
                    showLocationName(location);
                }
                @Override public void onProviderDisabled(String provider) {
                    if (headerLocation != null) headerLocation.setText("Location unavailable · tap to retry");
                }
            };
            manager.requestSingleUpdate(LocationManager.NETWORK_PROVIDER,
                    locationListener, Looper.getMainLooper());
            handler.postDelayed(() -> {
                if (headerLocation != null && "Finding your area…".contentEquals(headerLocation.getText())) {
                    headerLocation.setText("Area unavailable · tap to retry");
                    removeLocationUpdates();
                }
            }, 12000);
        } catch (SecurityException exception) {
            headerLocation.setText("Location permission needed · tap to allow");
        }
    }

    private void showLocationName(Location location) {
        Thread geocoderThread = new Thread(() -> {
            String place = null;
            try {
                if (Geocoder.isPresent()) {
                    List<Address> addresses = new Geocoder(this, Locale.getDefault())
                            .getFromLocation(location.getLatitude(), location.getLongitude(), 1);
                    if (addresses != null && !addresses.isEmpty()) {
                        Address address = addresses.get(0);
                        place = firstNonEmpty(address.getLocality(),
                                address.getSubAdminArea(), address.getAdminArea());
                        if (place != null && address.getCountryName() != null
                                && !address.getCountryName().isEmpty()
                                && !place.equals(address.getCountryName())) {
                            place += ", " + address.getCountryName();
                        }
                    }
                }
            } catch (IOException | RuntimeException exception) {
                Log.w(TAG, "Could not resolve a city name for the current area", exception);
            }
            String result = place;
            handler.post(() -> {
                if (headerLocation == null) return;
                if (result != null) headerLocation.setText(result);
                else headerLocation.setText("Location active · area name unavailable");
            });
        }, "study-tracker-geocoder");
        geocoderThread.start();
    }

    private String firstNonEmpty(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) return value.trim();
        }
        return null;
    }

    private void removeLocationUpdates() {
        if (locationListener == null) return;
        LocationManager manager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (manager != null) manager.removeUpdates(locationListener);
        locationListener = null;
    }

    private void restoreTimerState() {
        android.content.SharedPreferences prefs = getSharedPreferences(
                TimerAlarmScheduler.PREFS, MODE_PRIVATE);
        timerRunning = prefs.getBoolean(TimerAlarmScheduler.KEY_RUNNING, false);
        timerStartMillis = prefs.getLong(TimerAlarmScheduler.KEY_START_MILLIS, 0L);
        timerEndMillis = prefs.getLong(TimerAlarmScheduler.KEY_END_MILLIS, 0L);
        timerDurationSeconds = prefs.getInt(TimerAlarmScheduler.KEY_DURATION_SECONDS, 0);
        timerToken = prefs.getString(TimerAlarmScheduler.KEY_TOKEN, "");
        timerTopicValue = prefs.getString(TimerAlarmScheduler.KEY_TOPIC, "");
        timerTopicIdValue = prefs.getString(TimerAlarmScheduler.KEY_TOPIC_ID, "");
        if (timerTopicIdValue.isEmpty()) {
            timerTopicIdValue = topicIdForName(timerTopicValue);
        }
        timerInterruptedValue = prefs.getBoolean(TimerAlarmScheduler.KEY_INTERRUPTED, false);
        if (timerRunning && !timerToken.isEmpty()) {
            long dueAt = Math.max(System.currentTimeMillis(), timerEndMillis);
            TimerAlarmScheduler.scheduleTimer(this, dueAt, timerToken);
        }
    }

    private void reloadStore() {
        try {
            store = new TrackerStore(getFilesDir());
        } catch (IOException exception) {
            Log.e(TAG, "Could not reload tracker data after the focus alarm", exception);
            showError(exception);
        }
    }

    private void createNavigation() {
        tabs.removeAllTabs();
        for (int i = 0; i < TABS.length; i++) {
            TabLayout.Tab tab = tabs.newTab().setText(TABS[i])
                    .setIcon(TAB_ICONS[i]);
            tabs.addTab(tab, false);
        }
        tabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override public void onTabSelected(TabLayout.Tab tab) {
                activeTab = TABS[tab.getPosition()];
                resetScrollOnRender = true;
                render();
            }
            @Override public void onTabUnselected(TabLayout.Tab tab) {}
            @Override public void onTabReselected(TabLayout.Tab tab) {}
        });
        int selected = java.util.Arrays.asList(TABS).indexOf(activeTab);
        tabs.getTabAt(Math.max(selected, 0)).select();
    }

    private void render() {
        int previousScroll = scroll.getScrollY();
        boolean resetScroll = resetScrollOnRender;
        resetScrollOnRender = false;
        content.removeAllViews();
        switch (activeTab) {
            case "Today": renderToday(); break;
            case "Habits": renderDaily(); break;
            case "Study": renderSessions(); break;
            case "Review": renderReviews(); break;
            case "Learn": renderTopics(); break;
            default: renderToday();
        }
        if (resetScroll || "Today".equals(activeTab)) scroll.post(() -> scroll.scrollTo(0, 0));
        else scroll.post(() -> scroll.scrollTo(0, previousScroll));
    }

    private void renderToday() {
        LocalDate today = LocalDate.now();
        pageHeader(greeting() + ",", today.format(
                DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.getDefault())));

        List<TrackerStore.Habit> habits = store.getHabits();
        Map<String, Boolean> checks = store.getChecks().get(today.toString());
        int completedHabits = 0;
        for (TrackerStore.Habit habit : habits) {
            if (checks != null && Boolean.TRUE.equals(checks.get(habit.id))) completedHabits++;
        }

        int targetCount = 0;
        int targetHits = 0;
        int todayStudySeconds = 0;
        for (TrackerStore.TargetEntry entry : store.getTargets()) {
            if (today.toString().equals(entry.date)) {
                targetCount++;
                if ("true".equals(entry.hit)) targetHits++;
            }
        }
        for (TrackerStore.Session session : store.getSessions()) {
            if (today.toString().equals(session.date)) todayStudySeconds += session.durationSeconds;
        }
        for (TrackerStore.ReadingLog reading : store.getReadingLogs()) {
            if (today.toString().equals(reading.date)) {
                todayStudySeconds += reading.durationMinutes * 60;
            }
        }

        LinearLayout summary = addCardBody(content, Color.parseColor(GREEN));
        summary.addView(text("A quick look at today", 15, true));
        LinearLayout firstMetrics = row();
        addMetricTile(firstMetrics, "Habits",
                habits.isEmpty() ? "—" : completedHabits + "/" + habits.size(), GREEN);
        addMetricTile(firstMetrics, "Study time", todayStudySeconds == 0
                ? "Not yet" : formatDuration(todayStudySeconds), GREEN);
        summary.addView(firstMetrics);
        LinearLayout secondMetrics = row();
        addMetricTile(secondMetrics, "Goals hit",
                targetCount == 0 ? "—" : targetHits + "/" + targetCount, AMBER);
        TrackerMetrics.CurriculumProgress curriculum = TrackerMetrics.curriculum(store);
        addMetricTile(secondMetrics, "Curriculum", String.format(Locale.getDefault(),
                curriculum.totalItems == 0 ? "—" : "%.0f%%", curriculum.percentage()), AMBER);
        summary.addView(secondMetrics);

        MaterialButton focusButton = primaryButton();
        focusButton.setText(timerRunning ? "Return to your focus session" : "Start a focus session");
        focusButton.setOnClickListener(view -> selectTab("Study"));
        content.addView(focusButton, cardParams());

        renderDailyReminders();

        sectionTitle("Today's plan");
        renderPlanItems(content, today);
        addSecondaryButton(content, "Add to today's plan", () -> openPlanDialog(null, today));

        sectionTitle("Today's habits");
        if (habits.isEmpty()) {
            addHint("Your routine starts here. Add a habit to build a steady daily rhythm.");
            addPrimaryButton(content, "Add your first habit", () -> {
                selectTab("Habits");
                content.post(() -> editHabit(null));
            });
        } else {
            LinearLayout habitProgress = addCardBody(content, Color.parseColor(GREEN));
            TextView progressLabel = text(completedHabits + " of " + habits.size()
                    + " habits complete", 13, true);
            habitProgress.addView(progressLabel);
            LinearProgressIndicator progress = new LinearProgressIndicator(this);
            progress.setMax(100);
            progress.setProgress(completedHabits * 100 / habits.size());
            progress.setIndicatorColor(Color.parseColor(GREEN));
            progress.setTrackColor(getColor(R.color.outline));
            progress.setTrackCornerRadius(dp(4));
            LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(8));
            progressParams.setMargins(dp(6), dp(2), dp(6), dp(4));
            habitProgress.addView(progress, progressParams);

            for (TrackerStore.Habit habit : habits) {
                LinearLayout body = addCardBody(content, Color.parseColor(
                        habit.core ? AMBER : GREEN));
                CheckBox check = new CheckBox(this);
                check.setText(habit.name + (habit.core ? "  ·  Core" : ""));
                styleCheckBox(check);
                check.setChecked(checks != null
                        && Boolean.TRUE.equals(checks.get(habit.id)));
                check.setOnCheckedChangeListener((button, checked) -> runStoreAction(() -> {
                    store.setCheck(habit.id, today.toString(), checked);
                    render();
                }));
                body.addView(check);
            }
            addSecondaryButton(content, "Manage habits & weekly view",
                    () -> selectTab("Habits"));
        }

        if (targetCount > 0) {
            sectionTitle("Today's targets");
            addHint(targetHits + " of " + targetCount
                    + " targets met. Add or review entries in Targets.");
        }
        if (curriculum.totalItems > 0) {
            sectionTitle("Learning progress");
            addHint(curriculum.completedItems + " of " + curriculum.totalItems
                    + " curriculum items complete.");
            addSecondaryButton(content, "Continue learning", () -> selectTab("Learn"));
        }
        renderComingUp(today);
        renderStreakHighlights();
    }

    private void renderDailyReminders() {
        sectionTitle("Daily reminders");
        android.content.SharedPreferences prefs = getSharedPreferences(
                DailyReminderScheduler.PREFS, MODE_PRIVATE);
        boolean morningEnabled = prefs.getBoolean(
                DailyReminderScheduler.MORNING_ENABLED, false);
        boolean eveningEnabled = prefs.getBoolean(
                DailyReminderScheduler.EVENING_ENABLED, false);
        String morningStatus = reminderStatus(DailyReminderScheduler.MORNING, morningEnabled);
        String eveningStatus = reminderStatus(DailyReminderScheduler.EVENING, eveningEnabled);
        LinearLayout body = addCardBody(content, Color.parseColor(GREEN));
        LinearLayout summary = row();
        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.ic_tab_today);
        icon.setColorFilter(Color.parseColor(GREEN));
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(24), dp(24));
        iconParams.setMarginEnd(dp(8));
        summary.addView(icon, iconParams);
        LinearLayout status = vertical();
        status.addView(text("A nudge for your day", 15, true));
        addCardHintTo(status, morningStatus + " · " + eveningStatus);
        summary.addView(status, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        MaterialButton settings = miniButton(remindersExpanded ? "Done" : "Set up");
        settings.setTextSize(12);
        settings.setMinHeight(dp(40));
        settings.setContentDescription(remindersExpanded
                ? "Hide daily reminder settings" : "Set up daily reminders");
        settings.setOnClickListener(view -> {
            remindersExpanded = !remindersExpanded;
            prefs.edit().putBoolean("settings_expanded", remindersExpanded).apply();
            render();
        });
        summary.addView(settings);
        body.addView(summary);
        body.setOnClickListener(view -> {
            remindersExpanded = !remindersExpanded;
            prefs.edit().putBoolean("settings_expanded", remindersExpanded).apply();
            render();
        });
        if (remindersExpanded) {
            renderDailyReminder(DailyReminderScheduler.MORNING, "Morning plan",
                    "Make a simple plan for the day.", R.drawable.ic_tab_today);
            renderDailyReminder(DailyReminderScheduler.EVENING, "Evening check-in",
                    "Notice what you managed to get done.", R.drawable.ic_tab_reviews);
        }
    }

    private String reminderStatus(String kind, boolean enabled) {
        String label = DailyReminderScheduler.MORNING.equals(kind) ? "Morning" : "Evening";
        if (!enabled) return label + " off";
        LocalTime time = DailyReminderScheduler.getTime(this, kind);
        return label + " " + time.format(DateTimeFormatter.ofPattern(
                DateFormat.is24HourFormat(this) ? "HH:mm" : "h:mm a", Locale.getDefault()));
    }

    private void renderDailyReminder(String kind, String title, String detail, int icon) {
        boolean morning = DailyReminderScheduler.MORNING.equals(kind);
        String enabledKey = morning ? DailyReminderScheduler.MORNING_ENABLED
                : DailyReminderScheduler.EVENING_ENABLED;
        String timeKey = morning ? DailyReminderScheduler.MORNING_TIME
                : DailyReminderScheduler.EVENING_TIME;
        String soundKey = morning ? DailyReminderScheduler.MORNING_SOUND
                : DailyReminderScheduler.EVENING_SOUND;
        android.content.SharedPreferences prefs = getSharedPreferences(
                DailyReminderScheduler.PREFS, MODE_PRIVATE);
        boolean enabled = prefs.getBoolean(enabledKey, false);

        LinearLayout body = addCardBody(content, Color.parseColor(morning ? GREEN : AMBER));
        LinearLayout topRow = row();
        ImageView reminderIcon = new ImageView(this);
        reminderIcon.setImageResource(icon);
        reminderIcon.setColorFilter(Color.parseColor(morning ? GREEN : AMBER));
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(24), dp(24));
        iconParams.setMarginEnd(dp(8));
        topRow.addView(reminderIcon, iconParams);

        LinearLayout titleBlock = vertical();
        titleBlock.addView(text(title, 15, true));
        addCardHintTo(titleBlock, detail);
        topRow.addView(titleBlock, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        CheckBox toggle = new CheckBox(this);
        styleCheckBox(toggle);
        toggle.setChecked(enabled);
        toggle.setContentDescription((enabled ? "Turn off " : "Turn on ") + title);
        toggle.setOnCheckedChangeListener((button, checked) -> {
            if (checked && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                    && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                pendingReminderKind = kind;
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},
                        REQUEST_REMINDER_NOTIFICATIONS);
                return;
            }
            if (checked && !AlarmSoundService.notificationsAvailable(this)) {
                new AlertDialog.Builder(this)
                        .setTitle("Turn on notifications")
                        .setMessage("Daily reminders need notifications so you can dismiss or snooze them.")
                        .setNegativeButton("Not now", null)
                        .setPositiveButton("Open settings", (dialog, which) ->
                                openAlarmNotificationSettings())
                        .show();
                button.setChecked(false);
                return;
            }
            prefs.edit().putBoolean(enabledKey, checked).apply();
            if (checked) {
                prepareEnabledReminders();
            } else {
                DailyReminderScheduler.scheduleIfEnabled(this, kind);
            }
            render();
        });
        topRow.addView(toggle);
        body.addView(topRow);

        LinearLayout actions = row();
        LocalTime time = DailyReminderScheduler.getTime(this, kind);
        MaterialButton timeButton = outlinedButton();
        timeButton.setText(time.format(DateTimeFormatter.ofPattern(
                DateFormat.is24HourFormat(this) ? "HH:mm" : "h:mm a", Locale.getDefault())));
        timeButton.setContentDescription("Set " + title + " time");
        timeButton.setOnClickListener(view -> new TimePickerDialog(this,
                (picker, hour, minute) -> {
                    String formatted = String.format(Locale.ROOT, "%02d:%02d", hour, minute);
                    prefs.edit().putString(timeKey, formatted).apply();
                    DailyReminderScheduler.scheduleIfEnabled(this, kind);
                    if (prefs.getBoolean(enabledKey, false)) prepareEnabledReminders();
                    render();
                }, time.getHour(), time.getMinute(), DateFormat.is24HourFormat(this)).show());
        actions.addView(timeButton);

        Uri savedSound = null;
        String soundValue = prefs.getString(soundKey, "");
        if (!soundValue.isEmpty()) {
            try {
                savedSound = Uri.parse(soundValue);
            } catch (RuntimeException exception) {
                Log.e(TAG, "Invalid saved " + kind + " reminder sound URI", exception);
            }
        }
        Uri defaultSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
        Uri displayedSound = savedSound == null ? defaultSound : savedSound;
        Uri chosenSound = savedSound;
        String soundLabel = "Sound: " + getRingtoneTitle(displayedSound);
        MaterialButton soundButton = outlinedButton();
        soundButton.setText(soundLabel);
        soundButton.setOnClickListener(view -> chooseReminderSound(kind, chosenSound));
        LinearLayout.LayoutParams soundParams = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        soundParams.setMarginStart(dp(8));
        actions.addView(soundButton, soundParams);
        body.addView(actions);
    }

    private String getRingtoneTitle(Uri sound) {
        if (sound == null) return "Device alarm";
        try {
            android.media.Ringtone ringtone = RingtoneManager.getRingtone(this, sound);
            if (ringtone == null) return "Device alarm";
            String title = ringtone.getTitle(this);
            return title == null || title.trim().isEmpty() ? "Device alarm" : title;
        } catch (RuntimeException exception) {
            Log.e(TAG, "Could not read the selected reminder sound", exception);
            return "Device alarm";
        }
    }

    private void chooseReminderSound(String kind, Uri currentSound) {
        Intent picker = new Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                .putExtra(RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI,
                        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM))
                .putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, currentSound);
        try {
            startActivityForResult(picker,
                    DailyReminderScheduler.MORNING.equals(kind)
                            ? REQUEST_MORNING_RINGTONE : REQUEST_EVENING_RINGTONE);
        } catch (android.content.ActivityNotFoundException exception) {
            Log.e(TAG, "No system ringtone picker is available", exception);
            Toast.makeText(this, "This device doesn't provide a ringtone picker.",
                    Toast.LENGTH_LONG).show();
        }
    }

    private void prepareEnabledReminders() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    REQUEST_REMINDER_NOTIFICATIONS);
            return;
        }
        requestExactAlarmAccessIfNeeded();
        DailyReminderScheduler.scheduleAll(this);
    }

    private void requestExactAlarmAccessIfNeeded() {
        AlarmManager manager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                && manager != null && !manager.canScheduleExactAlarms()) {
            try {
                Intent settings = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                        Uri.parse("package:" + getPackageName()));
                startActivity(settings);
                Toast.makeText(this, "Allow exact alarms for reminders at the chosen time.",
                        Toast.LENGTH_LONG).show();
            } catch (RuntimeException exception) {
                Log.e(TAG, "Could not open exact alarm settings", exception);
                Toast.makeText(this, "Reminders are set, but may arrive a little late.",
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    private void renderStreakHighlights() {
        List<TrackerStore.Habit> coreHabits = new ArrayList<>();
        for (TrackerStore.Habit habit : store.getHabits()) {
            if (habit.core) coreHabits.add(habit);
        }
        if (coreHabits.isEmpty()) return;

        sectionTitle("A little momentum");
        int visibleCount = showAllTodayStreaks ? coreHabits.size()
                : Math.min(3, coreHabits.size());
        for (int i = 0; i < visibleCount; i++) {
            TrackerStore.Habit habit = coreHabits.get(i);
            TrackerMetrics.Streak streak = TrackerMetrics.streak(
                    store, habit.id, LocalDate.now());
            LinearLayout body = addCardBody(content, Color.parseColor(AMBER));
            LinearLayout streakRow = row();
            TextView value = text(Integer.toString(streak.current), 36, true);
            value.setTextColor(Color.parseColor(AMBER));
            streakRow.addView(value);
            ImageView flame = new ImageView(this);
            flame.setImageResource(R.drawable.ic_tab_streaks);
            flame.setColorFilter(Color.parseColor(AMBER));
            flame.setContentDescription("Streak");
            LinearLayout.LayoutParams flameParams = new LinearLayout.LayoutParams(dp(28), dp(28));
            flameParams.setMarginStart(dp(5));
            streakRow.addView(flame, flameParams);
            TextView label = text("day" + (streak.current == 1 ? "" : "s")
                    + " in a row · " + habit.name, 14, true);
            streakRow.addView(label);
            body.addView(streakRow);
            addCardHintTo(body, streak.current > 0
                    ? "Your best is " + streak.longest + " days. Keep it gentle and steady."
                    : "A fresh start counts. Check this habit off whenever you're ready.");
        }
        if (coreHabits.size() > 3) {
            MaterialButton moreStreaks = outlinedButton();
            styleLightOutlinedButton(moreStreaks);
            moreStreaks.setText(showAllTodayStreaks
                    ? "Show fewer streaks" : "See all " + coreHabits.size() + " core streaks");
            moreStreaks.setOnClickListener(view -> {
                showAllTodayStreaks = !showAllTodayStreaks;
                render();
            });
            content.addView(moreStreaks, cardParams());
        }
    }

    private void renderComingUp(LocalDate today) {
        sectionTitle("Coming up");
        addHint("Make a little room for what matters over the next few days.");
        LinearLayout dates = row();
        for (int i = 1; i <= 3; i++) {
            LocalDate day = today.plusDays(i);
            int count = 0;
            for (TrackerStore.PlanItem plan : store.getPlans()) {
                if (day.toString().equals(plan.date)) count++;
            }
            String label = day.getDayOfWeek().getDisplayName(
                    java.time.format.TextStyle.SHORT, Locale.getDefault())
                    + "\n" + day.getDayOfMonth()
                    + (count == 0 ? "\nPlan" : "\n" + count + " planned");
            MaterialButton dayButton = outlinedButton();
            dayButton.setText(label);
            dayButton.setTextSize(13);
            styleLightOutlinedButton(dayButton);
            dayButton.setMinHeight(dp(84));
            dayButton.setInsetTop(0);
            dayButton.setInsetBottom(0);
            dayButton.setPadding(dp(4), dp(3), dp(4), dp(3));
            dayButton.setOnClickListener(view -> {
                habitDate = day;
                selectTab("Habits");
            });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    0, dp(84), 1);
            params.setMargins(dp(3), 0, dp(3), 0);
            dates.addView(dayButton, params);
        }
        content.addView(dates, cardParams());
    }

    private void renderPlanItems(LinearLayout parent, LocalDate day) {
        List<TrackerStore.PlanItem> plans = new ArrayList<>();
        for (TrackerStore.PlanItem plan : store.getPlans()) {
            if (day.toString().equals(plan.date)) plans.add(plan);
        }
        Collections.sort(plans, (first, second) -> {
            if (first.time.isEmpty()) return second.time.isEmpty() ? 0 : 1;
            if (second.time.isEmpty()) return -1;
            return first.time.compareTo(second.time);
        });
        if (plans.isEmpty()) {
            addHintTo(parent, "No plans yet. Add one small thing you'd like to make time for.");
            return;
        }
        for (TrackerStore.PlanItem plan : plans) {
            LinearLayout body = addCardBody(parent, Color.parseColor(
                    plan.done ? MUTED : GREEN));
            LinearLayout planRow = row();
            CheckBox done = new CheckBox(this);
            styleCheckBox(done);
            done.setChecked(plan.done);
            LocalDate planDay = safeDate(plan.date);
            done.setEnabled(planDay == null || !planDay.isAfter(LocalDate.now()));
            done.setContentDescription("Mark " + plan.text + " complete");
            done.setOnCheckedChangeListener((button, checked) -> runStoreAction(() -> {
                store.setPlanDone(plan.id, checked);
                if (checked) PlanReminderScheduler.cancelPlan(this, plan.id);
                else {
                    for (TrackerStore.PlanItem item : store.getPlans()) {
                        if (plan.id.equals(item.id)) {
                            PlanReminderScheduler.schedulePlan(this, item);
                            break;
                        }
                    }
                }
                render();
            }));
            planRow.addView(done);
            LinearLayout details = vertical();
            TextView title = text(plan.text, 14, true);
            if (plan.done) title.setPaintFlags(
                    title.getPaintFlags() | android.graphics.Paint.STRIKE_THRU_TEXT_FLAG);
            details.addView(title);
            if (!plan.time.isEmpty()) {
                DateTimeFormatter timeFormat = DateTimeFormatter.ofPattern(
                        "h:mm a", Locale.getDefault());
                String range = LocalTime.parse(plan.time).format(timeFormat);
                if (plan.endTime != null && !plan.endTime.isEmpty()) {
                    range += " – " + LocalTime.parse(plan.endTime).format(timeFormat);
                }
                addCardHintTo(details, range);
            }
            planRow.addView(details, new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1));
            MaterialButton more = miniButton("More");
            more.setOnClickListener(view -> {
                PopupMenu menu = new PopupMenu(this, more);
                menu.getMenu().add("Edit");
                menu.getMenu().add("Remove");
                menu.setOnMenuItemClickListener(item -> {
                    if ("Edit".contentEquals(item.getTitle())) openPlanDialog(plan, day);
                    else new AlertDialog.Builder(this).setTitle("Remove this plan?")
                            .setMessage(plan.text).setNegativeButton("Keep", null)
                            .setPositiveButton("Remove", (dialog, which) ->
                                    runStoreAction(() -> {
                                        store.deletePlan(plan.id);
                                        PlanReminderScheduler.cancelPlan(this, plan.id);
                                        render();
                                    })).show();
                    return true;
                });
                menu.show();
            });
            planRow.addView(more);
            body.addView(planRow);
        }
    }

    private void openPlanDialog(TrackerStore.PlanItem existing, LocalDate day) {
        LocalDate planDay = existing == null ? day : safeDate(existing.date);
        if (planDay == null || planDay.isBefore(LocalDate.now())) {
            Toast.makeText(this, "Plans can only be added or changed for today or a future day.",
                    Toast.LENGTH_LONG).show();
            return;
        }
        LinearLayout form = vertical();
        form.addView(text("Planned for " + planDay.format(DateTimeFormatter.ofPattern(
                "EEEE, MMMM d", Locale.getDefault())), 13, true));
        EditText title = input("What would you like to do?", InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        EditText time = input("Start time (optional)", InputType.TYPE_CLASS_DATETIME);
        makeTimePicker(time, "Plan start time");
        EditText endTime = input("End time", InputType.TYPE_CLASS_DATETIME);
        makeTimePicker(endTime, "Plan end time");
        TextView reminderHint = text("Set both times to reserve a time block and get a reminder at its start.",
                11, false);
        reminderHint.setTextColor(Color.parseColor(MUTED));
        if (existing != null) {
            title.setText(existing.text);
            time.setText(existing.time);
            endTime.setText(existing.endTime);
        }
        form.addView(title);
        addFieldLabel(form, "START");
        form.addView(time);
        addFieldLabel(form, "END");
        form.addView(endTime);
        form.addView(reminderHint);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(existing == null ? "Add to your plan" : "Edit plan")
                .setView(form)
                .setNegativeButton("Cancel", null)
                .setPositiveButton(existing == null ? "Add plan" : "Save", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(view -> {
                    String text = title.getText().toString().trim();
                    if (text.isEmpty()) {
                        title.setError("Add a short plan");
                        return;
                    }
                    String timeValue = time.getText().toString().trim();
                    if (!timeValue.isEmpty()) {
                        try {
                            timeValue = LocalTime.parse(timeValue).toString();
                        } catch (RuntimeException exception) {
                            time.setError("Use 24-hour time, such as 14:30");
                            return;
                        }
                    }
                    String endTimeValue = endTime.getText().toString().trim();
                    if (!endTimeValue.isEmpty()) {
                        try {
                            endTimeValue = LocalTime.parse(endTimeValue).toString();
                        } catch (RuntimeException exception) {
                            endTime.setError("Use 24-hour time, such as 15:30");
                            return;
                        }
                    }
                    if (timeValue.isEmpty() != endTimeValue.isEmpty()) {
                        endTime.setError("Enter both times, or leave both blank");
                        return;
                    }
                    if (!timeValue.isEmpty()
                            && !LocalTime.parse(endTimeValue).isAfter(LocalTime.parse(timeValue))) {
                        endTime.setError("End time must be later than start time");
                        return;
                    }
                    if (!timeValue.isEmpty() && planDay.equals(LocalDate.now())
                            && (existing == null || !existing.time.equals(timeValue))) {
                        try {
                            if (!LocalTime.parse(timeValue).isAfter(LocalTime.now())) {
                                time.setError("Choose a future time today, or plan for another day");
                                return;
                            }
                        } catch (RuntimeException exception) {
                            time.setError("Use a valid time");
                            return;
                        }
                    }
                    String finalTime = timeValue;
                    String finalEndTime = endTimeValue;
                    runStoreAction(() -> {
                        if (planDay.isBefore(LocalDate.now())) {
                            title.setError("Plans can only be saved for today or a future day");
                            return;
                        }
                        if (existing == null) {
                            store.addPlan(planDay.toString(), finalTime, finalEndTime, text);
                            TrackerStore.PlanItem saved = store.getPlans().get(
                                    store.getPlans().size() - 1);
                            PlanReminderScheduler.schedulePlan(this, saved);
                        } else {
                            PlanReminderScheduler.cancelPlan(this, existing.id);
                            store.updatePlan(existing.id, finalTime, finalEndTime, text);
                            for (TrackerStore.PlanItem plan : store.getPlans()) {
                                if (existing.id.equals(plan.id)) {
                                    PlanReminderScheduler.schedulePlan(this, plan);
                                    break;
                                }
                            }
                        }
                        if (!finalTime.isEmpty() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                                != PackageManager.PERMISSION_GRANTED) {
                            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},
                                    REQUEST_PLAN_NOTIFICATIONS);
                        } else if (!finalTime.isEmpty()) {
                            requestExactAlarmAccessIfNeeded();
                        }
                        render();
                        dialog.dismiss();
                    });
                }));
        dialog.show();
    }

    private String greeting() {
        int hour = LocalTime.now().getHour();
        if (hour < 12) return "Good morning";
        if (hour < 17) return "Good afternoon";
        return "Good evening";
    }

    private void selectTab(String tabName) {
        int position = java.util.Arrays.asList(TABS).indexOf(tabName);
        if (position >= 0 && tabs.getSelectedTabPosition() != position) {
            tabs.selectTab(tabs.getTabAt(position));
        }
    }

    private void renderDaily() {
        pageHeader("Habits", "Keep a simple routine. Plan for today or choose a day ahead.");
        LinearLayout dateControls = row();
        MaterialButton previousDay = outlinedButton();
        previousDay.setText("‹");
        previousDay.setContentDescription("Previous day");
        previousDay.setEnabled(habitDate.isAfter(LocalDate.now()));
        dateControls.addView(previousDay, new LinearLayout.LayoutParams(dp(52), dp(44)));
        LinearLayout.LayoutParams previousDayParams =
                (LinearLayout.LayoutParams) previousDay.getLayoutParams();
        previousDayParams.setMargins(0, 0, dp(4), 0);
        previousDay.setLayoutParams(previousDayParams);
        previousDay.setOnClickListener(view -> {
            if (habitDate.isAfter(LocalDate.now())) {
                habitDate = habitDate.minusDays(1);
                render();
            }
        });
        MaterialButton dateButton = outlinedButton();
        dateButton.setText(habitDate.equals(LocalDate.now()) ? "Today"
                : habitDate.format(DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault())));
        LinearLayout.LayoutParams dateButtonParams = new LinearLayout.LayoutParams(0, dp(44), 1);
        dateButtonParams.setMargins(dp(4), 0, dp(4), 0);
        dateControls.addView(dateButton, dateButtonParams);
        dateButton.setOnClickListener(view -> {
            DatePickerDialog picker = new DatePickerDialog(this,
                (DatePicker selected, int year, int month, int day) -> {
                    habitDate = LocalDate.of(year, month + 1, day);
                    render();
                }, habitDate.getYear(), habitDate.getMonthValue() - 1, habitDate.getDayOfMonth());
            picker.getDatePicker().setMinDate(LocalDate.now().atStartOfDay(ZoneId.systemDefault())
                    .toInstant().toEpochMilli());
            picker.show();
        });
        MaterialButton nextDay = outlinedButton();
        nextDay.setText("›");
        nextDay.setContentDescription("Next day");
        nextDay.setEnabled(true);
        dateControls.addView(nextDay, new LinearLayout.LayoutParams(dp(52), dp(44)));
        LinearLayout.LayoutParams nextDayParams =
                (LinearLayout.LayoutParams) nextDay.getLayoutParams();
        nextDayParams.setMargins(dp(4), 0, 0, 0);
        nextDay.setLayoutParams(nextDayParams);
        nextDay.setOnClickListener(view -> {
            habitDate = habitDate.plusDays(1);
            render();
        });
        LinearLayout dateCard = addCardBody(content, Color.parseColor(GREEN));
        dateCard.addView(dateControls);

        List<TrackerStore.Habit> habits = store.getHabits();
        Map<String, Boolean> values = store.getChecks().get(habitDate.toString());
        int completed = 0;
        if (values != null) {
            for (TrackerStore.Habit habit : habits) {
                if (Boolean.TRUE.equals(values.get(habit.id))) completed++;
            }
        }
        LinearLayout summary = addCardBody(content, Color.parseColor(GREEN));
        summary.addView(text(habitDate.equals(LocalDate.now()) ? "Today's check-in"
                : "Plan for " + habitDate.format(DateTimeFormatter.ofPattern(
                "EEEE, MMM d", Locale.getDefault())), 13, true));
        summary.addView(text(habits.isEmpty() ? "Add a few habits to get started."
                : habitDate.isAfter(LocalDate.now())
                ? habits.size() + " habits to keep in mind"
                : completed + " of " + habits.size() + " habits complete", 18, true));
        if (!habits.isEmpty() && !habitDate.isAfter(LocalDate.now())) {
            LinearProgressIndicator progress = new LinearProgressIndicator(this);
            progress.setMax(100);
            progress.setProgress(completed * 100 / habits.size());
            progress.setIndicatorColor(Color.parseColor(GREEN));
            progress.setTrackColor(getColor(R.color.outline));
            progress.setTrackCornerRadius(dp(4));
            summary.addView(progress, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(8)));
        }

        sectionTitle("Plans for this day");
        renderPlanItems(content, habitDate);
        addSecondaryButton(content, "Add something to this day",
                () -> openPlanDialog(null, habitDate));

        sectionTitle(habitDate.equals(LocalDate.now()) ? "Your habits" : "Your plan");
        if (habits.isEmpty()) {
            addHint("Start small. Choose habits you can realistically repeat and build from there.");
        } else {
            if (habitDate.isAfter(LocalDate.now())) {
                addHint("Pick the habits you want to focus on. Mark them complete when the day arrives.");
            }
            for (TrackerStore.Habit habit : habits) {
                LinearLayout body = addCardBody(content, Color.parseColor(
                        habit.core ? AMBER : GREEN));
                LinearLayout habitRow = row();
                CheckBox check = new CheckBox(this);
                check.setText(habit.name);
                styleCheckBox(check);
                check.setChecked(values != null && Boolean.TRUE.equals(values.get(habit.id)));
                check.setEnabled(!habitDate.isAfter(LocalDate.now()));
                check.setOnCheckedChangeListener((button, checked) -> runStoreAction(() -> {
                    if (!habitDate.isAfter(LocalDate.now())) {
                        store.setCheck(habit.id, habitDate.toString(), checked);
                        render();
                    }
                }));
                habitRow.addView(check, new LinearLayout.LayoutParams(0,
                        LinearLayout.LayoutParams.WRAP_CONTENT, 1));
                if (habit.core) {
                    TextView core = text("Core", 10, true);
                    core.setTextColor(Color.parseColor(AMBER));
                    habitRow.addView(core);
                }
                MaterialButton edit = miniButton("Edit");
                edit.setOnClickListener(view -> editHabit(habit));
                habitRow.addView(edit);
                body.addView(habitRow);
            }
        }
        addPrimaryButton(content, habits.isEmpty() ? "Add your first habit" : "Add habit",
                () -> editHabit(null));

        MaterialButton weekToggle = outlinedButton();
        styleLightOutlinedButton(weekToggle);
        weekToggle.setText(showHabitWeek ? "Hide weekly overview" : "See weekly overview");
        weekToggle.setOnClickListener(view -> {
            showHabitWeek = !showHabitWeek;
            render();
        });
        content.addView(weekToggle, cardParams());
        if (showHabitWeek) {
            sectionTitle("Weekly overview");
            renderHabitWeek(habits);
        }
    }

    private void renderHabitWeek(List<TrackerStore.Habit> habits) {
        LocalDate monday = habitDate.with(DayOfWeek.MONDAY);
        LinearLayout controls = row();
        MaterialButton previousWeek = outlinedButton();
        previousWeek.setText("‹");
        previousWeek.setContentDescription("Previous week");
        LocalDate currentMonday = LocalDate.now().with(DayOfWeek.MONDAY);
        previousWeek.setEnabled(monday.isAfter(currentMonday));
        controls.addView(previousWeek, new LinearLayout.LayoutParams(dp(48), dp(42)));
        previousWeek.setOnClickListener(view -> {
            habitDate = habitDate.minusWeeks(1);
            render();
        });
        TextView weekLabel = text(monday.format(DateTimeFormatter.ofPattern(
                "MMM d", Locale.getDefault())) + " – "
                + monday.plusDays(6).format(DateTimeFormatter.ofPattern(
                "MMM d", Locale.getDefault())), 13, true);
        weekLabel.setGravity(Gravity.CENTER);
        controls.addView(weekLabel, new LinearLayout.LayoutParams(0,
                dp(42), 1));
        MaterialButton nextWeek = outlinedButton();
        nextWeek.setText("›");
        nextWeek.setContentDescription("Next week");
        nextWeek.setEnabled(true);
        controls.addView(nextWeek, new LinearLayout.LayoutParams(dp(48), dp(42)));
        nextWeek.setOnClickListener(view -> {
            habitDate = habitDate.plusWeeks(1);
            if (habitDate.isAfter(LocalDate.now())) habitDate = LocalDate.now();
            render();
        });
        LinearLayout controlsCard = addCardBody(content, Color.parseColor(GREEN));
        controlsCard.addView(controls);

        if (habits.isEmpty()) {
            addHint("Your weekly check-ins will appear here after you add habits.");
            return;
        }
        addHint("Only today can be checked off here. Upcoming days are for planning.");
        String[] dayLabels = {"Mo", "Tu", "We", "Th", "Fr", "Sa", "Su"};
        for (TrackerStore.Habit habit : habits) {
            LinearLayout body = addCardBody(content, Color.parseColor(
                    habit.core ? AMBER : GREEN));
            LinearLayout heading = row();
            TextView name = text(habit.name, 14, true);
            heading.addView(name, new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1));
            int doneCount = 0;
            int daysCounted = 0;
            for (int i = 0; i < 7; i++) {
                LocalDate day = monday.plusDays(i);
                if (!day.isAfter(LocalDate.now())) daysCounted++;
                if (!day.isAfter(LocalDate.now())
                        && TrackerMetrics.isDone(store.getChecks(), habit.id, day)) {
                    doneCount++;
                }
            }
            heading.addView(text(daysCounted == 0 ? "Coming up"
                    : doneCount + "/" + daysCounted + " days", 12, true));
            body.addView(heading);
            LinearLayout days = row();
            for (int i = 0; i < 7; i++) {
                LocalDate day = monday.plusDays(i);
                boolean done = TrackerMetrics.isDone(store.getChecks(), habit.id, day);
                TextView chip = text(dayLabels[i] + "\n" + (done ? "✓" : "·"), 11, true);
                chip.setGravity(Gravity.CENTER);
                chip.setTextColor(Color.parseColor(done ? WHITE : MUTED));
                android.graphics.drawable.GradientDrawable background =
                        new android.graphics.drawable.GradientDrawable();
                background.setColor(done ? Color.parseColor(GREEN) : getColor(R.color.surface_variant));
                background.setCornerRadius(dp(8));
                if (day.equals(habitDate)) {
                    background.setStroke(dp(2), Color.parseColor(AMBER));
                }
                chip.setBackground(background);
                boolean today = day.equals(LocalDate.now());
                chip.setContentDescription(habit.name + ", " + day + ", "
                        + (today ? (done ? "complete, tap to undo" : "tap to check off")
                        : day.isAfter(LocalDate.now()) ? "upcoming, not a check-in"
                        : (done ? "complete" : "not complete")));
                chip.setEnabled(today);
                if (today) {
                    chip.setOnClickListener(view -> runStoreAction(() -> {
                        store.setCheck(habit.id, day.toString(), !done);
                        render();
                    }));
                }
                LinearLayout.LayoutParams chipParams = new LinearLayout.LayoutParams(
                        0, dp(46), 1);
                chipParams.setMargins(dp(2), dp(4), dp(2), dp(2));
                days.addView(chip, chipParams);
            }
            body.addView(days);
        }
    }

    private void editHabit(TrackerStore.Habit habit) {
        LinearLayout form = vertical();
        EditText name = input("Habit name", InputType.TYPE_CLASS_TEXT);
        if (habit != null) name.setText(habit.name);
        CheckBox core = new CheckBox(this);
        core.setText("Core habit (track streak)");
        styleCheckBox(core);
        if (habit != null) core.setChecked(habit.core);
        form.addView(name);
        form.addView(core);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(habit == null ? "Add habit" : "Edit habit")
                .setView(form)
                .setNegativeButton("Cancel", null)
                .setPositiveButton(habit == null ? "Add" : "Save", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(view -> {
                    String value = name.getText().toString().trim();
                    if (value.isEmpty()) {
                        name.setError("Enter a habit name");
                        return;
                    }
                    runStoreAction(() -> {
                        if (habit == null) store.addHabit(value, core.isChecked());
                        else store.updateHabit(habit.id, value, core.isChecked());
                        render();
                        dialog.dismiss();
                    });
                }));
        if (habit != null) {
            dialog.setButton(AlertDialog.BUTTON_NEUTRAL, "Remove", (d, which) -> {});
        }
        dialog.show();
        if (habit != null) {
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(view ->
                    new AlertDialog.Builder(this)
                            .setTitle("Remove habit?")
                            .setMessage("This also removes its saved daily check-ins.")
                            .setNegativeButton("Cancel", null)
                            .setPositiveButton("Remove", (d, which) -> runStoreAction(() -> {
                                store.deleteHabit(habit.id);
                                dialog.dismiss();
                                render();
                            })).show());
        }
    }

    private void renderTargets() {
        sectionTitle("Goals & targets");
        addHint("Track a number you care about, like pages read, minutes studied, or practice problems.");
        sectionTitle("Log a measurement");
        LinearLayout entryForm = addCardBody(content, Color.parseColor(GREEN));
        EditText entryDate = input("Date (YYYY-MM-DD)", InputType.TYPE_CLASS_DATETIME);
        entryDate.setText(LocalDate.now().toString());
        makeDatePicker(entryDate, "Target date", true);
        addFieldLabel(entryForm, "DATE");
        EditText habit = input("Habit name", InputType.TYPE_CLASS_TEXT);
        EditText target = input("Target", InputType.TYPE_CLASS_NUMBER
                | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        EditText actual = input("Actual", InputType.TYPE_CLASS_NUMBER
                | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        EditText notes = input("Notes (optional)", InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        entryForm.addView(entryDate);
        addFieldLabel(entryForm, "MEASUREMENT");
        entryForm.addView(habit);
        addFieldLabel(entryForm, "TARGET");
        entryForm.addView(target);
        addFieldLabel(entryForm, "ACTUAL");
        entryForm.addView(actual);
        addFieldLabel(entryForm, "NOTES");
        entryForm.addView(notes);
        addPrimaryButton(entryForm, "Save target entry", () -> {
            LocalDate day = parseDate(entryDate, "Date");
            Double goal = parseNumber(target, "Target", true);
            Double value = parseNumber(actual, "Actual", false);
            if (day == null || goal == null || value == null) return;
            if (day.isAfter(LocalDate.now())) {
                entryDate.setError("Choose today or an earlier date for an actual result");
                return;
            }
            if (habit.getText().toString().trim().isEmpty()) {
                habit.setError("Enter a habit name");
                return;
            }
            runStoreAction(() -> {
                store.addTarget(day.toString(), habit.getText().toString(), goal, value,
                        notes.getText().toString());
                render();
            });
        });

        sectionTitle("History & filters");
        LinearLayout filterForm = addCardBody(content, getColor(R.color.outline));
        EditText filterHabit = input("Habit contains", InputType.TYPE_CLASS_TEXT);
        EditText from = input("From date (optional)", InputType.TYPE_CLASS_DATETIME);
        EditText to = input("To date (optional)", InputType.TYPE_CLASS_DATETIME);
        makeDatePicker(from, "From date", true);
        makeDatePicker(to, "To date", true);
        filterForm.addView(filterHabit);
        filterForm.addView(from);
        filterForm.addView(to);
        addSecondaryButton(filterForm, "Apply filters", () -> {
            LocalDate start = optionalDate(from, "From date");
            LocalDate end = optionalDate(to, "To date");
            if (from.getText().length() > 0 && start == null
                    || to.getText().length() > 0 && end == null) return;
            if (start != null && end != null && start.isAfter(end)) {
                to.setError("Must not be before From date");
                return;
            }
            displayTargetEntries(filterHabit.getText().toString(), start, end);
        });
        displayTargetEntries("", null, null);
    }

    private void displayTargetEntries(String filter, LocalDate from, LocalDate to) {
        removeDynamicTargetList();
        LinearLayout list = new LinearLayout(this);
        list.setTag("target-list");
        list.setOrientation(LinearLayout.VERTICAL);
        content.addView(list);
        List<TrackerStore.TargetEntry> entries = new ArrayList<>(store.getTargets());
        Collections.sort(entries, (a, b) -> b.date.compareTo(a.date));
        int count = 0;
        for (TrackerStore.TargetEntry entry : entries) {
            LocalDate day;
            try { day = LocalDate.parse(entry.date); }
            catch (RuntimeException ignored) { continue; }
            if (!entry.habit.toLowerCase(Locale.ROOT).contains(filter.toLowerCase(Locale.ROOT))
                    || from != null && day.isBefore(from)
                    || to != null && day.isAfter(to)) continue;
            String line = entry.date + " · " + entry.habit + "\n"
                    + entry.actual + "/" + entry.target + " · "
                    + ("true".equals(entry.hit) ? "Hit target" : "Missed target")
                    + (entry.notes.isEmpty() ? "" : "\n" + entry.notes);
            addCardTo(list, line, "true".equals(entry.hit) ? GREEN : AMBER);
            count++;
        }
        if (count == 0) addHintTo(list, "No matching entries.");
    }

    private void removeDynamicTargetList() {
        for (int i = content.getChildCount() - 1; i >= 0; i--) {
            View child = content.getChildAt(i);
            if ("target-list".equals(child.getTag())) content.removeViewAt(i);
        }
    }

    private void addFocusHero() {
        LinearLayout hero = vertical();
        hero.setBackgroundResource(R.drawable.bg_focus_hero);
        hero.setElevation(dp(4));
        hero.setClipToOutline(true);

        LinearLayout heroContent = vertical();
        heroContent.setGravity(Gravity.CENTER_HORIZONTAL);
        heroContent.setPadding(dp(18), dp(14), dp(18), dp(14));

        TextView eyebrow = text("DAY BY DAY · FOCUS", 10, true);
        eyebrow.setTextColor(Color.parseColor("#C4E4C2"));
        eyebrow.setLetterSpacing(0.12f);
        heroContent.addView(eyebrow);

        TextView headline = text("Make room for what matters.", 23, true);
        headline.setTextColor(Color.parseColor("#F4F7E8"));
        headline.setGravity(Gravity.CENTER);
        headline.setPadding(dp(4), dp(2), dp(4), 0);
        heroContent.addView(headline);

        TextView subtitle = text("A little focus can grow into real progress.", 12, false);
        subtitle.setTextColor(Color.parseColor("#D1E2D4"));
        subtitle.setGravity(Gravity.CENTER);
        heroContent.addView(subtitle);

        focusSprout = new FocusSproutView();
        focusSprout.setDarkBackground(true);
        LinearLayout.LayoutParams plantParams = new LinearLayout.LayoutParams(dp(112), dp(112));
        plantParams.topMargin = dp(6);
        heroContent.addView(focusSprout, plantParams);

        hero.addView(heroContent);
        LinearLayout.LayoutParams heroParams = cardParams();
        heroParams.setMargins(0, dp(4), 0, dp(14));
        this.content.addView(hero, heroParams);
    }

    private void renderSessions() {
        addFocusHero();
        LinearLayout timerCard = addCardBody(content, getColor(R.color.timer_accent));
        timerMinutes = input("Minutes", InputType.TYPE_CLASS_NUMBER);
        timerMinutes.setText(Integer.toString(
                timerDurationSeconds > 0 ? Math.max(1, timerDurationSeconds / 60) : 25));
        TextView durationIntro = text("Choose your focus time", 16, true);
        durationIntro.setPadding(0, 0, 0, dp(4));
        timerCard.addView(durationIntro);
        addFieldLabel(timerCard, "DURATION IN MINUTES");
        timerCard.addView(timerMinutes);
        LinearLayout presets = row();
        addSecondaryButton(presets, "25 min", () -> timerMinutes.setText("25"));
        addSecondaryButton(presets, "50 min", () -> timerMinutes.setText("50"));
        timerCard.addView(presets);

        timerTopic = topicDropdown("Choose a study topic", timerTopicIdValue, timerTopicValue);
        timerTopic.setEnabled(!timerRunning);
        addFieldLabel(timerCard, "STUDY TOPIC");
        timerInterrupted = new CheckBox(this);
        timerInterrupted.setText("Interrupted");
        styleCheckBox(timerInterrupted);
        timerInterrupted.setChecked(timerInterruptedValue);
        timerCard.addView(timerTopic);
        timerCard.addView(timerInterrupted);

        FrameLayout timerFace = new FrameLayout(this);
        timerProgress = new CircularProgressIndicator(this);
        timerProgress.setIndicatorSize(dp(180));
        timerProgress.setIndicatorInset(dp(4));
        timerProgress.setTrackThickness(dp(8));
        timerProgress.setMax(Math.max(1, timerDurationSeconds));
        timerProgress.setIndicatorColor(getColor(R.color.timer_accent));
        timerProgress.setTrackColor(getColor(R.color.timer_track));
        android.graphics.drawable.GradientDrawable timerFaceBackground =
                new android.graphics.drawable.GradientDrawable();
        timerFaceBackground.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        timerFaceBackground.setColor(getColor(R.color.timer_face));
        View timerFaceSurface = new View(this);
        timerFaceSurface.setBackground(timerFaceBackground);
        timerFace.addView(timerFaceSurface, new FrameLayout.LayoutParams(
                dp(158), dp(158), Gravity.CENTER));
        FrameLayout.LayoutParams progressParams = new FrameLayout.LayoutParams(
                dp(190), dp(190), Gravity.CENTER);
        timerFace.addView(timerProgress, progressParams);
        LinearLayout timerLabels = vertical();
        timerLabels.setGravity(Gravity.CENTER);
        timerCountdown = text(timerRunning
                ? formatClock((int) Math.max(0, (timerEndMillis - System.currentTimeMillis() + 999) / 1000))
                : formatClock(timerDurationSeconds > 0 ? timerDurationSeconds : 25 * 60), 38, true);
        timerCountdown.setTextColor(getColor(R.color.timer_accent));
        timerCountdown.setTypeface(android.graphics.Typeface.create(
                "sans-serif-medium", android.graphics.Typeface.NORMAL));
        timerCountdown.setContentDescription("Time remaining " + timerCountdown.getText());
        timerCountdown.setTextSize(40);
        timerStatus = text(timerRunning ? "Focus session in progress"
                : "Ready when you are", 12, false);
        timerLabels.addView(timerCountdown);
        timerLabels.addView(timerStatus);
        FrameLayout.LayoutParams labelsParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        timerFace.addView(timerLabels, labelsParams);
        timerMinutes.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(
                    CharSequence value, int start, int count, int after) {}
            @Override public void onTextChanged(
                    CharSequence value, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable value) {
                updateTimerPreview();
            }
        });
        LinearLayout.LayoutParams faceParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(205));
        timerCard.addView(timerFace, faceParams);
        updateTimerPreview();

        if (timerRunning) {
            long remaining = Math.max(0, (timerEndMillis - System.currentTimeMillis()) / 1000);
            timerProgress.setMax(Math.max(1, timerDurationSeconds));
            timerProgress.setProgress(Math.max(0, timerDurationSeconds - (int) remaining));
            focusSprout.setProgress(1f - (float) remaining
                    / Math.max(1, timerDurationSeconds));
        } else {
            boolean ringing = getSharedPreferences(TimerAlarmScheduler.PREFS, MODE_PRIVATE)
                    .getBoolean(TimerAlarmScheduler.KEY_ALARM_RINGING, false);
            if (ringing) {
                timerStatus.setText("Time's up · stop or snooze from the alarm notification");
                timerCountdown.setText("Done");
            } else {
                timerProgress.setProgress(0);
            }
            focusSprout.setProgress(0f);
        }

        timerActionButton = primaryButton();
        boolean alarmRinging = getSharedPreferences(TimerAlarmScheduler.PREFS, MODE_PRIVATE)
                .getBoolean(TimerAlarmScheduler.KEY_ALARM_RINGING, false);
        timerActionButton.setText(timerRunning ? "End session & save"
                : alarmRinging ? "Dismiss alarm" : "Start focus timer");
        timerActionButton.setOnClickListener(view -> {
            if (timerRunning) stopTimer();
            else if (alarmRinging) dismissAlarm();
            else startTimer();
        });
        timerCard.addView(timerActionButton);
        if (timerRunning) {
            handler.removeCallbacks(timerTick);
            handler.post(timerTick);
        }

        sectionTitle("Log a session");
        LinearLayout manualForm = addCardBody(content, getColor(R.color.outline));
        EditText day = input("Date (YYYY-MM-DD)", InputType.TYPE_CLASS_DATETIME);
        day.setText(LocalDate.now().toString());
        makeDatePicker(day, "Session date", true);
        addFieldLabel(manualForm, "Date");
        manualForm.addView(day);
        EditText start = input("Start time (HH:MM)", InputType.TYPE_CLASS_DATETIME);
        LocalTime now = LocalTime.now().withSecond(0).withNano(0);
        if (now.equals(LocalTime.MIDNIGHT)) now = now.plusMinutes(1);
        LocalTime startDefault = now.minusHours(1);
        if (startDefault.isAfter(now)) startDefault = LocalTime.MIDNIGHT;
        start.setText(startDefault.format(DateTimeFormatter.ofPattern("HH:mm")));
        makeTimePicker(start, "Start time");
        addFieldLabel(manualForm, "Start time");
        manualForm.addView(start);
        EditText end = input("End time (HH:MM)", InputType.TYPE_CLASS_DATETIME);
        end.setText(now.format(DateTimeFormatter.ofPattern("HH:mm")));
        makeTimePicker(end, "End time");
        addFieldLabel(manualForm, "End time");
        manualForm.addView(end);
        MaterialAutoCompleteTextView topic = topicDropdown("Choose a study topic", "", "");
        addFieldLabel(manualForm, "STUDY TOPIC");
        CheckBox interrupted = new CheckBox(this);
        interrupted.setText("Interrupted");
        styleCheckBox(interrupted);
        manualForm.addView(topic);
        manualForm.addView(interrupted);
        addPrimaryButton(manualForm, "Save session", () -> {
            LocalDate sessionDay = parseDate(day, "Date");
            LocalTime begin = parseTime(start, "Start time");
            LocalTime finish = parseTime(end, "End time");
            if (sessionDay == null || begin == null || finish == null) return;
            if (sessionDay.isAfter(LocalDate.now())) {
                day.setError("Sessions can be logged for today or an earlier date");
                return;
            }
            String topicId = selectedTopicId(topic);
            if (topicId.isEmpty()) {
                topic.setError("Choose a study topic");
                topic.requestFocus();
                topic.showDropDown();
                return;
            }
            int seconds = (int) (finish.toSecondOfDay() - begin.toSecondOfDay());
            if (seconds <= 0) {
                end.setError("End must be later than start");
                return;
            }
            runStoreAction(() -> {
                store.addSession(sessionDay.toString(), begin.toString(), finish.toString(),
                        seconds, topicId, topic.getText().toString(),
                        interrupted.isChecked());
                render();
            });
        });

        renderReadingLogForm();
        renderSessionHistory();

        renderTopicStudySummary();
        renderReadingHistory();

        MaterialButton targetsToggle = outlinedButton();
        styleLightOutlinedButton(targetsToggle);
        targetsToggle.setText(showTargets ? "Hide goals & targets" : "Track a number goal");
        targetsToggle.setOnClickListener(view -> {
            showTargets = !showTargets;
            render();
        });
        content.addView(targetsToggle, cardParams());
        if (showTargets) renderTargets();
    }

    private void renderSessionHistory() {
        sectionTitle("Session history");
        addHint("Find past focus sessions by topic or date. Your timer and reading totals stay together in the weekly summary.");
        LocalDate today = LocalDate.now();
        LocalDate monday = today.with(DayOfWeek.MONDAY);
        int todaySeconds = 0, weekSeconds = 0;
        for (TrackerStore.Session session : store.getSessions()) {
            LocalDate sessionDay = safeDate(session.date);
            if (sessionDay == null) continue;
            if (sessionDay.equals(today)) todaySeconds += session.durationSeconds;
            if (!sessionDay.isBefore(monday) && !sessionDay.isAfter(monday.plusDays(6))) {
                weekSeconds += session.durationSeconds;
            }
        }
        for (TrackerStore.ReadingLog reading : store.getReadingLogs()) {
            LocalDate readingDay = safeDate(reading.date);
            int seconds = reading.durationMinutes * 60;
            if (today.equals(readingDay)) todaySeconds += seconds;
            if (readingDay != null && !readingDay.isBefore(monday)
                    && !readingDay.isAfter(monday.plusDays(6))) weekSeconds += seconds;
        }
        LinearLayout totals = row();
        addMetricTile(totals, "Today", todaySeconds == 0 ? "—"
                : formatDuration(todaySeconds), GREEN);
        addMetricTile(totals, "This week", weekSeconds == 0 ? "—"
                : formatDuration(weekSeconds), GREEN);
        addCardBody(content, getColor(R.color.timer_accent)).addView(totals);

        LinearLayout filters = addCardBody(content, getColor(R.color.outline));
        EditText query = input("Search study topics", InputType.TYPE_CLASS_TEXT);
        EditText from = input("Start date", InputType.TYPE_CLASS_DATETIME);
        EditText to = input("End date", InputType.TYPE_CLASS_DATETIME);
        makeDatePicker(from, "Session start date", true);
        makeDatePicker(to, "Session end date", true);
        addFieldLabel(filters, "TOPIC");
        filters.addView(query);
        LinearLayout dateRange = row();
        LinearLayout fromColumn = vertical();
        LinearLayout toColumn = vertical();
        addFieldLabel(fromColumn, "FROM");
        fromColumn.addView(from);
        addFieldLabel(toColumn, "TO");
        toColumn.addView(to);
        dateRange.addView(fromColumn, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        LinearLayout.LayoutParams toParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        toParams.setMargins(dp(8), 0, 0, 0);
        dateRange.addView(toColumn, toParams);
        filters.addView(dateRange);

        CheckBox interruptedOnly = new CheckBox(this);
        interruptedOnly.setText("Interrupted sessions only");
        styleCheckBox(interruptedOnly);
        filters.addView(interruptedOnly);
        LinearLayout quickFilters = row();
        MaterialButton lastWeek = miniButton("Last 7 days");
        lastWeek.setOnClickListener(view -> {
            from.setText(today.minusDays(6).toString());
            to.setText(today.toString());
        });
        MaterialButton allDates = miniButton("All dates");
        allDates.setOnClickListener(view -> {
            from.setText("");
            to.setText("");
        });
        quickFilters.addView(lastWeek, new LinearLayout.LayoutParams(
                0, dp(40), 1));
        LinearLayout.LayoutParams allParams = new LinearLayout.LayoutParams(
                0, dp(40), 1);
        allParams.setMargins(dp(8), 0, 0, 0);
        quickFilters.addView(allDates, allParams);
        filters.addView(quickFilters);

        LinearLayout filterActions = row();
        MaterialButton apply = miniButton("Apply filters");
        apply.setOnClickListener(view -> {
            LocalDate start = optionalDate(from, "Start date");
            LocalDate end = optionalDate(to, "End date");
            if (from.getText().length() > 0 && start == null
                    || to.getText().length() > 0 && end == null) return;
            if (start != null && end != null && start.isAfter(end)) {
                to.setError("End date must be on or after start date");
                return;
            }
            sessionHistoryLimit = 20;
            displaySessionHistory(query.getText().toString(), start, end,
                    interruptedOnly.isChecked());
        });
        MaterialButton clear = miniButton("Clear");
        clear.setOnClickListener(view -> {
            query.setText("");
            from.setText("");
            to.setText("");
            interruptedOnly.setChecked(false);
            sessionHistoryLimit = 20;
            displaySessionHistory("", null, null, false);
        });
        filterActions.addView(apply, new LinearLayout.LayoutParams(0, dp(40), 1));
        LinearLayout.LayoutParams clearParams = new LinearLayout.LayoutParams(
                0, dp(40), 1);
        clearParams.setMargins(dp(8), 0, 0, 0);
        filterActions.addView(clear, clearParams);
        filters.addView(filterActions);

        LinearLayout list = vertical();
        list.setTag("session-history-list");
        content.addView(list);
        displaySessionHistory("", null, null, false);
    }

    private void displaySessionHistory(
            String query, LocalDate from, LocalDate to, boolean interruptedOnly) {
        LinearLayout list = null;
        for (int i = content.getChildCount() - 1; i >= 0; i--) {
            View child = content.getChildAt(i);
            if ("session-history-list".equals(child.getTag())) {
                list = (LinearLayout) child;
                break;
            }
        }
        if (list == null) {
            list = vertical();
            list.setTag("session-history-list");
            content.addView(list);
        } else {
            list.removeAllViews();
        }

        List<TrackerStore.Session> entries = new ArrayList<>(store.getSessions());
        Collections.sort(entries, (a, b) ->
                (b.date + b.start).compareTo(a.date + a.start));
        String normalizedQuery = query.trim().toLowerCase(Locale.ROOT);
        List<TrackerStore.Session> matches = new ArrayList<>();
        int totalSeconds = 0;
        for (TrackerStore.Session session : entries) {
            LocalDate sessionDay = safeDate(session.date);
            if (sessionDay == null || from != null && sessionDay.isBefore(from)
                    || to != null && sessionDay.isAfter(to)
                    || interruptedOnly && !"true".equals(session.interrupted)) continue;
            String topicName = topicNameForSession(session);
            if (!normalizedQuery.isEmpty()
                    && !topicName.toLowerCase(Locale.ROOT).contains(normalizedQuery)) continue;
            matches.add(session);
            totalSeconds += session.durationSeconds;
        }
        if (matches.isEmpty()) {
            addHintTo(list, entries.isEmpty()
                    ? "Completed focus sessions will appear here."
                    : "No sessions match those filters. Try a wider date range.");
            return;
        }
        addCardTo(list, matches.size() + (matches.size() == 1 ? " session" : " sessions")
                + " · " + formatDuration(totalSeconds), GREEN);
        int shown = Math.min(matches.size(), sessionHistoryLimit);
        for (int i = 0; i < shown; i++) {
            TrackerStore.Session session = matches.get(i);
            String topicName = topicNameForSession(session);
            LinearLayout card = addCardBody(list, Color.parseColor(GREEN));
            card.addView(text(topicName.isEmpty() ? "Study session" : topicName, 15, true));
            TextView details = text(session.date + " · " + session.start + "–" + session.end
                    + "\n" + formatDuration(session.durationSeconds) + " · "
                    + ("true".equals(session.interrupted) ? "Interrupted" : "Focused"),
                    12, false);
            details.setTextColor(Color.parseColor(MUTED));
            card.addView(details);
            LinearLayout actions = row();
            MaterialButton edit = miniButton("Edit");
            edit.setOnClickListener(view -> editSession(session));
            MaterialButton delete = miniButton("Delete");
            delete.setTextColor(getColor(R.color.danger));
            delete.setOnClickListener(view -> confirmDeleteSession(session));
            LinearLayout.LayoutParams editParams = new LinearLayout.LayoutParams(0, dp(38), 1);
            editParams.setMargins(0, dp(6), dp(6), 0);
            actions.addView(edit, editParams);
            LinearLayout.LayoutParams deleteParams = new LinearLayout.LayoutParams(
                    0, dp(38), 1);
            deleteParams.setMargins(dp(6), dp(6), 0, 0);
            actions.addView(delete, deleteParams);
            card.addView(actions);
        }
        if (shown < matches.size()) {
            MaterialButton showAll = outlinedButton();
            styleLightOutlinedButton(showAll);
            showAll.setText("Show all " + matches.size() + " sessions");
            showAll.setOnClickListener(view -> {
                sessionHistoryLimit = matches.size();
                displaySessionHistory(query, from, to, interruptedOnly);
            });
            list.addView(showAll, cardParams());
        }
    }

    private void updateTimerPreview() {
        if (timerRunning || timerMinutes == null || timerCountdown == null
                || timerProgress == null) return;
        try {
            int minutes = Integer.parseInt(timerMinutes.getText().toString().trim());
            if (minutes < 1 || minutes > 720) return;
            int seconds = minutes * 60;
            timerCountdown.setText(formatClock(seconds));
            timerCountdown.setContentDescription("Timer set for " + minutes + " minutes");
            timerProgress.setMax(seconds);
            timerProgress.setProgress(0);
            timerStatus.setText("Ready when you are");
        } catch (NumberFormatException ignored) {
            // Partial numeric input is validated when the timer starts.
        }
    }

    private void renderReadingLogForm() {
        sectionTitle("Log reading");
        addHint("Capture reading time separately, so your study totals reflect both focused sessions and self-paced learning.");
        LinearLayout form = addCardBody(content, getColor(R.color.outline));

        EditText date = input("Date (YYYY-MM-DD)", InputType.TYPE_CLASS_DATETIME);
        date.setText(LocalDate.now().toString());
        makeDatePicker(date, "Reading date", true);
        addFieldLabel(form, "DATE");
        form.addView(date);

        MaterialAutoCompleteTextView topic = topicDropdown("Choose a topic", "", "");
        addFieldLabel(form, "TOPIC");
        form.addView(topic);

        EditText minutes = input("Minutes read", InputType.TYPE_CLASS_NUMBER);
        minutes.setText("20");
        addFieldLabel(form, "TIME SPENT · MINUTES");
        form.addView(minutes);
        LinearLayout durationPresets = row();
        addSecondaryButton(durationPresets, "15 min", () -> minutes.setText("15"));
        addSecondaryButton(durationPresets, "30 min", () -> minutes.setText("30"));
        addSecondaryButton(durationPresets, "45 min", () -> minutes.setText("45"));
        form.addView(durationPresets);

        EditText pagesRead = input("Pages read (optional)", InputType.TYPE_CLASS_NUMBER);
        addFieldLabel(form, "PAGES READ · OPTIONAL");
        form.addView(pagesRead);

        EditText notes = input("What did you read? (optional)", InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        notes.setMinLines(2);
        notes.setMaxLines(4);
        notes.setGravity(Gravity.TOP | Gravity.START);
        addFieldLabel(form, "CHAPTER, PAGE, OR TAKEAWAY");
        form.addView(notes);

        addPrimaryButton(form, "Save reading", () -> {
            LocalDate readingDay = parseDate(date, "Date");
            if (readingDay == null) return;
            if (readingDay.isAfter(LocalDate.now())) {
                date.setError("Reading can be logged for today or an earlier date");
                return;
            }
            String topicId = selectedTopicId(topic);
            if (topicId.isEmpty()) {
                topic.setError("Choose a topic");
                topic.requestFocus();
                topic.showDropDown();
                return;
            }
            int duration;
            try {
                duration = Integer.parseInt(minutes.getText().toString().trim());
                if (duration < 1 || duration > 720) throw new NumberFormatException();
            } catch (NumberFormatException exception) {
                minutes.setError("Choose 1 to 720 minutes");
                return;
            }
            Integer pages = parseOptionalCount(pagesRead, "Pages read", 10000);
            if (pages == null) return;
            runStoreAction(() -> {
                store.addReadingLog(readingDay.toString(), topicId, duration, pages,
                        notes.getText().toString());
                Toast.makeText(this, "Reading saved", Toast.LENGTH_SHORT).show();
                render();
            });
        });
    }

    private void renderTopicStudySummary() {
        sectionTitle("Time by topic");
        boolean found = false;
        for (TrackerStore.Topic topic : store.getTopics()) {
            int seconds = 0;
            for (TrackerStore.Session session : store.getSessions()) {
                if (topic.id.equals(session.topicId)
                        || session.topicId.isEmpty() && topic.name.equals(session.topic)) {
                    seconds += session.durationSeconds;
                }
            }
            for (TrackerStore.ReadingLog reading : store.getReadingLogs()) {
                if (topic.id.equals(reading.topicId)) {
                    seconds += reading.durationMinutes * 60;
                }
            }
            if (seconds > 0) {
                addCard(topic.name + "\n" + formatDuration(seconds));
                found = true;
            }
        }
        if (!found) addHint("Your time for each topic will build up here.");
    }

    private void renderReadingHistory() {
        sectionTitle("Reading history");
        addHint("Search by topic or note, or narrow the list to a date range. Edit or remove an entry any time.");
        LinearLayout filters = addCardBody(content, getColor(R.color.outline));
        EditText topicFilter = input("Search topics and notes", InputType.TYPE_CLASS_TEXT);
        EditText from = input("Start date", InputType.TYPE_CLASS_DATETIME);
        EditText to = input("End date", InputType.TYPE_CLASS_DATETIME);
        makeDatePicker(from, "From date", true);
        makeDatePicker(to, "To date", true);
        addFieldLabel(filters, "SEARCH");
        filters.addView(topicFilter);
        LinearLayout range = row();
        LinearLayout rangeFrom = vertical();
        LinearLayout rangeTo = vertical();
        addFieldLabel(rangeFrom, "FROM");
        rangeFrom.addView(from);
        addFieldLabel(rangeTo, "TO");
        rangeTo.addView(to);
        range.addView(rangeFrom, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        LinearLayout.LayoutParams toParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        toParams.setMargins(dp(8), 0, 0, 0);
        range.addView(rangeTo, toParams);
        filters.addView(range);
        LinearLayout filterActions = row();
        MaterialButton apply = miniButton("Apply");
        apply.setOnClickListener(view -> {
            LocalDate start = optionalDate(from, "From date");
            LocalDate end = optionalDate(to, "To date");
            if (from.getText().length() > 0 && start == null
                    || to.getText().length() > 0 && end == null) return;
            if (start != null && end != null && start.isAfter(end)) {
                to.setError("Must not be before From date");
                return;
            }
            displayReadingHistory(topicFilter.getText().toString(), start, end);
        });
        MaterialButton clear = miniButton("Clear");
        clear.setOnClickListener(view -> {
            topicFilter.setText("");
            from.setText("");
            to.setText("");
            displayReadingHistory("", null, null);
        });
        filterActions.addView(apply, new LinearLayout.LayoutParams(
                0, dp(40), 1));
        LinearLayout.LayoutParams clearParams = new LinearLayout.LayoutParams(
                0, dp(40), 1);
        clearParams.setMargins(dp(8), 0, 0, 0);
        filterActions.addView(clear, clearParams);
        filters.addView(filterActions);

        LinearLayout list = vertical();
        list.setTag("reading-history-list");
        content.addView(list);
        displayReadingHistory("", null, null);
    }

    private void displayReadingHistory(String query, LocalDate from, LocalDate to) {
        LinearLayout list = null;
        for (int i = content.getChildCount() - 1; i >= 0; i--) {
            View child = content.getChildAt(i);
            if ("reading-history-list".equals(child.getTag())) {
                list = (LinearLayout) child;
                break;
            }
        }
        if (list == null) {
            list = vertical();
            list.setTag("reading-history-list");
            content.addView(list);
        } else {
            list.removeAllViews();
        }
        List<TrackerStore.ReadingLog> entries = new ArrayList<>(store.getReadingLogs());
        Collections.sort(entries, (a, b) -> {
            int dateOrder = b.date.compareTo(a.date);
            return dateOrder != 0 ? dateOrder
                    : Long.compare(b.loggedAtMillis, a.loggedAtMillis);
        });
        String normalizedQuery = query.trim().toLowerCase(Locale.ROOT);
        List<TrackerStore.ReadingLog> matches = new ArrayList<>();
        int totalMinutes = 0;
        int totalPages = 0;
        for (TrackerStore.ReadingLog entry : entries) {
            LocalDate entryDay = safeDate(entry.date);
            if (entryDay == null || from != null && entryDay.isBefore(from)
                    || to != null && entryDay.isAfter(to)) continue;
            String topic = topicNameForReading(entry);
            String searchable = (topic + " " + entry.notes).toLowerCase(Locale.ROOT);
            if (!normalizedQuery.isEmpty() && !searchable.contains(normalizedQuery)) continue;
            matches.add(entry);
            totalMinutes += entry.durationMinutes;
            totalPages += entry.pagesRead;
        }
        if (store.getReadingLogs().isEmpty()) {
            addHintTo(list, "Your saved reading sessions will appear here.");
            return;
        }
        String summary = matches.size() + (matches.size() == 1 ? " reading log" : " reading logs")
                + " · " + formatDuration(totalMinutes * 60) + " recorded";
        if (totalPages > 0) summary += " · " + totalPages + " pages";
        addCardTo(list, summary, GREEN);
        if (matches.isEmpty()) {
            addHintTo(list, "No reading logs match those filters.");
            return;
        }
        for (TrackerStore.ReadingLog entry : matches) {
            LinearLayout card = addCardBody(list, Color.parseColor(GREEN));
            card.addView(text(topicNameForReading(entry), 15, true));
            TextView details = text(entry.date + " · "
                    + formatDuration(entry.durationMinutes * 60)
                    + (entry.pagesRead > 0 ? " · " + entry.pagesRead + " pages" : ""),
                    12, false);
            details.setTextColor(Color.parseColor(MUTED));
            card.addView(details);
            if (!entry.notes.isEmpty()) {
                TextView notes = text(entry.notes, 13, false);
                notes.setPadding(0, dp(6), 0, 0);
                card.addView(notes);
            }
            LinearLayout actions = row();
            MaterialButton edit = miniButton("Edit");
            edit.setOnClickListener(view -> editReadingLog(entry));
            MaterialButton delete = miniButton("Delete");
            delete.setTextColor(getColor(R.color.danger));
            delete.setOnClickListener(view -> confirmDeleteReadingLog(entry));
            LinearLayout.LayoutParams editParams = new LinearLayout.LayoutParams(
                    0, dp(38), 1);
            editParams.setMargins(0, dp(6), dp(6), 0);
            actions.addView(edit, editParams);
            LinearLayout.LayoutParams deleteParams = new LinearLayout.LayoutParams(
                    0, dp(38), 1);
            deleteParams.setMargins(dp(6), dp(6), 0, 0);
            actions.addView(delete, deleteParams);
            card.addView(actions);
        }
    }

    private String topicNameForReading(TrackerStore.ReadingLog reading) {
        for (TrackerStore.Topic topic : store.getTopics()) {
            if (topic.id.equals(reading.topicId)) return topic.name;
        }
        return reading.topic == null || reading.topic.isEmpty()
                ? "Reading session" : reading.topic;
    }

    private void editSession(TrackerStore.Session session) {
        LinearLayout form = vertical();
        form.setPadding(dp(8), dp(4), dp(8), dp(4));
        EditText date = input("Date", InputType.TYPE_CLASS_DATETIME);
        date.setText(session.date);
        makeDatePicker(date, "Session date", true);
        addFieldLabel(form, "DATE");
        form.addView(date);
        EditText start = input("Start time", InputType.TYPE_CLASS_DATETIME);
        start.setText(editableTime(session.start));
        makeTimePicker(start, "Start time");
        addFieldLabel(form, "START TIME");
        form.addView(start);
        EditText end = input("End time", InputType.TYPE_CLASS_DATETIME);
        end.setText(editableTime(session.end));
        makeTimePicker(end, "End time");
        addFieldLabel(form, "END TIME · NEXT-DAY END TIMES ARE SUPPORTED");
        form.addView(end);
        MaterialAutoCompleteTextView topic = topicDropdown(
                "Choose a study topic", session.topicId, topicNameForSession(session));
        addFieldLabel(form, "TOPIC");
        form.addView(topic);
        CheckBox interrupted = new CheckBox(this);
        interrupted.setText("This session was interrupted");
        styleCheckBox(interrupted);
        interrupted.setChecked("true".equals(session.interrupted));
        form.addView(interrupted);

        android.widget.ScrollView scrollableForm = new android.widget.ScrollView(this);
        scrollableForm.setFillViewport(true);
        scrollableForm.addView(form);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Edit study session")
                .setView(scrollableForm)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save changes", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(view -> {
                    LocalDate day = parseDate(date, "Date");
                    LocalTime begin = parseTime(start, "Start time");
                    LocalTime finish = parseTime(end, "End time");
                    if (day == null || begin == null || finish == null) return;
                    if (day.isAfter(LocalDate.now())) {
                        date.setError("Sessions can be logged for today or an earlier date");
                        return;
                    }
                    if (begin.equals(finish)) {
                        end.setError("End time must differ from start time");
                        return;
                    }
                    String topicId = selectedTopicId(topic);
                    if (topicId.isEmpty()) {
                        topic.setError("Choose a study topic");
                        topic.requestFocus();
                        topic.showDropDown();
                        return;
                    }
                    runStoreAction(() -> {
                        store.updateSession(session.id, day.toString(), begin.toString(),
                                finish.toString(), topicId, interrupted.isChecked());
                        render();
                        dialog.dismiss();
                    });
                }));
        dialog.show();
    }

    private String editableTime(String value) {
        try {
            return LocalTime.parse(value).format(DateTimeFormatter.ofPattern("HH:mm"));
        } catch (RuntimeException exception) {
            return value == null ? "" : value;
        }
    }

    private void confirmDeleteSession(TrackerStore.Session session) {
        new AlertDialog.Builder(this)
                .setTitle("Delete this study session?")
                .setMessage(session.date + " · " + topicNameForSession(session) + "\n"
                        + formatDuration(session.durationSeconds)
                        + "\nThis will also update your study-time totals.")
                .setNegativeButton("Keep session", null)
                .setPositiveButton("Delete session", (dialog, which) -> runStoreAction(() -> {
                    store.deleteSession(session.id);
                    render();
                }))
                .show();
    }

    private void editReadingLog(TrackerStore.ReadingLog entry) {
        LinearLayout form = vertical();
        form.setPadding(dp(8), dp(4), dp(8), dp(4));
        EditText date = input("Date", InputType.TYPE_CLASS_DATETIME);
        date.setText(entry.date);
        makeDatePicker(date, "Reading date", true);
        addFieldLabel(form, "DATE");
        form.addView(date);
        MaterialAutoCompleteTextView topic = topicDropdown(
                "Choose a topic", entry.topicId, topicNameForReading(entry));
        addFieldLabel(form, "TOPIC");
        form.addView(topic);
        EditText minutes = input("Minutes read", InputType.TYPE_CLASS_NUMBER);
        minutes.setText(Integer.toString(entry.durationMinutes));
        addFieldLabel(form, "TIME SPENT · MINUTES");
        form.addView(minutes);
        EditText pagesRead = input("Pages read (optional)", InputType.TYPE_CLASS_NUMBER);
        if (entry.pagesRead > 0) pagesRead.setText(Integer.toString(entry.pagesRead));
        addFieldLabel(form, "PAGES READ · OPTIONAL");
        form.addView(pagesRead);
        EditText notes = input("Chapter, pages, or takeaway", InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        notes.setMinLines(2);
        notes.setMaxLines(4);
        notes.setGravity(Gravity.TOP | Gravity.START);
        notes.setText(entry.notes);
        addFieldLabel(form, "YOUR READING NOTES");
        form.addView(notes);
        android.widget.ScrollView scrollableForm = new android.widget.ScrollView(this);
        scrollableForm.setFillViewport(true);
        scrollableForm.addView(form);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Edit reading log")
                .setView(scrollableForm)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save changes", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(view -> {
                    LocalDate day = parseDate(date, "Date");
                    if (day == null) return;
                    if (day.isAfter(LocalDate.now())) {
                        date.setError("Reading can be logged for today or an earlier date");
                        return;
                    }
                    String topicId = selectedTopicId(topic);
                    if (topicId.isEmpty()) {
                        topic.setError("Choose a topic");
                        topic.requestFocus();
                        topic.showDropDown();
                        return;
                    }
                    int duration;
                    try {
                        duration = Integer.parseInt(minutes.getText().toString().trim());
                        if (duration < 1 || duration > 720) throw new NumberFormatException();
                    } catch (NumberFormatException exception) {
                        minutes.setError("Choose 1 to 720 minutes");
                        return;
                    }
                    Integer pages = parseOptionalCount(pagesRead, "Pages read", 10000);
                    if (pages == null) return;
                    runStoreAction(() -> {
                        store.updateReadingLog(entry.id, day.toString(), topicId,
                                duration, pages, notes.getText().toString());
                        render();
                        dialog.dismiss();
                    });
                }));
        dialog.show();
    }

    private void confirmDeleteReadingLog(TrackerStore.ReadingLog entry) {
        new AlertDialog.Builder(this)
                .setTitle("Delete this reading log?")
                .setMessage(entry.date + " · " + topicNameForReading(entry) + "\n"
                        + formatDuration(entry.durationMinutes * 60)
                        + "\nThis will update your study-time totals.")
                .setNegativeButton("Keep log", null)
                .setPositiveButton("Delete log", (dialog, which) -> runStoreAction(() -> {
                    store.deleteReadingLog(entry.id);
                    render();
                }))
                .show();
    }

    private MaterialAutoCompleteTextView topicDropdown(
            String hint, String selectedTopicId, String selectedTopicName) {
        MaterialAutoCompleteTextView field = new MaterialAutoCompleteTextView(this);
        field.setHint(hint);
        field.setSingleLine(true);
        field.setThreshold(0);
        field.setInputType(InputType.TYPE_NULL);
        field.setTag("");
        List<String> names = new ArrayList<>();
        List<String> ids = new ArrayList<>();
        names.add("Choose a topic");
        ids.add("");
        int selectedPosition = 0;
        for (TrackerStore.Topic topic : store.getTopics()) {
            names.add(topic.name);
            ids.add(topic.id);
            if (!selectedTopicId.isEmpty() && selectedTopicId.equals(topic.id)
                    || selectedTopicId.isEmpty() && topic.name.equals(selectedTopicName)) {
                selectedPosition = names.size() - 1;
            }
        }
        field.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_1, names));
        field.setDropDownHeight(dp(420));
        field.setOnItemClickListener((parent, view, position, itemId) ->
                field.setTag(ids.get(position)));
        field.setText(selectedPosition == 0 ? "" : names.get(selectedPosition), false);
        if (selectedPosition > 0) field.setTag(ids.get(selectedPosition));
        field.setOnClickListener(view -> field.showDropDown());
        field.setOnTouchListener((view, event) -> {
            if (event.getAction() == MotionEvent.ACTION_UP && field.isEnabled()) {
                field.post(field::showDropDown);
            }
            return false;
        });
        field.setContentDescription(hint);
        return field;
    }

    private String selectedTopicId(EditText field) {
        Object tag = field.getTag();
        return tag instanceof String ? (String) tag : "";
    }

    private String topicIdForName(String name) {
        if (name == null || name.isEmpty()) return "";
        for (TrackerStore.Topic topic : store.getTopics()) {
            if (topic.name.equals(name)) return topic.id;
        }
        return "";
    }

    private String topicNameForSession(TrackerStore.Session session) {
        for (TrackerStore.Topic topic : store.getTopics()) {
            if (topic.id.equals(session.topicId)) return topic.name;
        }
        return session.topic == null ? "" : session.topic;
    }

    private void startTimer() {
        timerTopicIdValue = selectedTopicId(timerTopic);
        if (timerTopicIdValue.isEmpty()) {
            timerTopic.setError("Choose a study topic");
            timerTopic.requestFocus();
            timerTopic.showDropDown();
            return;
        }
        timerTopicValue = timerTopic.getText().toString().trim();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    REQUEST_NOTIFICATIONS);
            return;
        }
        if (!alarmNotificationsAvailable()) {
            new AlertDialog.Builder(this)
                    .setTitle("Turn on alarm notifications")
                    .setMessage("The focus timer needs enabled notifications so its alarm can be dismissed.")
                    .setNegativeButton("Not now", null)
                    .setPositiveButton("Open settings", (dialog, which) ->
                            openAlarmNotificationSettings())
                    .show();
            return;
        }
        if (!TimerAlarmScheduler.canScheduleExact(this)) {
            try {
                Intent settings = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                        Uri.parse("package:" + getPackageName()));
                startActivity(settings);
                Toast.makeText(this, "Allow exact alarms, then tap Start focus timer again.",
                        Toast.LENGTH_LONG).show();
            } catch (RuntimeException exception) {
                Toast.makeText(this, "Exact alarm access is required for an on-time timer alarm.",
                        Toast.LENGTH_LONG).show();
            }
            return;
        }
        int minutes;
        try {
            minutes = Integer.parseInt(timerMinutes.getText().toString().trim());
            if (minutes < 1 || minutes > 720) throw new NumberFormatException();
        } catch (NumberFormatException exception) {
            timerMinutes.setError("Choose 1 to 720 minutes");
            return;
        }

        timerDurationSeconds = minutes * 60;
        TimerAlarmScheduler.cancelSnooze(this);
        timerStartMillis = System.currentTimeMillis();
        timerEndMillis = timerStartMillis + timerDurationSeconds * 1000L;
        timerTopicValue = timerTopic.getText().toString().trim();
        timerInterruptedValue = timerInterrupted.isChecked();
        timerToken = UUID.randomUUID().toString();
        timerRunning = true;
        getSharedPreferences(TimerAlarmScheduler.PREFS, MODE_PRIVATE).edit()
                .putBoolean(TimerAlarmScheduler.KEY_RUNNING, true)
                .putBoolean(TimerAlarmScheduler.KEY_ALARM_RINGING, false)
                .putString(TimerAlarmScheduler.KEY_TOKEN, timerToken)
                .putLong(TimerAlarmScheduler.KEY_START_MILLIS, timerStartMillis)
                .putLong(TimerAlarmScheduler.KEY_END_MILLIS, timerEndMillis)
                .putInt(TimerAlarmScheduler.KEY_DURATION_SECONDS, timerDurationSeconds)
                .putString(TimerAlarmScheduler.KEY_TOPIC, timerTopicValue)
                .putString(TimerAlarmScheduler.KEY_TOPIC_ID, timerTopicIdValue)
                .putBoolean(TimerAlarmScheduler.KEY_INTERRUPTED, timerInterruptedValue)
                .apply();
        TimerAlarmScheduler.scheduleTimer(this, timerEndMillis, timerToken);
        render();
        handler.removeCallbacks(timerTick);
        handler.post(timerTick);
    }

    private void stopTimer() {
        android.content.SharedPreferences prefs = getSharedPreferences(
                TimerAlarmScheduler.PREFS, MODE_PRIVATE);
        if (!prefs.getBoolean(TimerAlarmScheduler.KEY_RUNNING, false)) {
            restoreTimerState();
            render();
            return;
        }
        long startMillis = timerStartMillis;
        int seconds = (int) Math.max(1,
                Math.min(timerDurationSeconds,
                        (System.currentTimeMillis() - timerStartMillis) / 1000));
        LocalDateTime start = LocalDateTime.ofInstant(
                Instant.ofEpochMilli(startMillis), ZoneId.systemDefault());
        LocalDateTime end = LocalDateTime.now();
        try {
            TimerAlarmScheduler.cancelTimer(this);
            TimerAlarmScheduler.cancelSnooze(this);
            store.addSession(start.toLocalDate().toString(),
                    start.format(DateTimeFormatter.ofPattern("HH:mm")),
                    end.format(DateTimeFormatter.ofPattern("HH:mm")),
                    seconds, timerTopicIdValue, timerTopicValue, timerInterruptedValue);
            prefs.edit()
                    .putBoolean(TimerAlarmScheduler.KEY_RUNNING, false)
                    .putBoolean(TimerAlarmScheduler.KEY_ALARM_RINGING, false)
                    .apply();
            timerRunning = false;
            handler.removeCallbacks(timerTick);
            Toast.makeText(this, "Session saved: " + formatDuration(seconds),
                    Toast.LENGTH_SHORT).show();
            render();
        } catch (IOException exception) {
            if (timerRunning) {
                TimerAlarmScheduler.scheduleTimer(this,
                        Math.max(System.currentTimeMillis() + 1000, timerEndMillis), timerToken);
            }
            showError(exception);
        }
    }

    private void renderStreaks() {
        pageHeader("Your streaks", "Show up consistently; let momentum build.");
        int count = 0;
        for (TrackerStore.Habit habit : store.getHabits()) {
            if (!habit.core) continue;
            count++;
            TrackerMetrics.Streak streak = TrackerMetrics.streak(
                    store, habit.id, LocalDate.now());
            LinearLayout body = addCardBody(content, Color.parseColor(AMBER));
            body.addView(text(habit.name, 16, true));
            LinearLayout highlight = row();
            ImageView flame = new ImageView(this);
            flame.setImageResource(R.drawable.ic_tab_streaks);
            flame.setColorFilter(Color.parseColor(AMBER));
            LinearLayout.LayoutParams flameParams = new LinearLayout.LayoutParams(dp(42), dp(42));
            flameParams.setMarginEnd(dp(10));
            highlight.addView(flame, flameParams);
            TextView current = text(Integer.toString(streak.current), 38, true);
            current.setTextColor(Color.parseColor(AMBER));
            highlight.addView(current);
            TextView days = text("day" + (streak.current == 1 ? "" : "s"), 16, true);
            days.setTextColor(Color.parseColor(MUTED));
            days.setPadding(dp(8), dp(12), dp(4), dp(8));
            highlight.addView(days);
            body.addView(highlight);
            addCardHintTo(body, "Longest: " + streak.longest + " days"
                    + " · Last missed: "
                    + (streak.lastMissed == null ? "None recorded" : streak.lastMissed));
        }
        if (count == 0) {
            addHint("Choose the habits that matter most from the Habits page to track streaks.");
            addSecondaryButton(content, "Choose core habits", () -> selectTab("Habits"));
        }
    }

    private void renderTopics() {
        pageHeader("Learning path", "Small steps add up. Pick a topic and work at your own pace.");
        TrackerMetrics.CurriculumProgress progress = TrackerMetrics.curriculum(store);
        LinearLayout summary = addCardBody(content, Color.parseColor(AMBER));
        summary.addView(text("Your progress", 15, true));
        summary.addView(text(progress.completedItems + " of " + progress.totalItems
                + " steps done · " + String.format(Locale.getDefault(),
                "%.0f%%", progress.percentage()), 13, false));
        LinearProgressIndicator overallProgress = new LinearProgressIndicator(this);
        overallProgress.setMax(100);
        overallProgress.setProgress((int) progress.percentage());
        overallProgress.setIndicatorColor(Color.parseColor(AMBER));
        overallProgress.setTrackColor(getColor(R.color.outline));
        overallProgress.setTrackCornerRadius(dp(4));
        summary.addView(overallProgress, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(8)));
        addHint("Tap a topic to see its checklist. Your progress is saved as you go.");
        for (TrackerStore.Topic topic : store.getTopics()) {
            addTopicCard(topic);
        }
        addPrimaryButton(content, "Add topic", () -> editTopic(null));
    }

    private void addTopicCard(TrackerStore.Topic topic) {
        int topicIndex = store.getTopics().indexOf(topic);
        int accent = getColor(TOPIC_ACCENTS[Math.max(0, topicIndex) % TOPIC_ACCENTS.length]);
        LinearLayout card = addCardBody(content, accent);
        card.setTag("topic:" + topic.id);

        LinearLayout heading = row();
        TextView name = text(topic.name, 16, true);
        name.setTextColor(Color.parseColor(GREEN));
        heading.addView(name, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        TextView expand = text(expandedTopics.contains(topic.id) ? "Hide" : "Steps", 12, true);
        expand.setTextColor(Color.parseColor(MUTED));
        expand.setGravity(Gravity.CENTER);
        expand.setMinWidth(dp(52));
        expand.setMinHeight(dp(44));
        expand.setContentDescription(expandedTopics.contains(topic.id)
                ? "Hide " + topic.name + " checklist" : "Show " + topic.name + " checklist");
        expand.setOnClickListener(view -> toggleTopic(topic));
        heading.addView(expand);
        TextView edit = text("Edit", 12, true);
        edit.setTextColor(Color.parseColor(AMBER));
        edit.setGravity(Gravity.CENTER);
        edit.setMinWidth(dp(48));
        edit.setMinHeight(dp(44));
        edit.setContentDescription("Edit " + topic.name + " name and guidance");
        edit.setOnClickListener(view -> editTopic(topic));
        heading.addView(edit);
        card.addView(heading);
        name.setOnClickListener(view -> toggleTopic(topic));
        heading.setOnClickListener(view -> toggleTopic(topic));

        TextView guidance = text(topic.guidance.isEmpty()
                ? "No guidance note yet." : topic.guidance, 13, false);
        guidance.setTextColor(Color.parseColor(MUTED));
        card.addView(guidance);

        int completed = 0;
        for (TrackerStore.TopicItem item : topic.items) if (item.done) completed++;
        int percentage = topic.items.isEmpty() ? 0 : completed * 100 / topic.items.size();
        TextView progressText = text(topic.items.isEmpty() ? "Add your first step"
                : completed + " of " + topic.items.size() + " steps · " + percentage + "%",
                12, true);
        card.addView(progressText);
        LinearProgressIndicator progressBar = new LinearProgressIndicator(this);
        progressBar.setMax(100);
        progressBar.setProgress(percentage);
        progressBar.setIndicatorColor(accent);
        progressBar.setTrackColor(getColor(R.color.outline));
        progressBar.setTrackCornerRadius(dp(4));
        card.addView(progressBar, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(10)));

        if (expandedTopics.contains(topic.id)) {
            for (TrackerStore.TopicItem item : topic.items) {
                LinearLayout itemRow = row();
                CheckBox check = new CheckBox(this);
                styleCheckBox(check);
                check.setChecked(item.done);
                check.setContentDescription(item.text);
                check.setOnCheckedChangeListener((button, checked) ->
                        runStoreAction(() -> {
                            store.setTopicItemDone(topic.id, item.id, checked);
                            render();
                        }));
                itemRow.addView(check);
                TextView itemText = text(item.text, 13, false);
                itemRow.addView(itemText, new LinearLayout.LayoutParams(0,
                        LinearLayout.LayoutParams.WRAP_CONTENT, 1));
                itemText.setOnClickListener(view -> editTopicItem(topic, item));
                TextView editStep = text("Edit", 12, true);
                editStep.setTextColor(Color.parseColor(AMBER));
                editStep.setGravity(Gravity.CENTER);
                editStep.setMinWidth(dp(52));
                editStep.setMinHeight(dp(44));
                editStep.setContentDescription("Edit step: " + item.text);
                editStep.setOnClickListener(view -> editTopicItem(topic, item));
                itemRow.addView(editStep);
                card.addView(itemRow);
            }
            addSecondaryButton(card, "Add a step", () -> editTopicItem(topic, null));
        }
    }

    private void toggleTopic(TrackerStore.Topic topic) {
        if (!expandedTopics.add(topic.id)) expandedTopics.remove(topic.id);
        render();
    }

    private void editTopic(TrackerStore.Topic topic) {
        LinearLayout form = vertical();
        form.setPadding(dp(8), 0, dp(8), dp(4));
        form.addView(text(topic == null
                ? "Add a subject and a short note to guide your study."
                : "Update the subject name or the reminder you see while studying.",
                13, false));

        addFieldLabel(form, "TOPIC NAME");
        EditText name = input("e.g. Networking", InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        name.setSingleLine(true);
        form.addView(name);

        addFieldLabel(form, "HOW TO HANDLE IT");
        TextView guidanceHint = text(
                "A short, practical reminder works best.", 11, false);
        guidanceHint.setTextColor(Color.parseColor(MUTED));
        form.addView(guidanceHint);
        EditText guidance = input("What should you focus on?", InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        guidance.setMinLines(4);
        guidance.setMaxLines(6);
        guidance.setGravity(Gravity.TOP | Gravity.START);
        if (topic != null) {
            name.setText(topic.name);
            guidance.setText(topic.guidance);
        }
        form.addView(guidance);

        MaterialButton manageSteps = null;
        if (topic != null) {
            int itemCount = topic.items.size();
            TextView checklistTitle = text("CHECKLIST", 12, true);
            checklistTitle.setTextColor(Color.parseColor(GREEN));
            checklistTitle.setPadding(dp(6), dp(12), dp(6), 0);
            form.addView(checklistTitle);
            TextView checklistDescription = text(itemCount == 1
                    ? "1 step · checked-off progress is kept when you edit this topic."
                    : itemCount + " steps · checked-off progress is kept when you edit this topic.",
                    11, false);
            checklistDescription.setTextColor(Color.parseColor(MUTED));
            checklistDescription.setPadding(dp(6), dp(2), dp(6), dp(4));
            form.addView(checklistDescription);
            manageSteps = outlinedButton();
            manageSteps.setText("Manage steps");
            form.addView(manageSteps);
        }

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(topic == null ? "Add topic" : "Edit topic")
                .setView(form)
                .setNegativeButton("Cancel", null)
                .setPositiveButton(topic == null ? "Add topic" : "Save changes", null)
                .create();
        if (topic != null) dialog.setButton(AlertDialog.BUTTON_NEUTRAL, "Remove", (d, which) -> {});
        dialog.setOnShowListener(ignored -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
                String topicName = name.getText().toString().trim();
                if (topicName.isEmpty()) {
                    name.setError("Enter a topic name");
                    return;
                }
                runStoreAction(() -> {
                    if (topic == null) {
                        store.addTopic(topicName, guidance.getText().toString());
                    } else {
                        store.updateTopic(topic.id, topicName, guidance.getText().toString());
                    }
                    render();
                    dialog.dismiss();
                });
            });
            if (topic != null) {
                dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setTextColor(getColor(R.color.danger));
                dialog.getButton(AlertDialog.BUTTON_NEUTRAL)
                        .setOnClickListener(view -> confirmDeleteTopic(topic, dialog));
            }
        });
        if (manageSteps != null) {
            manageSteps.setOnClickListener(view -> {
                expandedTopics.add(topic.id);
                dialog.dismiss();
                render();
                scroll.post(() -> {
                    for (int i = 0; i < content.getChildCount(); i++) {
                        View child = content.getChildAt(i);
                        if (("topic:" + topic.id).equals(child.getTag())) {
                            scroll.smoothScrollTo(0, child.getTop());
                            break;
                        }
                    }
                });
            });
        }
        dialog.show();
        android.view.Window window = dialog.getWindow();
        if (window != null) {
            android.graphics.drawable.GradientDrawable background =
                    new android.graphics.drawable.GradientDrawable();
            background.setColor(getColor(R.color.surface));
            background.setCornerRadius(dp(14));
            background.setStroke(dp(1), getColor(R.color.outline));
            window.setBackgroundDrawable(background);
        }
        for (int which : new int[]{AlertDialog.BUTTON_NEUTRAL,
                AlertDialog.BUTTON_NEGATIVE, AlertDialog.BUTTON_POSITIVE}) {
            android.widget.Button button = dialog.getButton(which);
            if (button != null) {
                button.setAllCaps(false);
                button.setLetterSpacing(0);
                button.setTextSize(14);
            }
        }
        if (topic != null) {
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setTextColor(getColor(R.color.danger));
        }
    }

    private void confirmDeleteTopic(TrackerStore.Topic topic) {
        confirmDeleteTopic(topic, null);
    }

    private void confirmDeleteTopic(TrackerStore.Topic topic, AlertDialog editDialog) {
        new AlertDialog.Builder(this)
                .setTitle("Remove topic?")
                .setMessage("This also removes its curriculum checklist.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Remove", (dialog, which) -> runStoreAction(() -> {
                    store.deleteTopic(topic.id);
                    expandedTopics.remove(topic.id);
                    if (editDialog != null) editDialog.dismiss();
                    render();
                })).show();
    }

    private void editTopicItem(TrackerStore.Topic topic, TrackerStore.TopicItem item) {
        EditText text = input("Sub-item text", InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        if (item != null) text.setText(item.text);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(item == null ? "Add sub-item" : "Edit sub-item")
                .setView(text)
                .setNegativeButton("Cancel", null)
                .setPositiveButton(item == null ? "Add" : "Save", null)
                .create();
        if (item != null) dialog.setButton(AlertDialog.BUTTON_NEUTRAL, "Remove", (d, which) -> {});
        dialog.setOnShowListener(ignored -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
                String value = text.getText().toString().trim();
                if (value.isEmpty()) {
                    text.setError("Enter sub-item text");
                    return;
                }
                runStoreAction(() -> {
                    if (item == null) store.addTopicItem(topic.id, value);
                    else store.updateTopicItem(topic.id, item.id, value);
                    expandedTopics.add(topic.id);
                    render();
                    dialog.dismiss();
                });
            });
            if (item != null) dialog.getButton(AlertDialog.BUTTON_NEUTRAL)
                    .setOnClickListener(view -> confirmDeleteTopicItem(topic, item, dialog));
        });
        dialog.show();
    }

    private void confirmDeleteTopicItem(TrackerStore.Topic topic, TrackerStore.TopicItem item) {
        confirmDeleteTopicItem(topic, item, null);
    }

    private void confirmDeleteTopicItem(
            TrackerStore.Topic topic, TrackerStore.TopicItem item, AlertDialog editDialog) {
        new AlertDialog.Builder(this)
                .setTitle("Remove sub-item?")
                .setMessage(item.text)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Remove", (dialog, which) -> runStoreAction(() -> {
                    store.deleteTopicItem(topic.id, item.id);
                    if (editDialog != null) editDialog.dismiss();
                    render();
                })).show();
    }

    private void renderReviews() {
        pageHeader("Review", "Look back at what you did, then choose what comes next.");
        LinearLayout period = row();
        MaterialButton previous = outlinedButton();
        previous.setText("‹");
        previous.setContentDescription("Previous week");
        period.addView(previous, new LinearLayout.LayoutParams(dp(52), dp(44)));
        previous.setOnClickListener(view -> {
            reviewWeekStart = reviewWeekStart.minusWeeks(1);
            render();
        });
        TextView periodLabel = text(reviewWeekLabel(), 14, true);
        periodLabel.setGravity(Gravity.CENTER);
        period.addView(periodLabel, new LinearLayout.LayoutParams(0, dp(48), 1));
        MaterialButton next = outlinedButton();
        next.setText("›");
        next.setContentDescription("Next week");
        next.setEnabled(reviewWeekStart.isBefore(LocalDate.now().with(DayOfWeek.MONDAY)));
        period.addView(next, new LinearLayout.LayoutParams(dp(52), dp(44)));
        next.setOnClickListener(view -> {
            if (reviewWeekStart.isBefore(LocalDate.now().with(DayOfWeek.MONDAY))) {
                reviewWeekStart = reviewWeekStart.plusWeeks(1);
                render();
            }
        });
        LinearLayout periodCard = addCardBody(content, Color.parseColor(GREEN));
        periodCard.addView(period);

        TrackerMetrics.Week metrics = TrackerMetrics.week(store, reviewWeekStart);
        LinearLayout metricCard = addCardBody(content, Color.parseColor(GREEN));
        metricCard.addView(text("Your week", 16, true));
        LinearLayout metricRowOne = row();
        addMetricTile(metricRowOne, "Complete days", store.getHabits().isEmpty()
                ? "—" : metrics.allHabitsDays + "/" + metrics.daysConsidered, GREEN);
        addMetricTile(metricRowOne, "Target success",
                reviewTargetRate(reviewWeekStart), GREEN);
        metricCard.addView(metricRowOne);
        LinearLayout metricRowTwo = row();
        addMetricTile(metricRowTwo, "Study time", metrics.sessionSeconds == 0
                ? "—" : formatDuration(metrics.sessionSeconds), AMBER);
        addMetricTile(metricRowTwo, "Longest streak", metrics.longestStreak == 0
                ? "—" : metrics.longestStreak + " days", AMBER);
        metricCard.addView(metricRowTwo);

        sectionTitle("Weekly activity");
        int[] studySecondsByDay = new int[7];
        int[] completedHabitsByDay = new int[7];
        List<TrackerStore.Habit> reviewHabits = store.getHabits();
        int daysConsidered = metrics.daysConsidered;
        for (int i = 0; i < 7; i++) {
            LocalDate day = reviewWeekStart.plusDays(i);
            if (day.isAfter(LocalDate.now())) continue;
            for (TrackerStore.Session session : store.getSessions()) {
                if (day.toString().equals(session.date)) {
                    studySecondsByDay[i] += session.durationSeconds;
                }
            }
            for (TrackerStore.ReadingLog reading : store.getReadingLogs()) {
                if (day.toString().equals(reading.date)) {
                    studySecondsByDay[i] += reading.durationMinutes * 60;
                }
            }
            for (TrackerStore.Habit habit : reviewHabits) {
                if (TrackerMetrics.isDone(store.getChecks(), habit.id, day)) {
                    completedHabitsByDay[i]++;
                }
            }
        }
        LinearLayout chartCard = addCardBody(content, Color.parseColor(GREEN));
        LinearLayout legend = row();
        addChartLegend(legend, GREEN, "Study time");
        if (!reviewHabits.isEmpty()) addChartLegend(legend, AMBER, "Habits complete");
        chartCard.addView(legend);
        WeeklyActivityChart chart = new WeeklyActivityChart(
                studySecondsByDay, completedHabitsByDay, reviewHabits.size(), daysConsidered);
        chartCard.addView(chart, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(210)));
        boolean hasStudyTime = false;
        boolean hasHabitCheckins = false;
        for (int i = 0; i < 7; i++) {
            hasStudyTime |= studySecondsByDay[i] > 0;
            hasHabitCheckins |= completedHabitsByDay[i] > 0;
        }
        addCardHintTo(chartCard, !hasStudyTime && !hasHabitCheckins
                ? "No activity recorded yet. Start a focus session, log reading, or check off a habit to see your week take shape."
                : "Green bars show study time from focus sessions and reading. The amber line tracks habits you checked off.");

        TrackerMetrics.CurriculumProgress curriculum = TrackerMetrics.curriculum(store);
        LinearLayout curriculumCard = addCardBody(content, Color.parseColor(AMBER));
        curriculumCard.addView(text("Learning so far", 15, true));
        curriculumCard.addView(text(curriculum.completedItems + "/"
                + curriculum.totalItems + " steps · "
                + String.format(Locale.getDefault(), "%.0f%%", curriculum.percentage()),
                14, true));
        LinearProgressIndicator curriculumBar = new LinearProgressIndicator(this);
        curriculumBar.setMax(100);
        curriculumBar.setProgress((int) curriculum.percentage());
        curriculumBar.setIndicatorColor(Color.parseColor(AMBER));
        curriculumBar.setTrackColor(getColor(R.color.outline));
        curriculumBar.setTrackCornerRadius(dp(4));
        curriculumCard.addView(curriculumBar, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(8)));

        TrackerStore.Review review = store.getReview(reviewWeekStart.toString());
        sectionTitle("A note to yourself");
        LinearLayout reflectionForm = addCardBody(content, getColor(R.color.outline));
        EditText blocker = input("Biggest blocker this week",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        blocker.setText(review.blocker);
        addFieldLabel(reflectionForm, "Biggest blocker this week");
        EditText change = input("One change for next week",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        change.setText(review.change);
        addFieldLabel(reflectionForm, "One change for next week");
        EditText notes = input("Other notes",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        notes.setText(review.notes);
        addFieldLabel(reflectionForm, "Other notes");
        reflectionForm.addView(blocker);
        reflectionForm.addView(change);
        reflectionForm.addView(notes);
        addPrimaryButton(reflectionForm, "Save note", () -> runStoreAction(() -> {
            store.saveReview(reviewWeekStart.toString(), blocker.getText().toString(),
                    change.getText().toString(), notes.getText().toString());
            Toast.makeText(this, "Your note is saved", Toast.LENGTH_SHORT).show();
        }));

        sectionTitle("Month at a glance");
        renderMonthly(reviewMonth);
    }

    private String reviewWeekLabel() {
        LocalDate currentMonday = LocalDate.now().with(DayOfWeek.MONDAY);
        if (reviewWeekStart.equals(currentMonday)) return "This week";
        return reviewWeekStart.format(DateTimeFormatter.ofPattern("MMM d", Locale.getDefault()))
                + " – " + reviewWeekStart.plusDays(6).format(
                DateTimeFormatter.ofPattern("MMM d", Locale.getDefault()));
    }

    private void renderMonthly(YearMonth month) {
        removeMonthlyRows();
        LinearLayout monthControls = row();
        MaterialButton previous = outlinedButton();
        previous.setText("‹");
        previous.setContentDescription("Previous month");
        monthControls.addView(previous, new LinearLayout.LayoutParams(dp(52), dp(44)));
        previous.setOnClickListener(view -> {
            reviewMonth = reviewMonth.minusMonths(1);
            render();
        });
        TextView monthLabel = text(month.equals(YearMonth.now()) ? "This month"
                : month.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())),
                14, true);
        monthLabel.setGravity(Gravity.CENTER);
        monthControls.addView(monthLabel, new LinearLayout.LayoutParams(0, dp(48), 1));
        MaterialButton next = outlinedButton();
        next.setText("›");
        next.setContentDescription("Next month");
        next.setEnabled(month.isBefore(YearMonth.now()));
        monthControls.addView(next, new LinearLayout.LayoutParams(dp(52), dp(44)));
        next.setOnClickListener(view -> {
            if (reviewMonth.isBefore(YearMonth.now())) {
                reviewMonth = reviewMonth.plusMonths(1);
                render();
            }
        });
        LinearLayout monthControlsCard = addCardBody(content, Color.parseColor(GREEN));
        monthControlsCard.addView(monthControls);

        LocalDate monthStart = month.atDay(1);
        LocalDate end = month.atEndOfMonth().isBefore(LocalDate.now())
                ? month.atEndOfMonth() : LocalDate.now();
        int totalSeconds = 0;
        int sessionCount = 0;
        int readingCount = 0;
        int targetCount = 0;
        int targetHits = 0;
        int recordedHabitDays = 0;
        int completedHabitDays = 0;
        for (TrackerStore.Session session : store.getSessions()) {
            LocalDate day = safeDate(session.date);
            if (day != null && !day.isBefore(monthStart) && !day.isAfter(end)) {
                totalSeconds += session.durationSeconds;
                sessionCount++;
            }
        }
        for (TrackerStore.ReadingLog reading : store.getReadingLogs()) {
            LocalDate day = safeDate(reading.date);
            if (day != null && !day.isBefore(monthStart) && !day.isAfter(end)) {
                totalSeconds += reading.durationMinutes * 60;
                readingCount++;
            }
        }
        for (TrackerStore.TargetEntry entry : store.getTargets()) {
            LocalDate day = safeDate(entry.date);
            if (day != null && !day.isBefore(monthStart) && !day.isAfter(end)) {
                targetCount++;
                if ("true".equals(entry.hit)) targetHits++;
            }
        }
        List<TrackerStore.Habit> habits = store.getHabits();
        if (!habits.isEmpty()) {
            for (LocalDate day = monthStart; !day.isAfter(end); day = day.plusDays(1)) {
                Map<String, Boolean> checks = store.getChecks().get(day.toString());
                if (checks == null || checks.isEmpty()) continue;
                recordedHabitDays++;
                boolean allDone = true;
                for (TrackerStore.Habit habit : habits) {
                    if (!Boolean.TRUE.equals(checks.get(habit.id))) {
                        allDone = false;
                        break;
                    }
                }
                if (allDone) completedHabitDays++;
            }
        }

        LinearLayout summary = addCardBody(content, Color.parseColor(GREEN));
        summary.addView(text("A month of showing up", 16, true));
        LinearLayout firstMetrics = row();
        addMetricTile(firstMetrics, "Study time", totalSeconds == 0
                ? "No study time" : formatDuration(totalSeconds), GREEN);
        addMetricTile(firstMetrics, "Focus sessions", Integer.toString(sessionCount), GREEN);
        summary.addView(firstMetrics);
        if (readingCount > 0) {
            addCardHintTo(summary, readingCount + (readingCount == 1
                    ? " reading log contributed to study time."
                    : " reading logs contributed to study time."));
        }
        LinearLayout secondMetrics = row();
        addMetricTile(secondMetrics, "Goals met", targetCount == 0 ? "—"
                : targetHits + "/" + targetCount, AMBER);
        addMetricTile(secondMetrics, "Full habit days", recordedHabitDays == 0 ? "—"
                : completedHabitDays + "/" + recordedHabitDays, AMBER);
        summary.addView(secondMetrics);

        sectionTitle("Weeks so far");
        LinearLayout weeks = new LinearLayout(this);
        weeks.setOrientation(LinearLayout.VERTICAL);
        weeks.setTag("monthly-rows");
        content.addView(weeks);

        List<MonthlyWeek> monthlyWeeks = new ArrayList<>();
        LocalDate monday = monthStart.with(DayOfWeek.MONDAY);
        while (!monday.isAfter(end)) {
            LocalDate firstDay = monday.isBefore(monthStart) ? monthStart : monday;
            LocalDate lastDay = monday.plusDays(6).isAfter(end) ? end : monday.plusDays(6);
            int seconds = 0;
            int sessions = 0;
            int readings = 0;
            int targets = 0;
            int hits = 0;
            int fullHabitDays = 0;
            int trackedHabitDays = 0;
            int longestCoreStreak = 0;
            int coreRun = 0;
            List<TrackerStore.Habit> monthHabits = store.getHabits();
            List<TrackerStore.Habit> coreHabits = new ArrayList<>();
            for (TrackerStore.Habit habit : monthHabits) {
                if (habit.core) coreHabits.add(habit);
            }
            for (LocalDate day = firstDay; !day.isAfter(lastDay); day = day.plusDays(1)) {
                Map<String, Boolean> checks = store.getChecks().get(day.toString());
                if (checks != null && !checks.isEmpty() && !monthHabits.isEmpty()) {
                    trackedHabitDays++;
                    boolean allDone = true;
                    for (TrackerStore.Habit habit : monthHabits) {
                        if (!Boolean.TRUE.equals(checks.get(habit.id))) {
                            allDone = false;
                            break;
                        }
                    }
                    if (allDone) fullHabitDays++;
                }
                if (!coreHabits.isEmpty()) {
                    boolean allCoreDone = true;
                    for (TrackerStore.Habit habit : coreHabits) {
                        if (!TrackerMetrics.isDone(store.getChecks(), habit.id, day)) {
                            allCoreDone = false;
                            break;
                        }
                    }
                    coreRun = allCoreDone ? coreRun + 1 : 0;
                    longestCoreStreak = Math.max(longestCoreStreak, coreRun);
                }
            }
            for (TrackerStore.Session session : store.getSessions()) {
                LocalDate day = safeDate(session.date);
                if (day != null && !day.isBefore(firstDay) && !day.isAfter(lastDay)) {
                    seconds += session.durationSeconds;
                    sessions++;
                }
            }
            for (TrackerStore.ReadingLog reading : store.getReadingLogs()) {
                LocalDate day = safeDate(reading.date);
                if (day != null && !day.isBefore(firstDay) && !day.isAfter(lastDay)) {
                    seconds += reading.durationMinutes * 60;
                    readings++;
                }
            }
            for (TrackerStore.TargetEntry entry : store.getTargets()) {
                LocalDate day = safeDate(entry.date);
                if (day != null && !day.isBefore(firstDay) && !day.isAfter(lastDay)) {
                    targets++;
                    if ("true".equals(entry.hit)) hits++;
                }
            }
            monthlyWeeks.add(new MonthlyWeek(firstDay, lastDay, seconds,
                    sessions, readings, targets, hits, fullHabitDays, trackedHabitDays,
                    longestCoreStreak));
            monday = monday.plusWeeks(1);
        }
        int maxWeekSeconds = 0;
        for (MonthlyWeek week : monthlyWeeks) {
            maxWeekSeconds = Math.max(maxWeekSeconds, week.seconds);
        }
        boolean hasMonthActivity = totalSeconds > 0 || targetCount > 0 || recordedHabitDays > 0;
        if (!hasMonthActivity) {
            addHintTo(weeks, "No activity recorded for this month yet.");
        } else {
            for (int i = 0; i < monthlyWeeks.size(); i++) {
                MonthlyWeek week = monthlyWeeks.get(i);
                LinearLayout body = addCardBody(weeks, Color.parseColor(GREEN));
                LinearLayout heading = row();
                String dateRange = week.start.format(DateTimeFormatter.ofPattern(
                        "MMM d", Locale.getDefault())) + " – "
                        + week.end.format(DateTimeFormatter.ofPattern(
                        "MMM d", Locale.getDefault()));
                heading.addView(text(dateRange, 14, true),
                        new LinearLayout.LayoutParams(0,
                                LinearLayout.LayoutParams.WRAP_CONTENT, 1));
                heading.addView(text(formatDuration(week.seconds), 13, true));
                body.addView(heading);
                LinearProgressIndicator progress = new LinearProgressIndicator(this);
                progress.setMax(100);
                progress.setProgress(maxWeekSeconds == 0 ? 0
                        : week.seconds * 100 / maxWeekSeconds);
                progress.setIndicatorColor(Color.parseColor(GREEN));
                progress.setTrackColor(getColor(R.color.outline));
                progress.setTrackCornerRadius(dp(4));
                body.addView(progress, new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, dp(7)));
                String details = week.sessions + (week.sessions == 1
                        ? " focus session" : " focus sessions");
                if (week.readingLogs > 0) details += " · " + week.readingLogs
                        + (week.readingLogs == 1 ? " reading log" : " reading logs");
                if (week.targets > 0) details += " · " + week.hits + "/" + week.targets
                        + " goals met";
                if (week.trackedHabitDays > 0) details += " · " + week.fullHabitDays
                        + "/" + week.trackedHabitDays + " full habit days";
                if (week.longestCoreStreak > 0) details += " · "
                        + week.longestCoreStreak + "-day core streak";
                addCardHintTo(body, details);
            }
        }
    }

    private static final class MonthlyWeek {
        final LocalDate start;
        final LocalDate end;
        final int seconds;
        final int sessions;
        final int readingLogs;
        final int targets;
        final int hits;
        final int fullHabitDays;
        final int trackedHabitDays;
        final int longestCoreStreak;

        MonthlyWeek(LocalDate start, LocalDate end, int seconds,
                    int sessions, int readingLogs, int targets, int hits, int fullHabitDays,
                    int trackedHabitDays, int longestCoreStreak) {
            this.start = start;
            this.end = end;
            this.seconds = seconds;
            this.sessions = sessions;
            this.readingLogs = readingLogs;
            this.targets = targets;
            this.hits = hits;
            this.fullHabitDays = fullHabitDays;
            this.trackedHabitDays = trackedHabitDays;
            this.longestCoreStreak = longestCoreStreak;
        }
    }

    private String reviewTargetRate(LocalDate monday) {
        int entries = 0;
        int hits = 0;
        LocalDate lastDay = monday.plusDays(6).isBefore(LocalDate.now())
                ? monday.plusDays(6) : LocalDate.now();
        for (TrackerStore.TargetEntry entry : store.getTargets()) {
            LocalDate day = safeDate(entry.date);
            if (day != null && !day.isBefore(monday) && !day.isAfter(lastDay)) {
                entries++;
                if ("true".equals(entry.hit)) hits++;
            }
        }
        return entries == 0 ? "—" : String.format(Locale.getDefault(),
                "%.0f%%", hits * 100.0 / entries);
    }

    private void addChartLegend(LinearLayout parent, String color, String label) {
        View swatch = new View(this);
        swatch.setBackgroundColor(Color.parseColor(color));
        LinearLayout.LayoutParams swatchParams = new LinearLayout.LayoutParams(dp(10), dp(10));
        swatchParams.setMarginStart(dp(6));
        swatchParams.setMarginEnd(dp(5));
        parent.addView(swatch, swatchParams);
        TextView text = text(label, 11, false);
        text.setTextColor(Color.parseColor(MUTED));
        parent.addView(text);
    }

    private final class FocusSproutView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private float progress;
        private boolean darkBackground;

        FocusSproutView() {
            super(MainActivity.this);
            setContentDescription("A small plant grows as your focus timer progresses");
        }

        void setProgress(float value) {
            progress = Math.max(0f, Math.min(1f, value));
            invalidate();
        }

        void setDarkBackground(boolean value) {
            darkBackground = value;
            invalidate();
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float density = getResources().getDisplayMetrics().density;
            float centerX = getWidth() / 2f;
            float baseY = getHeight() * 0.82f;
            float stemTop = getHeight() * 0.2f;

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(darkBackground
                    ? Color.parseColor("#D7EBD9") : getColor(R.color.timer_track));
            canvas.drawCircle(centerX, getHeight() / 2f, getWidth() * 0.46f, paint);

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(2.2f * density);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setColor(darkBackground
                    ? Color.parseColor("#347A61") : getColor(R.color.timer_accent));
            canvas.drawLine(centerX, baseY, centerX, stemTop, paint);
            float firstBranchY = getHeight() * 0.62f;
            canvas.drawLine(centerX, firstBranchY,
                    getWidth() * 0.28f, getHeight() * 0.56f, paint);
            canvas.drawLine(centerX, firstBranchY,
                    getWidth() * 0.72f, getHeight() * 0.56f, paint);
            drawLeaf(canvas, getWidth() * 0.28f, getHeight() * 0.56f,
                    -48, density, false);
            drawLeaf(canvas, getWidth() * 0.72f, getHeight() * 0.56f,
                    48, density, false);
            if (progress >= 0.34f) {
                float secondBranchY = getHeight() * 0.44f;
                canvas.drawLine(centerX, secondBranchY,
                        getWidth() * 0.3f, getHeight() * 0.39f, paint);
                canvas.drawLine(centerX, secondBranchY,
                        getWidth() * 0.7f, getHeight() * 0.39f, paint);
                drawLeaf(canvas, getWidth() * 0.3f, getHeight() * 0.39f,
                        -48, density, false);
                drawLeaf(canvas, getWidth() * 0.7f, getHeight() * 0.39f,
                        48, density, false);
            }
            if (progress >= 0.68f) {
                drawLeaf(canvas, centerX, stemTop + 3 * density, 0, density, true);
            }
        }

        private void drawLeaf(Canvas canvas, float x, float y, float rotation,
                              float density, boolean accent) {
            canvas.save();
            canvas.rotate(rotation, x, y);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(accent ? getColor(R.color.secondary)
                    : darkBackground ? Color.parseColor("#347A61")
                    : getColor(R.color.timer_accent));
            canvas.drawOval(x - 4.5f * density, y - 7f * density,
                    x + 4.5f * density, y + 7f * density, paint);
            canvas.restore();
        }
    }

    private final class WeeklyActivityChart extends View {
        private final int[] studySeconds;
        private final int[] completedHabits;
        private final int habitCount;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final String[] days = new String[7];

        private final int daysConsidered;

        WeeklyActivityChart(int[] studySeconds, int[] completedHabits, int habitCount,
                            int daysConsidered) {
            super(MainActivity.this);
            this.studySeconds = studySeconds.clone();
            this.completedHabits = completedHabits.clone();
            this.habitCount = habitCount;
            this.daysConsidered = daysConsidered;
            for (int i = 0; i < days.length; i++) {
                days[i] = reviewWeekStart.plusDays(i).getDayOfWeek().getDisplayName(
                        java.time.format.TextStyle.SHORT, Locale.getDefault());
            }
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float density = getResources().getDisplayMetrics().density;
            float left = 34 * density;
            float right = getWidth() - (habitCount > 0 ? 34 : 12) * density;
            float top = 18 * density;
            float bottom = getHeight() - 30 * density;
            if (right <= left || bottom <= top) return;

            int maxSeconds = 0;
            for (int seconds : studySeconds) maxSeconds = Math.max(maxSeconds, seconds);
            float maxHours = Math.max(1f, (float) Math.ceil(maxSeconds / 3600f));
            float chartHeight = bottom - top;
            float chartWidth = right - left;
            paint.setTextSize(10 * density);
            paint.setStrokeWidth(1 * density);
            paint.setColor(Color.parseColor(LINE));
            for (int tick = 0; tick <= 2; tick++) {
                float y = bottom - chartHeight * tick / 2f;
                canvas.drawLine(left, y, right, y, paint);
                String label = formatChartHours(maxHours * tick / 2f);
                paint.setColor(Color.parseColor(MUTED));
                paint.setStyle(Paint.Style.FILL);
                canvas.drawText(label, 0, label.length(), 0, y + 3 * density, paint);
                paint.setColor(Color.parseColor(LINE));
            }
            if (habitCount > 0) {
                paint.setColor(Color.parseColor(AMBER));
                paint.setTextSize(10 * density);
                paint.setStyle(Paint.Style.FILL);
                canvas.drawText("100%", right + 3 * density, top + 3 * density, paint);
                canvas.drawText("50%", right + 3 * density,
                        top + chartHeight / 2f + 3 * density, paint);
                canvas.drawText("0%", right + 3 * density, bottom + 3 * density, paint);
            }

            float cellWidth = chartWidth / 7f;
            float barWidth = cellWidth * 0.48f;
            paint.setColor(Color.parseColor(GREEN));
            paint.setStyle(Paint.Style.FILL);
            for (int i = 0; i < 7; i++) {
                float centerX = left + cellWidth * (i + 0.5f);
                if (i < daysConsidered) {
                    float hours = studySeconds[i] / 3600f;
                    float barHeight = chartHeight * Math.min(hours / maxHours, 1f);
                    canvas.drawRoundRect(centerX - barWidth / 2f, bottom - barHeight,
                            centerX + barWidth / 2f, bottom, 4 * density, 4 * density, paint);
                }
                paint.setColor(Color.parseColor(MUTED));
                paint.setTextSize(10 * density);
                float dayWidth = paint.measureText(days[i]);
                canvas.drawText(days[i], centerX - dayWidth / 2f,
                        getHeight() - 7 * density, paint);
                paint.setColor(Color.parseColor(GREEN));
            }

            if (habitCount > 0) {
                Path completionLine = new Path();
                boolean started = false;
                for (int i = 0; i < daysConsidered; i++) {
                    float rate = (float) completedHabits[i] / habitCount;
                    float x = left + cellWidth * (i + 0.5f);
                    float y = bottom - chartHeight * rate;
                    if (!started) {
                        completionLine.moveTo(x, y);
                        started = true;
                    }
                    else completionLine.lineTo(x, y);
                }
                paint.setColor(Color.parseColor(AMBER));
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(2.5f * density);
                canvas.drawPath(completionLine, paint);
                paint.setStyle(Paint.Style.FILL);
                for (int i = 0; i < daysConsidered; i++) {
                    float rate = (float) completedHabits[i] / habitCount;
                    float x = left + cellWidth * (i + 0.5f);
                    float y = bottom - chartHeight * rate;
                    canvas.drawCircle(x, y, 4 * density, paint);
                }
            }
        }

        private String formatChartHours(float hours) {
            if (hours == 0) return "0";
            if (hours < 1) return "30m";
            if (hours % 1 == 0) return String.format(Locale.getDefault(), "%.0fh", hours);
            return String.format(Locale.getDefault(), "%.1fh", hours);
        }
    }

    private void removeMonthlyRows() {
        for (int i = content.getChildCount() - 1; i >= 0; i--) {
            Object tag = content.getChildAt(i).getTag();
            if ("monthly-rows".equals(tag)) {
                content.removeViewAt(i);
            }
        }
    }

    private EditText input(String hint, int inputType) {
        EditText field = new EditText(this);
        field.setSingleLine((inputType & InputType.TYPE_TEXT_FLAG_MULTI_LINE) == 0);
        field.setHint(hint);
        field.setInputType(inputType);
        field.setTextSize(15);
        field.setTextColor(Color.parseColor("#20302B"));
        field.setHintTextColor(Color.parseColor(MUTED));
        field.setPadding(dp(12), dp(8), dp(12), dp(8));
        field.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                Color.parseColor(LINE)));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, dp(3), 0, dp(3));
        field.setLayoutParams(params);
        return field;
    }

    private TextView text(String value, int size, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(Color.parseColor("#20302B"));
        view.setTypeface(android.graphics.Typeface.create(
                bold ? "sans-serif-medium" : "sans-serif",
                android.graphics.Typeface.NORMAL));
        view.setPadding(dp(6), dp(8), dp(6), dp(8));
        return view;
    }

    private void sectionTitle(String value) {
        TextView title = text(value, 18, true);
        title.setTextColor(Color.parseColor(PAGE_TEXT));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, dp(8), 0, dp(4));
        title.setLayoutParams(params);
        content.addView(title);
    }

    private void pageHeader(String title, String subtitle) {
        TextView heading = text(title, 24, true);
        heading.setTextColor(Color.parseColor(PAGE_TEXT));
        heading.setPadding(dp(4), dp(8), dp(4), dp(2));
        content.addView(heading);
        TextView description = text(subtitle, 13, false);
        description.setTextColor(Color.parseColor(PAGE_MUTED));
        description.setPadding(dp(4), 0, dp(4), dp(10));
        content.addView(description);
    }

    private void addMetricTile(LinearLayout parent, String label, String value, String accent) {
        LinearLayout tile = vertical();
        tile.setPadding(dp(8), dp(3), dp(8), dp(3));
        TextView metricValue = text(value, 17, true);
        metricValue.setTextColor(Color.parseColor(accent));
        TextView metricLabel = text(label, 11, false);
        metricLabel.setTextColor(Color.parseColor(MUTED));
        tile.addView(metricValue);
        tile.addView(metricLabel);
        parent.addView(tile, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
    }

    private void addHint(String value) {
        addHintTo(content, value);
    }

    private void addHintTo(LinearLayout parent, String value) {
        TextView hint = text(value, 13, false);
        hint.setTextColor(Color.parseColor(PAGE_MUTED));
        parent.addView(hint);
    }

    private void addCardHintTo(LinearLayout parent, String value) {
        TextView hint = text(value, 13, false);
        hint.setTextColor(Color.parseColor(MUTED));
        parent.addView(hint);
    }

    private void addCard(String value) { addCardTo(content, value, GREEN); }

    private void addCardTo(LinearLayout parent, String value, String accent) {
        TextView card = text(value, 14, false);
        addCardContent(parent, card, Color.parseColor(accent),
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT));
    }

    private LinearLayout addCardBody(LinearLayout parent, int accentColor) {
        MaterialCardView card = new MaterialCardView(this);
        card.setRadius(dp(12));
        card.setCardElevation(dp(2));
        card.setStrokeWidth(dp(1));
        card.setStrokeColor(getColor(R.color.outline));
        card.setCardBackgroundColor(Color.parseColor(WHITE));
        card.setUseCompatPadding(true);

        LinearLayout frame = row();
        View stripe = new View(this);
        stripe.setBackgroundColor(accentColor);
        frame.addView(stripe, new LinearLayout.LayoutParams(dp(4),
                LinearLayout.LayoutParams.MATCH_PARENT));
        LinearLayout body = vertical();
        body.setPadding(dp(16), dp(12), dp(16), dp(12));
        frame.addView(body, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        card.addView(frame);

        LinearLayout.LayoutParams params = cardParams();
        params.setMargins(0, dp(4), 0, dp(12));
        parent.addView(card, params);
        return body;
    }

    private MaterialCardView addCardContent(
            LinearLayout parent, View child, int accentColor,
            LinearLayout.LayoutParams childParams) {
        MaterialCardView card = new MaterialCardView(this);
        card.setRadius(dp(12));
        card.setCardElevation(dp(2));
        card.setStrokeWidth(dp(1));
        card.setStrokeColor(getColor(R.color.outline));
        card.setCardBackgroundColor(Color.parseColor(WHITE));
        card.setUseCompatPadding(true);

        LinearLayout frame = row();
        View stripe = new View(this);
        stripe.setBackgroundColor(accentColor);
        frame.addView(stripe, new LinearLayout.LayoutParams(dp(4),
                LinearLayout.LayoutParams.MATCH_PARENT));
        LinearLayout body = vertical();
        body.setPadding(dp(10), dp(6), dp(10), dp(6));
        body.addView(child, childParams);
        frame.addView(body, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        card.addView(frame);
        LinearLayout.LayoutParams params = cardParams();
        params.setMargins(0, dp(4), 0, dp(10));
        parent.addView(card, params);
        return card;
    }

    private LinearLayout row() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setGravity(Gravity.CENTER_VERTICAL);
        return layout;
    }

    private LinearLayout vertical() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private void addPrimaryButton(LinearLayout parent, String label, Runnable action) {
        MaterialButton button = primaryButton();
        button.setText(label);
        button.setOnClickListener(view -> action.run());
        parent.addView(button);
    }

    private void addSecondaryButton(LinearLayout parent, String label, Runnable action) {
        MaterialButton button = outlinedButton();
        styleLightOutlinedButton(button);
        button.setText(label);
        button.setOnClickListener(view -> action.run());
        parent.addView(button);
    }

    private void styleLightOutlinedButton(MaterialButton button) {
        button.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                getColor(R.color.surface)));
        button.setTextColor(getColor(R.color.text_primary));
    }

    private MaterialButton outlinedButton() {
        MaterialButton button = new MaterialButton(this, null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle);
        button.setAllCaps(false);
        button.setLetterSpacing(0);
        button.setCornerRadius(dp(12));
        return button;
    }

    private MaterialButton primaryButton() {
        MaterialButton button = new MaterialButton(this);
        button.setCornerRadius(dp(12));
        button.setAllCaps(false);
        button.setLetterSpacing(0);
        return button;
    }

    private MaterialButton miniButton(String label) {
        MaterialButton button = outlinedButton();
        button.setText(label);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setMinHeight(dp(34));
        button.setTextSize(10);
        button.setPadding(dp(2), 0, dp(2), 0);
        button.setInsetTop(0);
        button.setInsetBottom(0);
        return button;
    }

    private void styleCheckBox(CheckBox checkBox) {
        checkBox.setTextColor(Color.parseColor("#20302B"));
        checkBox.setButtonTintList(android.content.res.ColorStateList.valueOf(
                Color.parseColor(GREEN)));
    }

    private void addFieldLabel(LinearLayout parent, String label) {
        TextView view = text(label, 12, true);
        view.setTextColor(Color.parseColor(MUTED));
        view.setPadding(dp(6), dp(6), dp(6), 0);
        parent.addView(view);
    }

    private void makeDatePicker(EditText field, String label, boolean notAfterToday) {
        field.setFocusable(false);
        field.setClickable(true);
        field.setContentDescription(label + ". Tap to choose a date.");
        field.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_tab_today, 0);
        field.setCompoundDrawablePadding(dp(8));
        field.setCompoundDrawableTintList(android.content.res.ColorStateList.valueOf(
                Color.parseColor(GREEN)));
        field.setOnClickListener(view -> {
            LocalDate saved = safeDate(field.getText().toString().trim());
            final LocalDate selected = saved == null ? LocalDate.now() : saved;
            DatePickerDialog picker = new DatePickerDialog(this,
                    (datePicker, year, month, day) -> field.setText(
                            LocalDate.of(year, month + 1, day).toString()),
                    selected.getYear(), selected.getMonthValue() - 1, selected.getDayOfMonth());
            if (notAfterToday) {
                long endOfToday = LocalDate.now().plusDays(1)
                        .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() - 1;
                picker.getDatePicker().setMaxDate(endOfToday);
            }
            picker.show();
        });
    }

    private void makeTimePicker(EditText field, String label) {
        field.setFocusable(false);
        field.setClickable(true);
        field.setContentDescription(label + ". Tap to choose a time.");
        field.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_tab_sessions, 0);
        field.setCompoundDrawablePadding(dp(8));
        field.setCompoundDrawableTintList(android.content.res.ColorStateList.valueOf(
                Color.parseColor(GREEN)));
        field.setOnClickListener(view -> {
            LocalTime saved;
            try {
                saved = LocalTime.parse(field.getText().toString().trim());
            } catch (RuntimeException exception) {
                saved = LocalTime.now().withSecond(0).withNano(0);
            }
            new TimePickerDialog(this, (timePicker, hour, minute) ->
                    field.setText(String.format(Locale.ROOT, "%02d:%02d", hour, minute)),
                    saved.getHour(), saved.getMinute(), DateFormat.is24HourFormat(this)).show();
        });
    }

    private LinearLayout.LayoutParams fixedWidth(int widthDp) {
        return new LinearLayout.LayoutParams(widthDp == 0 ? 0 : dp(widthDp), dp(48));
    }

    private LinearLayout.LayoutParams cardParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, dp(4), 0, dp(6));
        return params;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private LocalDate parseDate(EditText field, String label) {
        try {
            return LocalDate.parse(field.getText().toString().trim());
        } catch (RuntimeException exception) {
            field.setError(label + " must use YYYY-MM-DD");
            return null;
        }
    }

    private LocalDate optionalDate(EditText field, String label) {
        if (field.getText().toString().trim().isEmpty()) return null;
        return parseDate(field, label);
    }

    private LocalTime parseTime(EditText field, String label) {
        try {
            return LocalTime.parse(field.getText().toString().trim());
        } catch (RuntimeException exception) {
            field.setError(label + " must use HH:MM");
            return null;
        }
    }

    private Double parseNumber(EditText field, String label, boolean positive) {
        try {
            double value = Double.parseDouble(field.getText().toString().trim());
            if (!Double.isFinite(value) || (positive ? value <= 0 : value < 0)) {
                field.setError(positive ? "Must be greater than 0" : "Must be 0 or greater");
                return null;
            }
            return value;
        } catch (RuntimeException exception) {
            field.setError("Enter a valid number");
            return null;
        }
    }

    private Integer parseOptionalCount(EditText field, String label, int maximum) {
        String value = field.getText().toString().trim();
        if (value.isEmpty()) return 0;
        try {
            int count = Integer.parseInt(value);
            if (count < 0 || count > maximum) throw new NumberFormatException();
            return count;
        } catch (NumberFormatException exception) {
            field.setError(label + " must be between 0 and " + maximum);
            return null;
        }
    }

    private LocalDate safeDate(String value) {
        try { return LocalDate.parse(value); }
        catch (RuntimeException exception) { return null; }
    }

    private String formatDuration(int seconds) {
        int minutes = Math.max(0, seconds) / 60;
        if (minutes < 60) return minutes + (minutes == 1 ? " min" : " min");
        int hours = minutes / 60;
        int remainder = minutes % 60;
        String result = hours + (hours == 1 ? " hour" : " hours");
        if (remainder > 0) result += " " + remainder + " min";
        return result;
    }

    private String formatClock(int seconds) {
        int safeSeconds = Math.max(seconds, 0);
        return String.format(Locale.getDefault(), "%02d:%02d",
                safeSeconds / 60, safeSeconds % 60);
    }

    private void runStoreAction(IoAction action) {
        try {
            action.run();
        } catch (IOException exception) {
            showError(exception);
        }
    }

    private void showError(Exception exception) {
        Toast.makeText(this, exception.getMessage() == null
                ? "Could not save tracker data." : exception.getMessage(),
                Toast.LENGTH_LONG).show();
    }

    private interface IoAction { void run() throws IOException; }
}
