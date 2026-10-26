package com.flowstate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class ActivityTrackerTest {

    @Test
    @DisplayName("ActivityTracker pause and resume transitions update state and notify listeners")
    void testPauseAndResume(@TempDir Path tempDir) {
        ActivityStorage storage = new ActivityStorage(tempDir.resolve("activities.json"));
        DistractionAnalyzer analyzer = new DistractionAnalyzer();
        ActivityTracker tracker = new ActivityTracker(storage, analyzer, Executors.newSingleThreadScheduledExecutor());

        AtomicBoolean notified = new AtomicBoolean(false);
        tracker.addChangeListener(() -> notified.set(true));

        assertThat(tracker.isTrackingEnabled()).isTrue();

        tracker.pauseTracking();
        assertThat(tracker.isTrackingEnabled()).isFalse();
        assertThat(notified.get()).isTrue();

        notified.set(false);
        tracker.resumeTracking();
        assertThat(tracker.isTrackingEnabled()).isTrue();
        assertThat(notified.get()).isTrue();

        tracker.shutdown();
    }
}
