package com.personal.studytracker;

import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlSerializer;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** XML schema: tracker/{habits,daily_checks,plans,target_entries,sessions,reading_logs,reviews,topics}. */
final class TrackerStore {
    static final class Habit {
        final String id;
        String name;
        boolean core;
        Habit(String id, String name, boolean core) {
            this.id = id;
            this.name = name;
            this.core = core;
        }
    }

    static final class TargetEntry {
        String id;
        String date, habit, target, actual, hit, notes;
    }

    static final class PlanItem {
        final String id;
        final String date;
        String time;
        String endTime;
        String text;
        boolean done;

        PlanItem(String id, String date, String time, String endTime,
                 String text, boolean done) {
            this.id = id;
            this.date = date;
            this.time = time;
            this.endTime = endTime;
            this.text = text;
            this.done = done;
        }
    }

    static final class Session {
        String id;
        String date, start, end, topicId, topic, interrupted;
        int durationSeconds;
    }

    static final class ReadingLog {
        String id;
        String date, topicId, topic, notes;
        int durationMinutes, pagesRead;
        long loggedAtMillis;
    }

    static final class Review {
        String blocker = "", change = "", notes = "";
    }

    static final class Topic {
        final String id;
        String name;
        String guidance;
        final List<TopicItem> items = new ArrayList<>();

        Topic(String id, String name, String guidance) {
            this.id = id;
            this.name = name;
            this.guidance = guidance;
        }
    }

    static final class TopicItem {
        final String id;
        String text;
        boolean done;

        TopicItem(String id, String text, boolean done) {
            this.id = id;
            this.text = text;
            this.done = done;
        }
    }

    private final File file;
    private final List<Habit> habits = new ArrayList<>();
    private final Map<String, Map<String, Boolean>> checks = new LinkedHashMap<>();
    private final List<PlanItem> plans = new ArrayList<>();
    private final List<TargetEntry> targets = new ArrayList<>();
    private final List<Session> sessions = new ArrayList<>();
    private final List<ReadingLog> readingLogs = new ArrayList<>();
    private final Map<String, Review> reviews = new LinkedHashMap<>();
    private final List<Topic> topics = new ArrayList<>();
    private boolean topicsSectionFound;
    private int topicsCurriculumVersion;
    private boolean curriculumMigrationPending;

    TrackerStore(File filesDir) throws IOException {
        file = new File(filesDir, "tracker.xml");
        if (file.exists()) {
            load();
            if (!topicsSectionFound) {
                seedTopics();
                save();
            } else {
                prepareCurriculumMigration();
            }
        } else {
            seedTopics();
            topicsCurriculumVersion = Curriculum.VERSION;
            save();
        }
    }

    File getFile() { return file; }
    List<Habit> getHabits() { return Collections.unmodifiableList(habits); }
    Map<String, Map<String, Boolean>> getChecks() { return checks; }
    List<TargetEntry> getTargets() { return Collections.unmodifiableList(targets); }
    List<PlanItem> getPlans() { return Collections.unmodifiableList(plans); }
    List<Session> getSessions() { return Collections.unmodifiableList(sessions); }
    List<ReadingLog> getReadingLogs() { return Collections.unmodifiableList(readingLogs); }
    List<Topic> getTopics() { return Collections.unmodifiableList(topics); }
    boolean isCurriculumMigrationPending() { return curriculumMigrationPending; }

    void migrateCurriculum(boolean append) throws IOException {
        if (!curriculumMigrationPending) return;
        if (append) {
            List<Topic> previousTopics = new ArrayList<>(topics);
            topics.clear();
            topics.addAll(previousTopics);
            for (Curriculum.Definition definition : Curriculum.all()) {
                if (findExistingTopic(previousTopics, definition.name) != null) continue;
                Topic topic = new Topic(UUID.randomUUID().toString(),
                        definition.name, definition.guidance);
                for (String itemText : definition.items) {
                    topic.items.add(new TopicItem(UUID.randomUUID().toString(), itemText, false));
                }
                topics.add(topic);
            }
        } else {
            replaceAllTopicsWithCurriculum();
        }
        curriculumMigrationPending = false;
        topicsCurriculumVersion = Curriculum.VERSION;
        save();
    }

    Review getReview(String weekStart) {
        Review review = reviews.get(weekStart);
        return review == null ? new Review() : review;
    }

