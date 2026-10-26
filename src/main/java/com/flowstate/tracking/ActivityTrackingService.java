package com.flowstate.tracking;

import com.flowstate.model.AppCategory;
import com.flowstate.model.ApplicationActivity;
import com.flowstate.repository.ActivityRepository;
import com.flowstate.service.FocusSessionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

/**
 * Background service that periodically samples the active foreground application window,
 * maintains interval durations, links activities to active focus sessions, and persists
 * them using ActivityRepository.
 */
public class ActivityTrackingService {
    private static final Logger logger = LoggerFactory.getLogger(ActivityTrackingService.class);
    private static final ActivityTrackingService INSTANCE = new ActivityTrackingService();

    private final ActivityTrackingProvider trackingProvider;
    private final ActivityRepository activityRepository;
    private final FocusSessionService sessionService;
    private final ScheduledExecutorService scheduler;
    private final boolean ownsScheduler;

    private final AtomicBoolean trackingEnabled = new AtomicBoolean(true);
    private final AtomicReference<ApplicationActivity> currentActivity = new AtomicReference<>(null);
    private final List<ActivityChangeListener> listeners = new CopyOnWriteArrayList<>();

    // Optional category and exclusion resolver: maps app identifier to AppCategory
    private Function<String, AppCategory> categoryResolver = app -> AppCategory.NEUTRAL;
    private Function<String, Boolean> exclusionPredicate = app -> false;

    private ScheduledFuture<?> pollingFuture;
    private final long pollIntervalMs;

    public ActivityTrackingService() {
        this(new WindowsNativeTrackingProvider(), null, FocusSessionService.getInstance(),
                Executors.newSingleThreadScheduledExecutor(r -> {
                    Thread t = new Thread(r, "FlowState-ActivityTracker");
                    t.setDaemon(true);
                    return t;
                }), true, 1500L);
    }

    public ActivityTrackingService(ActivityTrackingProvider trackingProvider,
                                   ActivityRepository activityRepository,
                                   FocusSessionService sessionService,
                                   ScheduledExecutorService scheduler,
                                   boolean ownsScheduler,
                                   long pollIntervalMs) {
        this.trackingProvider = Objects.requireNonNull(trackingProvider, "trackingProvider must not be null");
        this.activityRepository = activityRepository;
        this.sessionService = sessionService != null ? sessionService : FocusSessionService.getInstance();
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler must not be null");
        this.ownsScheduler = ownsScheduler;
        this.pollIntervalMs = Math.max(500, pollIntervalMs);
    }

    public static ActivityTrackingService getInstance() {
        return INSTANCE;
    }

    public synchronized void start() {
        if (pollingFuture != null && !pollingFuture.isCancelled()) {
            return;
        }

        logger.info("Starting background activity tracking polling at interval {}ms", pollIntervalMs);
        pollingFuture = scheduler.scheduleWithFixedDelay(
                this::pollForegroundApp,
                0,
                pollIntervalMs,
                TimeUnit.MILLISECONDS
        );
    }

    public synchronized void pauseTracking() {
        if (trackingEnabled.compareAndSet(true, false)) {
            logger.info("Activity tracking paused by user");
            // Finalize current activity interval if active
            finalizeCurrentActivity(Instant.now());
            notifyTrackingStateChanged(false);
        }
    }

    public synchronized void resumeTracking() {
        if (trackingEnabled.compareAndSet(false, true)) {
            logger.info("Activity tracking resumed by user");
            notifyTrackingStateChanged(true);
        }
    }

    public boolean isTrackingEnabled() {
        return trackingEnabled.get();
    }

    public ApplicationActivity getCurrentActivity() {
        return currentActivity.get();
    }

    public void setCategoryResolver(Function<String, AppCategory> categoryResolver) {
        if (categoryResolver != null) {
            this.categoryResolver = categoryResolver;
        }
    }

    public void setExclusionPredicate(Function<String, Boolean> exclusionPredicate) {
        if (exclusionPredicate != null) {
            this.exclusionPredicate = exclusionPredicate;
        }
    }

    public void addListener(ActivityChangeListener listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    public void removeListener(ActivityChangeListener listener) {
        listeners.remove(listener);
    }

    /**
     * Core polling logic executed on scheduler thread.
     */
    public void pollForegroundApp() {
        if (!trackingEnabled.get()) {
            return;
        }

        try {
            Optional<ForegroundAppInfo> sampleOpt = trackingProvider.getForegroundApp();
            if (sampleOpt.isEmpty()) {
                return;
            }

            ForegroundAppInfo sample = sampleOpt.get();
            String processName = sample.processName();

            // Check if application is excluded from tracking
            if (exclusionPredicate.apply(processName)) {
                finalizeCurrentActivity(Instant.now());
                return;
            }

            Instant now = sample.timestamp();
            ApplicationActivity current = currentActivity.get();

            Long activeSessionId = null;
            if (sessionService != null && sessionService.hasActiveSession()) {
                activeSessionId = sessionService.getActiveSession().getId();
            }

            if (current == null) {
                // First sampled activity
                AppCategory category = categoryResolver.apply(processName);
                ApplicationActivity newActivity = new ApplicationActivity(
                        processName, sample.windowTitle(), now, activeSessionId, category
                );
                currentActivity.set(newActivity);
                notifyActivityChanged(newActivity, null);
                return;
            }

            // Check if active application or window changed
            boolean sameApp = processName.equalsIgnoreCase(current.getApplicationName());
            boolean sameWindow = sample.windowTitle().equals(current.getWindowTitle());
            boolean sameSession = Objects.equals(activeSessionId, current.getSessionId());

            if (sameApp && sameWindow && sameSession) {
                // Same active window: update elapsed duration
                current.close(now);
            } else {
                // Application switch detected
                finalizeCurrentActivity(now);

                AppCategory category = categoryResolver.apply(processName);
                ApplicationActivity newActivity = new ApplicationActivity(
                        processName, sample.windowTitle(), now, activeSessionId, category
                );
                currentActivity.set(newActivity);
                notifyActivityChanged(newActivity, current);
            }
        } catch (Exception e) {
            logger.debug("Error during foreground app polling: {}", e.getMessage());
        }
    }

    private void finalizeCurrentActivity(Instant now) {
        ApplicationActivity prev = currentActivity.getAndSet(null);
        if (prev != null) {
            prev.close(now);
            // Only persist activities that lasted at least 1 second
            if (prev.getDurationSeconds() >= 1 && activityRepository != null) {
                try {
                    activityRepository.save(prev);
                } catch (Exception e) {
                    logger.warn("Failed to persist application activity to database: {}", e.getMessage());
                }
            }
        }
    }

    private void notifyActivityChanged(ApplicationActivity current, ApplicationActivity previous) {
        for (ActivityChangeListener l : listeners) {
            try {
                l.onActivityChanged(current, previous);
            } catch (Exception e) {
                logger.warn("Listener threw exception on activity change: {}", e.getMessage());
            }
        }
    }

    private void notifyTrackingStateChanged(boolean enabled) {
        for (ActivityChangeListener l : listeners) {
            try {
                l.onTrackingStateChanged(enabled);
            } catch (Exception e) {
                logger.warn("Listener threw exception on tracking state change: {}", e.getMessage());
            }
        }
    }

    public synchronized void shutdown() {
        logger.info("Shutting down ActivityTrackingService");
        if (pollingFuture != null) {
            pollingFuture.cancel(false);
            pollingFuture = null;
        }
        finalizeCurrentActivity(Instant.now());
        if (ownsScheduler && !scheduler.isShutdown()) {
            scheduler.shutdownNow();
        }
    }
}
