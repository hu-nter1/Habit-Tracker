package com.personal.studytracker;

import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

@RunWith(AndroidJUnit4.class)
public final class TrackerStoreIntegrationTest {
    private File directory;

    @Before public void createTemporaryStoreDirectory() throws IOException {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        directory = Files.createTempDirectory(
                context.getCacheDir().toPath(), "tracker-store-test-").toFile();
    }

    @After public void removeTemporaryStoreDirectory() throws IOException {
        if (directory == null || !directory.exists()) return;
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (!file.isFile() || !file.delete()) {
                    throw new IOException("Could not remove test file " + file);
                }
            }
        }
        if (!directory.delete()) throw new IOException("Could not remove test directory");
    }

    @Test public void curriculumDefinitionsHaveUniqueNamesAndSteps() {
        Set<String> names = new HashSet<>();
        int stepCount = 0;
        for (Curriculum.Definition definition : Curriculum.all()) {
            assertTrue("Duplicate curriculum topic: " + definition.name,
                    names.add(definition.name));
            assertFalse(definition.name.trim().isEmpty());
            assertFalse(definition.guidance.trim().isEmpty());
            assertFalse(definition.items.isEmpty());
            Set<String> steps = new HashSet<>();
            for (String item : definition.items) {
                assertFalse(item.trim().isEmpty());
                assertTrue("Duplicate step in " + definition.name, steps.add(item));
                stepCount++;
            }
        }
        assertEquals(13, names.size());
        assertEquals(143, stepCount);
    }

    @Test public void storeRoundTripFeedsReviewMetrics() throws IOException {
        TrackerStore store = new TrackerStore(directory);
        LocalDate today = LocalDate.now();
        String day = today.toString();
        String monday = today.with(DayOfWeek.MONDAY).toString();

        assertEquals(13, store.getTopics().size());
        TrackerStore.Topic topic = store.getTopics().get(0);
        TrackerStore.TopicItem item = topic.items.get(0);
        store.setTopicItemDone(topic.id, item.id, true);

        store.addHabit("Test habit", true);
        TrackerStore.Habit habit = store.getHabits().get(0);
        store.setCheck(habit.id, day, true);
        store.addTarget(day, "Test goal", 3, 3, "met");
        store.addPlan(day, "14:00", "14:30", "Test plan");
        store.addSession(day, "09:00", "09:25", 25 * 60,
                topic.id, topic.name, false);
        store.addReadingLog(day, topic.id, 15, 3, "Test reading");
        store.saveReview(monday, "distraction", "shorter sessions", "test note");

        TrackerStore reloaded = new TrackerStore(directory);
        assertEquals(1, reloaded.getHabits().size());
        assertTrue(TrackerMetrics.isDone(reloaded.getChecks(), habit.id, today));
        assertEquals(1, reloaded.getPlans().size());
        assertEquals(1, reloaded.getSessions().size());
        assertEquals(1, reloaded.getReadingLogs().size());
        assertEquals(3, reloaded.getReadingLogs().get(0).pagesRead);
        assertEquals("distraction", reloaded.getReview(monday).blocker);
        assertTrue(reloaded.getTopics().get(0).items.get(0).done);

        TrackerMetrics.Week week = TrackerMetrics.week(
                reloaded, today.with(DayOfWeek.MONDAY));
        assertEquals(1, week.allHabitsDays);
        assertEquals(1, week.longestStreak);
        assertEquals(1.0, week.targetSuccessRate, 0.0);
        assertEquals(40 * 60, week.sessionSeconds);
        TrackerMetrics.CurriculumProgress progress = TrackerMetrics.curriculum(reloaded);
        assertEquals(1, progress.completedItems);
        assertEquals(143, progress.totalItems);
    }

    @Test public void invalidPlansAndFutureCompletionAreRejected() throws IOException {
        TrackerStore store = new TrackerStore(directory);
        try {
            store.addPlan(LocalDate.now().toString(), "14:00", "13:00", "Invalid plan");
            fail("Expected an invalid plan time range to be rejected");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("later"));
        }

        LocalDate tomorrow = LocalDate.now().plusDays(1);
        store.addPlan(tomorrow.toString(), "", "", "Future plan");
        try {
            store.setPlanDone(store.getPlans().get(0).id, true);
            fail("Expected a future plan to remain incomplete");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("future"));
        }
        assertFalse(store.getPlans().get(0).done);
    }

    @Test public void appendingCurriculumPreservesLegacyProgressAndCustomTopics()
            throws IOException {
        String legacyXml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<tracker version=\"1\"><topics curriculum_version=\"1\">"
                + "<topic id=\"legacy-networking\" name=\"Networking\" guidance=\"\">"
                + "<item id=\"legacy-networking-item\" text=\"checked step\" done=\"true\"/>"
                + "</topic>"
                + "<topic id=\"legacy-os\" name=\"Operating Systems\" guidance=\"\">"
                + "<item id=\"legacy-os-item\" text=\"old step\" done=\"false\"/>"
                + "</topic>"
                + "<topic id=\"custom-topic\" name=\"My topic\" guidance=\"custom\">"
                + "<item id=\"custom-item\" text=\"keep me\" done=\"true\"/>"
                + "</topic>"
                + "</topics></tracker>";
        Files.write(new File(directory, "tracker.xml").toPath(),
                legacyXml.getBytes(StandardCharsets.UTF_8));

        TrackerStore store = new TrackerStore(directory);
        assertTrue(store.isCurriculumMigrationPending());
        store.migrateCurriculum(true);

        TrackerStore reloaded = new TrackerStore(directory);
        assertFalse(reloaded.isCurriculumMigrationPending());
        assertEquals(14, reloaded.getTopics().size());
        assertTrue(hasTopic(reloaded, "legacy-networking", "Networking"));
        assertTrue(hasTopic(reloaded, "legacy-os", "Operating Systems"));
        assertTrue(hasTopic(reloaded, "custom-topic", "My topic"));
        assertTrue(reloaded.getTopics().get(0).items.get(0).done);
        assertTrue(reloaded.getTopics().get(2).items.get(0).done);
    }

    private static boolean hasTopic(TrackerStore store, String id, String name) {
        for (TrackerStore.Topic topic : store.getTopics()) {
            if (id.equals(topic.id) && name.equals(topic.name)) return true;
        }
        return false;
    }
}