    void addHabit(String name, boolean core) throws IOException {
        habits.add(new Habit(UUID.randomUUID().toString(), name.trim(), core));
        save();
    }

    void updateHabit(String id, String name, boolean core) throws IOException {
        Habit habit = findHabit(id);
        habit.name = name.trim();
        habit.core = core;
        save();
    }

    void deleteHabit(String id) throws IOException {
        Habit habit = findHabit(id);
        habits.remove(habit);
        for (Map<String, Boolean> day : checks.values()) day.remove(id);
        save();
    }

    void setCheck(String habitId, String day, boolean done) throws IOException {
        findHabit(habitId);
        checks.computeIfAbsent(day, key -> new LinkedHashMap<>()).put(habitId, done);
        save();
    }

    void addPlan(String day, String time, String endTime, String text) throws IOException {
        requireText(text, "Plan");
        requirePlanDay(day);
        String[] times = validatePlanTimes(time, endTime);
        plans.add(new PlanItem(UUID.randomUUID().toString(), day, times[0], times[1],
                text.trim(), false));
        save();
    }

    void updatePlan(String id, String time, String endTime, String text) throws IOException {
        requireText(text, "Plan");
        PlanItem plan = findPlan(id);
        requirePlanDay(plan.date);
        String[] times = validatePlanTimes(time, endTime);
        plan.time = times[0];
        plan.endTime = times[1];
        plan.text = text.trim();
        save();
    }

    void setPlanDone(String id, boolean done) throws IOException {
        PlanItem plan = findPlan(id);
        LocalDate day = requirePlanDay(plan.date);
        if (done && day.isAfter(LocalDate.now())) {
            throw new IOException("A future plan cannot be marked complete yet.");
        }
        plan.done = done;
        save();
    }

    void deletePlan(String id) throws IOException {
        plans.remove(findPlan(id));
        save();
    }

    void addTarget(String day, String name, double target, double actual, String notes)
            throws IOException {
        TargetEntry entry = new TargetEntry();
        entry.id = UUID.randomUUID().toString();
        entry.date = day;
        entry.habit = name.trim();
        entry.target = number(target);
        entry.actual = number(actual);
        entry.hit = Boolean.toString(actual >= target);
        entry.notes = notes.trim();
        targets.add(entry);
        save();
    }

    void addSession(String day, String start, String end, int seconds,
                    String topic, boolean interrupted) throws IOException {
        addSession(day, start, end, seconds, "", topic, interrupted);
    }

    void addSession(String day, String start, String end, int seconds,
                    String topicId, String topic, boolean interrupted) throws IOException {
        Session session = new Session();
        session.id = UUID.randomUUID().toString();
        session.date = day;
        session.start = start;
        session.end = end;
        session.durationSeconds = seconds;
        session.topicId = topicId == null ? "" : topicId;
        session.topic = topic.trim();
        session.interrupted = Boolean.toString(interrupted);
        sessions.add(session);
        save();
    }

    void updateSession(String id, String day, String start, String end,
                       String topicId, boolean interrupted) throws IOException {
        LocalDate date;
        try {
            date = LocalDate.parse(day);
        } catch (DateTimeParseException exception) {
            throw new IOException("Choose a valid session date.", exception);
        }
        if (date.isAfter(LocalDate.now())) {
            throw new IOException("Sessions can only be logged for today or an earlier date.");
        }
        LocalTime startTime;
        LocalTime endTime;
        try {
            startTime = LocalTime.parse(start);
            endTime = LocalTime.parse(end);
        } catch (DateTimeParseException exception) {
            throw new IOException("Enter valid session start and end times.", exception);
        }
        if (startTime.equals(endTime)) {
            throw new IOException("Session end time must differ from its start time.");
        }
        int seconds = endTime.toSecondOfDay() - startTime.toSecondOfDay();
        if (seconds < 0) seconds += 24 * 60 * 60;
        Topic topic = findTopic(topicId);
        Session session = findSession(id);
        session.date = date.toString();
        session.start = startTime.toString();
        session.end = endTime.toString();
        session.durationSeconds = seconds;
        session.topicId = topic.id;
        session.topic = topic.name;
        session.interrupted = Boolean.toString(interrupted);
        save();
    }

    void deleteSession(String id) throws IOException {
        sessions.remove(findSession(id));
        save();
    }

