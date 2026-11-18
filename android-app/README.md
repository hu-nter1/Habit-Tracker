# Native Android app

This is the Java/XML Android version of Day by Day. `MainActivity.java`
provides five focused destinations—Today, Habits, Study, Review, and Learn—while
keeping number goals inside Study and core-habit streaks on Today.
`res/layout/activity_main.xml` defines the app shell,
and Material Components supplies the tab bar, buttons, cards, and progress
indicators. Five equal-width tabs use icons and compact labels. The interface
uses a slowly animated forest-green glow backdrop with drifting light points,
soft botanical details, pale high-contrast cards, and a coral accent. Its
date-and-location header is kept compact. The Study page has an original
sprout illustration that grows as focus time passes. Its calm, progress-led
approach takes general inspiration from focus apps such as Forest while
keeping Day by Day's own colors, artwork, and layout.

## Build and install

With the Android SDK installed, run:

```sh
./android-app/build.sh
adb install -r android-app/build/StudyTracker.apk
adb shell am start -n com.personal.studytracker/.MainActivity
```

The Gradle wrapper can also build directly from `android-app/`. It
uses the local Android SDK selected by `ANDROID_SDK_ROOT` or `ANDROID_HOME`.
The app compiles against Android 35 and Material Components 1.12.0.

## Data

The app persists data in its private app files directory as `tracker.xml`.
The file is available only to the app and can be inspected with Android's
app-data tools or an explicit export.

The XML uses `<tracker version="1">` with `<habits>`, week-grouped
`<daily_checks>`, `<plans>`, `<target_entries>`, `<sessions>`, `<reading_logs>`,
`<reviews>`, and `<topics>`. `<plans>` and `<reading_logs>` are optional when
reading older files; each `<plan>` stores an `id`, `date`, optional `time` and
`end_time`, `text`, and `done` flag. Timed plans require both start and end
times, with the end later on the same day; the start time triggers the
reminder. Plans without either time remain unscheduled tasks. Plans are
separate from habit check-ins and session records.
Each `<topic>` has an `id`, `name`, and `guidance`; its `<item>` children have
an `id`, `text`, and `done` flag. The starter curriculum is inserted when the
`<topics>` section is first created and can be edited in the Topics tab. Dates
are `YYYY-MM-DD`; study durations are stored in seconds.
Study-session topic fields are dropdowns populated from the saved curriculum,
including topics added in Learn; sessions also save the selected topic ID so
renaming a topic does not break its history. Each `<reading>` in
`<reading_logs>` stores its date, topic ID and name, duration in minutes,
optional page count and notes, and log timestamp. The `pages_read` attribute
is optional for older XML files. Reading time contributes to Today, weekly,
monthly, and per-topic study-time totals; page counts are summarized in
Reading history when provided.
Study sessions and reading logs can be edited or deleted from their histories;
deleting an entry also updates the derived study-time totals. Reading history
can be searched by topic or notes and filtered by date range. Session history
can be searched by topic, filtered by date range or interrupted status, and
starts with the 20 most recent matches; older results can be expanded.
The Review chart includes both focus sessions and reading time.

The Today tab brings together today's check-ins, focus time, number goals,
streaks, and a short dated plan. Plans can be made for today or upcoming days;
they are not counted as completed activity. The Habits tab puts the date-based
checklist first and keeps the optional Monday–Sunday overview. The Review tab
shows completed activity only through today, with a weekly chart and month
summaries that exclude future dates. Learn contains an editable curriculum
with a checklist and progress indicator for each topic.

## Focus timer and device context

The Study tab includes a countdown timer (1–720 minutes) with 25- and
50-minute presets and requires selecting a curriculum topic. When it ends, the
app logs the session to the existing `<sessions>` XML section, then plays the
device alarm sound and vibration. You can also save a separate reading log with
date, topic, minutes, and optional notes; it appears in Reading history and
optional pages read; it appears in Reading history and contributes to
study-time totals without pretending it was a timed focus session.
Both histories support editing and confirmed deletion; reading logs include
search and date-range filters.
The alarm notification can be stopped or snoozed for five minutes. Active timer
state is stored separately in app-private preferences; the alarm is restored
after an app/device restart. Android may ask for notification and exact-alarm
access so the timer can alert reliably while the app is closed.

The Today tab also lets you enable a recurring morning planning reminder and
an evening check-in reminder. Each has its own time and selectable system alarm
sound. These settings are kept in app preferences (not in `tracker.xml`) and
are rescheduled after device restart or a time-zone change. Allow notifications
and exact alarms when Android asks so reminders can play at the chosen time.

Timed plans also receive their own notification at the planned time, using the
device's default notification sound. Each activity notification includes
Dismiss and five-minute Snooze actions. Leave a plan's time blank to avoid a
notification. Plans cannot be created for past dates; past target entries and
study sessions remain available as historical records.

The header displays the device's current date/time and, with location permission,
an approximate city/area derived from the device's coarse location. Location is
not written to `tracker.xml`; the system geocoder may use network access to
resolve a place name. Tap the location row to retry or change the permission.
