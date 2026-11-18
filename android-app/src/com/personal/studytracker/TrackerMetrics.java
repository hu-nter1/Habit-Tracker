package com.personal.studytracker;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

final class TrackerMetrics {
    static final class Streak {
        int current;
        int longest;
        String lastMissed;
    }

    static final class Week {
        int allHabitsDays;
        int daysConsidered;
        double targetSuccessRate;
        int sessionSeconds;
        int longestStreak;
    }

    static final class CurriculumProgress {
        int completedItems;
        int totalItems;

        double percentage() {
            return totalItems == 0 ? 0 : (double) completedItems / totalItems * 100;
        }
    }

    static Streak streak(TrackerStore store, String habitId, LocalDate today) {
        Streak result = new Streak();
        Map<String, Map<String, Boolean>> checks = store.getChecks();
        LocalDate first = null;
        for (String day : checks.keySet()) {
            LocalDate candidate = LocalDate.parse(day);
            if (first == null || candidate.isBefore(first)) first = candidate;
        }
        if (first == null || first.isAfter(today)) return result;

        LocalDate currentDay = today;
        if (!isDone(checks, habitId, currentDay)) currentDay = today.minusDays(1);
        while (!currentDay.isBefore(first) && isDone(checks, habitId, currentDay)) {
            result.current++;
            currentDay = currentDay.minusDays(1);
        }

        int run = 0;
        for (LocalDate day = first; !day.isAfter(today); day = day.plusDays(1)) {
            if (isDone(checks, habitId, day)) {
                run++;
                result.longest = Math.max(result.longest, run);
            } else {
                run = 0;
                result.lastMissed = day.toString();
            }
        }
        return result;
    }

    static Week week(TrackerStore store, LocalDate monday) {
        Week result = new Week();
        List<TrackerStore.Habit> habits = store.getHabits();
        LocalDate today = LocalDate.now();
        LocalDate lastDay = monday.plusDays(6).isBefore(today) ? monday.plusDays(6) : today;
        result.daysConsidered = monday.isAfter(today) ? 0
                : (int) (lastDay.toEpochDay() - monday.toEpochDay() + 1);
        for (int i = 0; i < result.daysConsidered; i++) {
            LocalDate day = monday.plusDays(i);
            Map<String, Boolean> values = store.getChecks().get(day.toString());
            if (!habits.isEmpty()) {
                boolean all = true;
                for (TrackerStore.Habit habit : habits) {
                    if (values == null || !Boolean.TRUE.equals(values.get(habit.id))) {
                        all = false;
                        break;
                    }
                }
                if (all) result.allHabitsDays++;
            }
        }

        int targetCount = 0;
        int hitCount = 0;
        for (TrackerStore.TargetEntry entry : store.getTargets()) {
            LocalDate day = LocalDate.parse(entry.date);
            if (!day.isBefore(monday) && !day.isAfter(lastDay)) {
                targetCount++;
                if ("true".equals(entry.hit)) hitCount++;
            }
        }
        result.targetSuccessRate = targetCount == 0 ? 0 : (double) hitCount / targetCount;
        for (TrackerStore.Session session : store.getSessions()) {
            LocalDate day = LocalDate.parse(session.date);
            if (!day.isBefore(monday) && !day.isAfter(lastDay)) {
                result.sessionSeconds += session.durationSeconds;
            }
        }
        for (TrackerStore.ReadingLog reading : store.getReadingLogs()) {
            LocalDate day = LocalDate.parse(reading.date);
            if (!day.isBefore(monday) && !day.isAfter(lastDay)) {
                result.sessionSeconds += reading.durationMinutes * 60;
            }
        }
        for (TrackerStore.Habit habit : habits) {
            if (!habit.core) continue;
            int run = 0;
            for (int i = 0; i < result.daysConsidered; i++) {
                if (isDone(store.getChecks(), habit.id, monday.plusDays(i))) {
                    run++;
                    result.longestStreak = Math.max(result.longestStreak, run);
                } else {
                    run = 0;
                }
            }
        }
        return result;
    }

    static boolean isDone(Map<String, Map<String, Boolean>> checks,
                          String habitId, LocalDate day) {
        Map<String, Boolean> values = checks.get(day.toString());
        return values != null && Boolean.TRUE.equals(values.get(habitId));
    }

    static CurriculumProgress curriculum(TrackerStore store) {
        CurriculumProgress progress = new CurriculumProgress();
        for (TrackerStore.Topic topic : store.getTopics()) {
            for (TrackerStore.TopicItem item : topic.items) {
                progress.totalItems++;
                if (item.done) progress.completedItems++;
            }
        }
        return progress;
    }

    private TrackerMetrics() {}
}
