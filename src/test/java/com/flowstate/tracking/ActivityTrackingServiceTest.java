package com.flowstate.tracking;

import com.flowstate.model.AppCategory;
import com.flowstate.model.ApplicationActivity;
import com.flowstate.model.FocusSession;
import com.flowstate.model.SessionCategory;
import com.flowstate.repository.ActivityRepository;
import com.flowstate.service.FocusSessionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
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
    @DisplayName("Initial poll starts tracking active application")
    void testInitialSampleStartsActivity() {
        AtomicReference<ForegroundAppInfo> currentApp = new AtomicReference<>(
                new ForegroundAppInfo("idea64.exe", "FlowState - Project.java", Instant.now())
        );

        ActivityTrackingProvider stubProvider = new ActivityTrackingProvider() {
            @Override
            public Optional<ForegroundAppInfo> getForegroundApp() {
                return Optional.ofNullable(currentApp.get());
            }

            @Override
            public boolean isSupported() {
                return true;
            }
        };

        ActivityTrackingService service = new ActivityTrackingService(
                stubProvider, stubRepo, null, executor, false, 1000L
        );

        service.pollForegroundApp();

        ApplicationActivity active = service.getCurrentActivity();
        assertThat(active).isNotNull();
        assertThat(active.getApplicationName()).isEqualTo("idea64.exe");
        assertThat(active.getWindowTitle()).isEqualTo("FlowState - Project.java");
        assertThat(savedActivities).isEmpty(); // Not saved until window switches or closed
    }

    @Test
    @DisplayName("App switch closes previous activity with duration and persists to repository")
    void testAppSwitchFinalizesPreviousAndStartsNew() {
        Instant t0 = Instant.parse("2026-10-26T12:00:00Z");
        Instant t1 = Instant.parse("2026-10-26T12:05:00Z");

        AtomicReference<ForegroundAppInfo> currentApp = new AtomicReference<>(
                new ForegroundAppInfo("idea64.exe", "Main.java", t0)
        );

        ActivityTrackingProvider stubProvider = new ActivityTrackingProvider() {
            @Override
            public Optional<ForegroundAppInfo> getForegroundApp() {
                return Optional.ofNullable(currentApp.get());
            }

            @Override
            public boolean isSupported() {
                return true;
            }
        };

        ActivityTrackingService service = new ActivityTrackingService(
                stubProvider, stubRepo, null, executor, false, 1000L
        );

        service.pollForegroundApp();

        // Switch to Chrome 5 minutes later
        currentApp.set(new ForegroundAppInfo("chrome.exe", "Google Search", t1));
        service.pollForegroundApp();

        assertThat(savedActivities).hasSize(1);
        ApplicationActivity saved = savedActivities.get(0);
        assertThat(saved.getApplicationName()).isEqualTo("idea64.exe");
        assertThat(saved.getDurationSeconds()).isEqualTo(300L); // 5 minutes

        ApplicationActivity current = service.getCurrentActivity();
        assertThat(current).isNotNull();
        assertThat(current.getApplicationName()).isEqualTo("chrome.exe");
    }

    @Test
    @DisplayName("Active session ID is associated with logged application activities")
    void testSessionIdAssociatedWhenActive() {
        FocusSessionService sessionService = new FocusSessionService(Executors.newSingleThreadScheduledExecutor());
        FocusSession session = sessionService.startSession("Test Coding", SessionCategory.CODING, 1500);
        session.setId(99L);

        ActivityTrackingProvider stubProvider = new ActivityTrackingProvider() {
            @Override
            public Optional<ForegroundAppInfo> getForegroundApp() {
                return Optional.of(new ForegroundAppInfo("code.exe", "Workspace", Instant.now()));
            }

            @Override
            public boolean isSupported() { return true; }
        };

        ActivityTrackingService service = new ActivityTrackingService(
                stubProvider, stubRepo, sessionService, executor, false, 1000L
        );

        service.pollForegroundApp();

        ApplicationActivity active = service.getCurrentActivity();
        assertThat(active).isNotNull();
        assertThat(active.getSessionId()).isEqualTo(99L);

        sessionService.shutdown();
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
                stubProvider, stubRepo, null, executor, false, 1000L
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
                stubProvider, stubRepo, null, executor, false, 1000L
        );

        service.setExclusionPredicate(app -> "keepass.exe".equalsIgnoreCase(app));

        service.pollForegroundApp();

        assertThat(service.getCurrentActivity()).isNull();
        assertThat(savedActivities).isEmpty();
    }
}