    void addReadingLog(String day, String topicId, int durationMinutes,
                       int pagesRead, String notes)
            throws IOException {
        LocalDate date;
        try {
            date = LocalDate.parse(day);
        } catch (DateTimeParseException exception) {
            throw new IOException("Choose a valid reading date.", exception);
        }
        if (date.isAfter(LocalDate.now())) {
            throw new IOException("Reading can only be logged for today or an earlier date.");
        }
        if (durationMinutes < 1 || durationMinutes > 720) {
            throw new IOException("Reading time must be between 1 and 720 minutes.");
        }
        validatePagesRead(pagesRead);
        Topic topic = findTopic(topicId);
        ReadingLog reading = new ReadingLog();
        reading.id = UUID.randomUUID().toString();
        reading.date = date.toString();
        reading.topicId = topic.id;
        reading.topic = topic.name;
        reading.durationMinutes = durationMinutes;
        reading.pagesRead = pagesRead;
        reading.notes = notes == null ? "" : notes.trim();
        reading.loggedAtMillis = System.currentTimeMillis();
        readingLogs.add(reading);
        save();
    }

    void updateReadingLog(String id, String day, String topicId,
                          int durationMinutes, int pagesRead, String notes) throws IOException {
        LocalDate date;
        try {
            date = LocalDate.parse(day);
        } catch (DateTimeParseException exception) {
            throw new IOException("Choose a valid reading date.", exception);
        }
        if (date.isAfter(LocalDate.now())) {
            throw new IOException("Reading can only be logged for today or an earlier date.");
        }
        if (durationMinutes < 1 || durationMinutes > 720) {
            throw new IOException("Reading time must be between 1 and 720 minutes.");
        }
        validatePagesRead(pagesRead);
        Topic topic = findTopic(topicId);
        ReadingLog reading = findReadingLog(id);
        reading.date = date.toString();
        reading.topicId = topic.id;
        reading.topic = topic.name;
        reading.durationMinutes = durationMinutes;
        reading.pagesRead = pagesRead;
        reading.notes = notes == null ? "" : notes.trim();
        save();
    }

    private static void validatePagesRead(int pagesRead) throws IOException {
        if (pagesRead < 0 || pagesRead > 10000) {
            throw new IOException("Pages read must be between 0 and 10,000.");
        }
    }

    void deleteReadingLog(String id) throws IOException {
        readingLogs.remove(findReadingLog(id));
        save();
    }

    void saveReview(String weekStart, String blocker, String change, String notes)
            throws IOException {
        Review review = new Review();
        review.blocker = blocker;
        review.change = change;
        review.notes = notes;
        reviews.put(weekStart, review);
        save();
    }

    void addTopic(String name, String guidance) throws IOException {
        requireText(name, "Topic name");
        topics.add(new Topic(UUID.randomUUID().toString(), name.trim(), guidance.trim()));
        save();
    }

    void updateTopic(String id, String name, String guidance) throws IOException {
        requireText(name, "Topic name");
        Topic topic = findTopic(id);
        topic.name = name.trim();
        topic.guidance = guidance.trim();
        save();
    }

    void deleteTopic(String id) throws IOException {
        topics.remove(findTopic(id));
        save();
    }

    void addTopicItem(String topicId, String text) throws IOException {
        requireText(text, "Sub-item");
        findTopic(topicId).items.add(new TopicItem(UUID.randomUUID().toString(), text.trim(), false));
        save();
    }

    void updateTopicItem(String topicId, String itemId, String text) throws IOException {
        requireText(text, "Sub-item");
        findTopicItem(topicId, itemId).text = text.trim();
        save();
    }

    void setTopicItemDone(String topicId, String itemId, boolean done) throws IOException {
        findTopicItem(topicId, itemId).done = done;
        save();
    }

    void deleteTopicItem(String topicId, String itemId) throws IOException {
        Topic topic = findTopic(topicId);
        TopicItem item = findTopicItem(topicId, itemId);
        topic.items.remove(item);
        save();
    }

    private Habit findHabit(String id) throws IOException {
        for (Habit habit : habits) if (habit.id.equals(id)) return habit;
        throw new IOException("That habit no longer exists.");
    }

    private PlanItem findPlan(String id) throws IOException {
        for (PlanItem plan : plans) if (plan.id.equals(id)) return plan;
        throw new IOException("That plan no longer exists.");
    }

    private Topic findTopic(String id) throws IOException {
        for (Topic topic : topics) if (topic.id.equals(id)) return topic;
        throw new IOException("That topic no longer exists.");
    }

