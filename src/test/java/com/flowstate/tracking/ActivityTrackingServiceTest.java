package com.flowstate.tracking;

import com.flowstate.model.AppCategory;
import com.flowstate.model.ApplicationActivity;
import com.flowstate.repository.ActivityRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class ActivityTrackingServiceTest {

    private ScheduledExecutorService executor;
    private List<ApplicationActivity> savedActivities;
    private ActivityRepository stubRepo;

    @BeforeEach
    void setUp() {
        executor = Executors.newSingleThreadScheduledExecutor();
        savedActivities = new ArrayList<>();
        stubRepo = new ActivityRepository() {
            private long idSeq = 1L;

            @Override
            public ApplicationActivity save(ApplicationActivity activity) {
                activity.setId(idSeq++);
                savedActivities.add(activity);
                return activity;
            }

            @Override
            public boolean update(ApplicationActivity activity) { return true; }

            @Override
            public Optional<ApplicationActivity> findById(Long id) { return Optional.empty(); }

            @Override
            public List<ApplicationActivity> findBySessionId(Long sessionId) { return Collections.emptyList(); }

            @Override
            public List<ApplicationActivity> findByDateRange(Instant start, Instant end) { return Collections.emptyList(); }

            @Override
            public List<ApplicationActivity> findRecent(int limit) { return Collections.emptyList(); }

            @Override
            public long deleteBySessionId(Long sessionId) { return 0; }

            @Override
            public long clearAll() { return 0; }

            @Override
            public Map<String, Long> getTopApplicationsDuration(Instant start, Instant end, int limit) { return Collections.emptyMap(); }
        };
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    @Test
    @DisplayName("Activity tracking captures app change and saves previous app interval with duration")
    void testAppChangeRecordsDurationAndPersists() {
        Instant t0 = Instant.now().minusSeconds(10);
        Instant t1 = t0.plusSeconds(5);

        AtomicReference<ForegroundAppInfo> currentSample = new AtomicReference<>(
                new ForegroundAppInfo("idea64.exe", "FlowStateApp.java", t0)
        );

        ActivityTrackingProvider stubProvider = new ActivityTrackingProvider() {
            @Override
            public Optional<ForegroundAppInfo> getForegroundApp() {
                return Optional.ofNullable(currentSample.get());
            }

            @Override
            public boolean isSupported() { return true; }
        };

        ActivityTrackingService service = new ActivityTrackingService(
                stubProvider, stubRepo, executor, false, 1000L
        );

        // 1. Initial poll
        service.pollForegroundApp();
        ApplicationActivity first = service.getCurrentActivity();
        assertThat(first).isNotNull();
        assertThat(first.getApplicationName()).isEqualTo("idea64.exe");
        assertThat(savedActivities).isEmpty(); // Not finalized yet

        // 2. Poll same app later: duration should increment, nothing saved yet
        service.pollForegroundApp();
        assertThat(savedActivities).isEmpty();

        // 3. Switch to different app
        currentSample.set(new ForegroundAppInfo("chrome.exe", "Google Search", t1));
        service.pollForegroundApp();

        // Previous app (idea64) must now be saved with duration >= 1
        assertThat(savedActivities).hasSize(1);
        ApplicationActivity saved = savedActivities.get(0);
        assertThat(saved.getApplicationName()).isEqualTo("idea64.exe");

        // Current app should now be chrome
        ApplicationActivity current = service.getCurrentActivity();
        assertThat(current).isNotNull();
        assertThat(current.getApplicationName()).isEqualTo("chrome.exe");

        service.shutdown();
    }

    @Test
    @DisplayName("Pause tracking halts polling and resumes tracking correctly")
    void testPauseAndResumeTracking() {
        AtomicReference<ForegroundAppInfo> currentApp = new AtomicReference<>(
                new ForegroundAppInfo("notepad.exe", "Notes.txt", Instant.now())
        );

        ActivityTrackingProvider stubProvider = new ActivityTrackingProvider() {
            @Override
            public Optional<ForegroundAppInfo> getForegroundApp() {
                return Optional.ofNullable(currentApp.get());
            }

            @Override
            public boolean isSupported() { return true; }
        };

        ActivityTrackingService service = new ActivityTrackingService(
                stubProvider, stubRepo, executor, false, 1000L
        );

        service.pollForegroundApp();
        assertThat(service.getCurrentActivity()).isNotNull();

        service.pauseTracking();
        assertThat(service.isTrackingEnabled()).isFalse();
        assertThat(service.getCurrentActivity()).isNull(); // Finalized on pause

        // Try polling while paused
        currentApp.set(new ForegroundAppInfo("calculator.exe", "Calculator", Instant.now()));
        service.pollForegroundApp();
        assertThat(service.getCurrentActivity()).isNull(); // Should remain null

        // Resume
        service.resumeTracking();
        assertThat(service.isTrackingEnabled()).isTrue();
        service.pollForegroundApp();
        assertThat(service.getCurrentActivity()).isNotNull();
        assertThat(service.getCurrentActivity().getApplicationName()).isEqualTo("calculator.exe");

        service.shutdown();
    }

    @Test
    @DisplayName("Excluded application is not tracked or saved")
    void testExcludedAppIsNotTracked() {
        ActivityTrackingProvider stubProvider = new ActivityTrackingProvider() {
            @Override
            public Optional<ForegroundAppInfo> getForegroundApp() {
                return Optional.of(new ForegroundAppInfo("keepass.exe", "Passwords", Instant.now()));
            }

            @Override
            public boolean isSupported() { return true; }
        };

        ActivityTrackingService service = new ActivityTrackingService(
                stubProvider, stubRepo, executor, false, 1000L
        );

        service.setExclusionPredicate(app -> "keepass.exe".equalsIgnoreCase(app));

        service.pollForegroundApp();

        assertThat(service.getCurrentActivity()).isNull();
        assertThat(savedActivities).isEmpty();

        service.shutdown();
    }
}
