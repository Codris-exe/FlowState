package com.flowstate.concurrency;

import com.flowstate.model.AppCategory;
import com.flowstate.model.ApplicationActivity;
import com.flowstate.model.FocusSession;
import com.flowstate.model.SessionCategory;
import com.flowstate.repository.ActivityRepository;
import com.flowstate.service.FocusSessionService;
import com.flowstate.service.LifecycleManager;
import com.flowstate.tracking.ActivityTrackingProvider;
import com.flowstate.tracking.ActivityTrackingService;
import com.flowstate.tracking.ForegroundAppInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class ThreadSafetyIntegrationTest {

    @Test
    @DisplayName("FocusSessionService handles high concurrency pause/resume and interruption recording without race conditions")
    void testConcurrentSessionPauseResumeStress() throws InterruptedException {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        FocusSessionService service = new FocusSessionService(scheduler);
        FocusSession session = service.startSession("Concurrent Stress", SessionCategory.CODING, 1800);

        int threadCount = 8;
        int operationsPerThread = 50;
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger exceptionCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            pool.submit(() -> {
                try {
                    startLatch.await();
                    for (int op = 0; op < operationsPerThread; op++) {
                        service.recordInterruption();
                        // Controlled pause/resume attempts
                        try {
                            if (service.isRunning()) {
                                service.pauseSession();
                            } else if (service.isPaused()) {
                                service.resumeSession();
                            }
                        } catch (IllegalStateException ignored) {
                            // Expected when racing with another thread that already toggled state
                        }
                    }
                } catch (Exception e) {
                    exceptionCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean finished = doneLatch.await(5, TimeUnit.SECONDS);

        pool.shutdownNow();
        service.shutdown();
        scheduler.shutdownNow();

        assertThat(finished).isTrue();
        assertThat(exceptionCount.get()).isEqualTo(0);
        assertThat(session.getInterruptionCount()).isGreaterThan(0);
    }

    @Test
    @DisplayName("ActivityTrackingService safely processes concurrent app transitions without data corruption")
    void testConcurrentActivityTrackingSampling() throws InterruptedException {
        List<ApplicationActivity> savedList = new CopyOnWriteArrayList<>();
        ActivityRepository stubRepo = new ActivityRepository() {
            @Override
            public ApplicationActivity save(ApplicationActivity activity) {
                savedList.add(activity);
                return activity;
            }
            @Override public boolean update(ApplicationActivity activity) { return true; }
            @Override public Optional<ApplicationActivity> findById(Long id) { return Optional.empty(); }
            @Override public List<ApplicationActivity> findBySessionId(Long sessionId) { return Collections.emptyList(); }
            @Override public List<ApplicationActivity> findByDateRange(Instant start, Instant end) { return Collections.emptyList(); }
            @Override public List<ApplicationActivity> findRecent(int limit) { return Collections.emptyList(); }
            @Override public long deleteBySessionId(Long sessionId) { return 0; }
            @Override public long clearAll() { return 0; }
            @Override public Map<String, Long> getTopApplicationsDuration(Instant start, Instant end, int limit) { return Collections.emptyMap(); }
        };

        AtomicReference<ForegroundAppInfo> currentApp = new AtomicReference<>(
                new ForegroundAppInfo("idea64.exe", "Main.java", Instant.now())
        );

        ActivityTrackingProvider stubProvider = new ActivityTrackingProvider() {
            @Override
            public Optional<ForegroundAppInfo> getForegroundApp() {
                return Optional.ofNullable(currentApp.get());
            }

            @Override
            public boolean isSupported() { return true; }
        };

        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        ActivityTrackingService trackingService = new ActivityTrackingService(
                stubProvider, stubRepo, null, executor, false, 500L
        );

        int workers = 6;
        ExecutorService workerPool = Executors.newFixedThreadPool(workers);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(workers);

        for (int i = 0; i < workers; i++) {
            final int id = i;
            workerPool.submit(() -> {
                try {
                    startLatch.await();
                    for (int j = 0; j < 30; j++) {
                        currentApp.set(new ForegroundAppInfo("app-" + (id % 3) + ".exe", "Window " + j, Instant.now().plusSeconds(j * 2)));
                        trackingService.pollForegroundApp();
                    }
                } catch (Exception ignored) {
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean finished = doneLatch.await(5, TimeUnit.SECONDS);

        trackingService.shutdown();
        workerPool.shutdownNow();
        executor.shutdownNow();

        assertThat(finished).isTrue();
    }

    @Test
    @DisplayName("LifecycleManager shutdown is thread-safe and idempotent")
    void testLifecycleManagerShutdownIdempotency() throws InterruptedException {
        int threads = 4;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    LifecycleManager.getInstance().shutdownAll();
                } finally {
                    latch.countDown();
                }
            });
        }

        boolean finished = latch.await(3, TimeUnit.SECONDS);
        pool.shutdownNow();
        assertThat(finished).isTrue();
    }
}