    private TopicItem findTopicItem(String topicId, String itemId) throws IOException {
        for (TopicItem item : findTopic(topicId).items) {
            if (item.id.equals(itemId)) return item;
        }
        throw new IOException("That curriculum item no longer exists.");
    }

    private Session findSession(String id) throws IOException {
        for (Session session : sessions) if (session.id.equals(id)) return session;
        throw new IOException("That study session no longer exists.");
    }

    private ReadingLog findReadingLog(String id) throws IOException {
        for (ReadingLog reading : readingLogs) if (reading.id.equals(id)) return reading;
        throw new IOException("That reading log no longer exists.");
    }

    private static void requireText(String value, String label) throws IOException {
        if (value == null || value.trim().isEmpty()) {
            throw new IOException(label + " is required.");
        }
    }

    private static String[] validatePlanTimes(String start, String end) throws IOException {
        String startValue = start == null ? "" : start.trim();
        String endValue = end == null ? "" : end.trim();
        if (startValue.isEmpty() != endValue.isEmpty()) {
            throw new IOException("Enter both a start and end time, or leave both blank.");
        }
        if (startValue.isEmpty()) return new String[]{"", ""};
        try {
            LocalTime startTime = LocalTime.parse(startValue);
            LocalTime endTime = LocalTime.parse(endValue);
            if (!endTime.isAfter(startTime)) {
                throw new IOException("End time must be later than start time.");
            }
            return new String[]{startTime.toString(), endTime.toString()};
        } catch (DateTimeParseException exception) {
            throw new IOException("Enter valid plan times.", exception);
        }
    }

    private static LocalDate requirePlanDay(String value) throws IOException {
        final LocalDate day;
        try {
            day = LocalDate.parse(value);
        } catch (DateTimeParseException exception) {
            throw new IOException("Choose a valid date for the plan.", exception);
        }
        if (day.isBefore(LocalDate.now())) {
            throw new IOException("Plans can only be saved for today or a future day.");
        }
        return day;
    }

    private void seedTopics() {
        topicsCurriculumVersion = Curriculum.VERSION;
        addCurriculumTopics(new ArrayList<>(topics), null);
    }

    private void prepareCurriculumMigration() throws IOException {
        if (topicsCurriculumVersion >= Curriculum.VERSION) return;
        if (!hasLegacyStarterTopics()) {
            topicsCurriculumVersion = Curriculum.VERSION;
            save();
            return;
        }
        for (Topic topic : topics) {
            for (TopicItem item : topic.items) {
                if (item.done) {
                    curriculumMigrationPending = true;
                    return;
                }
            }
        }
        replaceStarterTopicsWithCurriculum();
        topicsCurriculumVersion = Curriculum.VERSION;
        save();
    }

    private boolean hasLegacyStarterTopics() {
        int legacyTopicCount = 0;
        for (Topic topic : topics) {
            if (isLegacyStarterName(topic.name)) legacyTopicCount++;
        }
        return legacyTopicCount >= 2;
    }

    private static boolean isLegacyStarterName(String name) {
        return "Networking".equals(name) || "Operating Systems".equals(name)
                || "Identity".equals(name) || "Applications".equals(name)
                || "Cloud Basics".equals(name) || "Scripting".equals(name)
                || "Explore Roles".equals(name) || "Build Evidence".equals(name)
                || "Apply for Jobs".equals(name);
    }

    private void replaceStarterTopicsWithCurriculum() {
        List<Topic> previousTopics = new ArrayList<>(topics);
        List<Topic> customTopics = new ArrayList<>();
        for (Topic topic : topics) {
            if (!isLegacyStarterName(topic.name)) customTopics.add(topic);
        }
        topics.clear();
        addCurriculumTopics(previousTopics, customTopics);
    }

    private void replaceAllTopicsWithCurriculum() {
        List<Topic> previousTopics = new ArrayList<>(topics);
        topics.clear();
        addCurriculumTopics(previousTopics, null);
    }

    private void addCurriculumTopics(
            List<Topic> previousTopics, List<Topic> topicsToPreserve) {
        for (Curriculum.Definition definition : Curriculum.all()) {
            Topic existing = findExistingTopic(previousTopics, definition.name);
            Topic topic = new Topic(existing == null ? UUID.randomUUID().toString() : existing.id,
                    definition.name, definition.guidance);
            for (String itemText : definition.items) {
                topic.items.add(new TopicItem(UUID.randomUUID().toString(), itemText, false));
            }
            topics.add(topic);
        }
        if (topicsToPreserve != null) topics.addAll(topicsToPreserve);
    }

    private static Topic findExistingTopic(List<Topic> previousTopics, String newName) {
        String oldName = newName;
        if ("OS (Windows-focused)".equals(newName)) oldName = "Operating Systems";
        else if ("Cloud".equals(newName)) oldName = "Cloud Basics";
        else if ("Choose a Domain".equals(newName)) oldName = "Explore Roles";
        else if ("Labs".equals(newName)) oldName = "Build Evidence";
        for (Topic topic : previousTopics) {
            if (oldName.equals(topic.name)) return topic;
        }
        return null;
    }

    private static String number(double value) {
        if (value == (long) value) return Long.toString((long) value);
        return Double.toString(value);
    }

    private void load() throws IOException {
        try (FileInputStream input = new FileInputStream(file)) {
            XmlPullParser parser = Xml.newPullParser();
            parser.setInput(input, "UTF-8");
            int event;
            String section = "";
            String day = "";
            String currentReviewWeek = "";
            while ((event = parser.next()) != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG) {
                    String tag = parser.getName();
                    if ("habits".equals(tag) || "daily_checks".equals(tag)
                            || "plans".equals(tag) || "target_entries".equals(tag) || "sessions".equals(tag)
                            || "reading_logs".equals(tag) || "reviews".equals(tag)
                            || "topics".equals(tag)) {
                        section = tag;
                        if ("topics".equals(tag)) {
                            topicsSectionFound = true;
                            String version = attr(parser, "curriculum_version");
                            if (!version.isEmpty()) {
                                try {
                                    topicsCurriculumVersion = Integer.parseInt(version);
                                } catch (NumberFormatException exception) {
                                    throw new IOException(
                                            "Curriculum version in tracker XML is invalid.", exception);
                                }
                            }
                        }
                    } else if ("day".equals(tag)) {
                        day = parser.getAttributeValue(null, "date");
                    } else if ("topic".equals(tag) && "topics".equals(section)) {
                        topics.add(new Topic(attr(parser, "id"), attr(parser, "name"),
                                attr(parser, "guidance")));
                    } else if ("item".equals(tag) && "topics".equals(section)) {
                        if (topics.isEmpty()) {
                            throw new IOException("Curriculum item has no topic in tracker XML.");
                        }
                        topics.get(topics.size() - 1).items.add(new TopicItem(
                                attr(parser, "id"), attr(parser, "text"),
                                Boolean.parseBoolean(attr(parser, "done"))));
                    } else if ("habit".equals(tag) && "habits".equals(section)) {
                        habits.add(new Habit(attr(parser, "id"), attr(parser, "name"),
                                Boolean.parseBoolean(attr(parser, "core"))));
                    } else if ("check".equals(tag) && "daily_checks".equals(section)) {
                        String habitId = attr(parser, "habit_id");
                        checks.computeIfAbsent(day, key -> new LinkedHashMap<>())
                                .put(habitId, Boolean.parseBoolean(attr(parser, "done")));
                    } else if ("plan".equals(tag) && "plans".equals(section)) {
                        plans.add(new PlanItem(attr(parser, "id"), attr(parser, "date"),
                                attr(parser, "time"), attr(parser, "end_time"), attr(parser, "text"),
                                Boolean.parseBoolean(attr(parser, "done"))));
                    } else if ("entry".equals(tag) && "target_entries".equals(section)) {
                        TargetEntry entry = new TargetEntry();
                        entry.id = attr(parser, "id");
                        entry.date = attr(parser, "date");
                        entry.habit = attr(parser, "habit");
                        entry.target = attr(parser, "target");
                        entry.actual = attr(parser, "actual");
                        entry.hit = attr(parser, "hit");
                        entry.notes = attr(parser, "notes");
                        targets.add(entry);
                    } else if ("session".equals(tag) && "sessions".equals(section)) {
                        Session session = new Session();
                        session.id = attr(parser, "id");
                        session.date = attr(parser, "date");
                        session.start = attr(parser, "start");
                        session.end = attr(parser, "end");
                        session.topicId = attr(parser, "topic_id");
                        session.topic = attr(parser, "topic");
                        session.interrupted = attr(parser, "interrupted");
                        session.durationSeconds = parseNonNegativeInt(
                                attr(parser, "duration_seconds"));
                        sessions.add(session);
                    } else if ("reading".equals(tag) && "reading_logs".equals(section)) {
                        ReadingLog reading = new ReadingLog();
                        reading.id = attr(parser, "id");
                        reading.date = attr(parser, "date");
                        reading.topicId = attr(parser, "topic_id");
                        reading.topic = attr(parser, "topic");
                        reading.durationMinutes = parseNonNegativeInt(
                                attr(parser, "duration_minutes"));
                        reading.pagesRead = parseOptionalNonNegativeInt(
                                attr(parser, "pages_read"));
                        reading.notes = attr(parser, "notes");
                        reading.loggedAtMillis = parseNonNegativeLong(
                                attr(parser, "logged_at_millis"));
                        readingLogs.add(reading);
                    } else if ("review".equals(tag) && "reviews".equals(section)) {
                        currentReviewWeek = attr(parser, "week_start");
                        Review review = new Review();
                        review.blocker = attr(parser, "blocker");
                        review.change = attr(parser, "change");
                        review.notes = attr(parser, "notes");
                        reviews.put(currentReviewWeek, review);
                    }
                } else if (event == XmlPullParser.END_TAG) {
                    String tag = parser.getName();
                    if ("day".equals(tag)) day = "";
                    if ("review".equals(tag)) {
                        currentReviewWeek = "";
                    }
                    if ("habits".equals(tag) || "daily_checks".equals(tag)
                            || "plans".equals(tag) || "target_entries".equals(tag) || "sessions".equals(tag)
                            || "reading_logs".equals(tag) || "reviews".equals(tag)
                            || "topics".equals(tag)) section = "";
                }
            }
        } catch (Exception exception) {
            throw new IOException("Could not read tracker XML: " + exception.getMessage(), exception);
        }
    }

    private void save() throws IOException {
        File temporary = new File(file.getParentFile(), "tracker.xml.tmp");
        try (FileOutputStream output = new FileOutputStream(temporary)) {
            XmlSerializer xml = Xml.newSerializer();
            xml.setOutput(output, "UTF-8");
            xml.startDocument("UTF-8", true);
            xml.startTag(null, "tracker");
            xml.attribute(null, "version", "1");

            xml.startTag(null, "habits");
            for (Habit habit : habits) {
                xml.startTag(null, "habit");
                xml.attribute(null, "id", habit.id);
                xml.attribute(null, "name", habit.name);
                xml.attribute(null, "core", Boolean.toString(habit.core));
                xml.endTag(null, "habit");
            }
            xml.endTag(null, "habits");

            xml.startTag(null, "daily_checks");
            List<String> days = new ArrayList<>(checks.keySet());
            Collections.sort(days);
            Map<String, String> weeks = new HashMap<>();
            for (String day : days) {
                String monday = LocalDate.parse(day).with(
                        java.time.DayOfWeek.MONDAY).toString();
                if (!monday.equals(weeks.get("current"))) {
                    if (weeks.containsKey("current")) xml.endTag(null, "week");
                    xml.startTag(null, "week");
                    xml.attribute(null, "start", monday);
                    weeks.put("current", monday);
                }
                xml.startTag(null, "day");
                xml.attribute(null, "date", day);
                Map<String, Boolean> dayChecks = checks.get(day);
                for (String habitId : dayChecks.keySet()) {
                    xml.startTag(null, "check");
                    xml.attribute(null, "habit_id", habitId);
                    xml.attribute(null, "done", Boolean.toString(dayChecks.get(habitId)));
                    xml.endTag(null, "check");
                }
                xml.endTag(null, "day");
            }
            if (weeks.containsKey("current")) xml.endTag(null, "week");
            xml.endTag(null, "daily_checks");

            xml.startTag(null, "plans");
            for (PlanItem plan : plans) {
                xml.startTag(null, "plan");
                xml.attribute(null, "id", plan.id);
                xml.attribute(null, "date", plan.date);
                xml.attribute(null, "time", plan.time);
                xml.attribute(null, "end_time", plan.endTime);
                xml.attribute(null, "text", plan.text);
                xml.attribute(null, "done", Boolean.toString(plan.done));
                xml.endTag(null, "plan");
            }
            xml.endTag(null, "plans");

            xml.startTag(null, "target_entries");
            for (TargetEntry entry : targets) {
                xml.startTag(null, "entry");
                xml.attribute(null, "id", entry.id);
                xml.attribute(null, "date", entry.date);
                xml.attribute(null, "habit", entry.habit);
                xml.attribute(null, "target", entry.target);
                xml.attribute(null, "actual", entry.actual);
                xml.attribute(null, "hit", entry.hit);
                xml.attribute(null, "notes", entry.notes);
                xml.endTag(null, "entry");
            }
            xml.endTag(null, "target_entries");

            xml.startTag(null, "sessions");
            for (Session session : sessions) {
                xml.startTag(null, "session");
                xml.attribute(null, "id", session.id);
                xml.attribute(null, "date", session.date);
                xml.attribute(null, "start", session.start);
                xml.attribute(null, "end", session.end);
                xml.attribute(null, "duration_seconds",
                        Integer.toString(session.durationSeconds));
                xml.attribute(null, "topic_id", session.topicId);
                xml.attribute(null, "topic", session.topic);
                xml.attribute(null, "interrupted", session.interrupted);
                xml.endTag(null, "session");
            }
            xml.endTag(null, "sessions");

            xml.startTag(null, "reading_logs");
            for (ReadingLog reading : readingLogs) {
                xml.startTag(null, "reading");
                xml.attribute(null, "id", reading.id);
                xml.attribute(null, "date", reading.date);
                xml.attribute(null, "topic_id", reading.topicId);
                xml.attribute(null, "topic", reading.topic);
                xml.attribute(null, "duration_minutes",
                        Integer.toString(reading.durationMinutes));
                xml.attribute(null, "pages_read", Integer.toString(reading.pagesRead));
                xml.attribute(null, "notes", reading.notes);
                xml.attribute(null, "logged_at_millis",
                        Long.toString(reading.loggedAtMillis));
                xml.endTag(null, "reading");
            }
            xml.endTag(null, "reading_logs");

            xml.startTag(null, "reviews");
            for (String weekStart : reviews.keySet()) {
                Review review = reviews.get(weekStart);
                xml.startTag(null, "review");
                xml.attribute(null, "week_start", weekStart);
                xml.attribute(null, "blocker", review.blocker);
                xml.attribute(null, "change", review.change);
                xml.attribute(null, "notes", review.notes);
                xml.endTag(null, "review");
            }
            xml.endTag(null, "reviews");

            xml.startTag(null, "topics");
            xml.attribute(null, "curriculum_version",
                    Integer.toString(topicsCurriculumVersion));
            for (Topic topic : topics) {
                xml.startTag(null, "topic");
                xml.attribute(null, "id", topic.id);
                xml.attribute(null, "name", topic.name);
                xml.attribute(null, "guidance", topic.guidance);
                for (TopicItem item : topic.items) {
                    xml.startTag(null, "item");
                    xml.attribute(null, "id", item.id);
                    xml.attribute(null, "text", item.text);
                    xml.attribute(null, "done", Boolean.toString(item.done));
                    xml.endTag(null, "item");
                }
                xml.endTag(null, "topic");
            }
            xml.endTag(null, "topics");
            xml.endTag(null, "tracker");
            xml.endDocument();
            xml.flush();
            output.getFD().sync();
        } catch (Exception exception) {
            temporary.delete();
            throw new IOException("Could not save tracker XML: " + exception.getMessage(), exception);
        }
        try {
            Files.move(temporary.toPath(), file.toPath(),
                    StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception exception) {
            if (!temporary.renameTo(file)) {
                temporary.delete();
                throw new IOException("Could not replace the tracker XML file.", exception);
            }
        }
    }

    private static String attr(XmlPullParser parser, String key) {
        String value = parser.getAttributeValue(null, key);
        return value == null ? "" : value;
    }

    private static int parseNonNegativeInt(String value) throws IOException {
        try {
            int number = Integer.parseInt(value);
            if (number < 0) throw new NumberFormatException();
            return number;
        } catch (NumberFormatException exception) {
            throw new IOException("Invalid session duration in tracker XML.");
        }
    }

    private static int parseOptionalNonNegativeInt(String value) throws IOException {
        if (value == null || value.isEmpty()) return 0;
        return parseNonNegativeInt(value);
    }

    private static long parseNonNegativeLong(String value) throws IOException {
        if (value == null || value.isEmpty()) return 0L;
        try {
            long number = Long.parseLong(value);
            if (number < 0) throw new NumberFormatException();
            return number;
        } catch (NumberFormatException exception) {
            throw new IOException("Invalid reading log timestamp in tracker XML.");
        }
    }
}
