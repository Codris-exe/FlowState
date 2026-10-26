package com.flowstate.tracking;

import com.flowstate.analytics.DistractionAnalyzer;
import com.flowstate.model.AppCategory;
import com.flowstate.model.ApplicationActivity;
import com.flowstate.repository.ActivityRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

/**
 * Background service that periodically samples the active foreground application window,
 * maintains interval durations, detects category transitions, and persists
 * them using ActivityRepository.
 */
public class ActivityTrackingService {
    private static final Logger logger = LoggerFactory.getLogger(ActivityTrackingService.class);
    private static final ActivityTrackingService INSTANCE = new ActivityTrackingService();

    private final ActivityTrackingProvider trackingProvider;
    private final ActivityRepository activityRepository;
    private final ScheduledExecutorService scheduler;
    private final boolean ownsScheduler;

    private final AtomicBoolean trackingEnabled = new AtomicBoolean(true);
    private final AtomicReference<ApplicationActivity> currentActivity = new AtomicReference<>(null);
    private final List<ActivityChangeListener> listeners = new CopyOnWriteArrayList<>();

    private Function<String, AppCategory> categoryResolver = app -> AppCategory.NEUTRAL;
    private Function<String, Boolean> exclusionPredicate = app -> false;
    private DistractionAnalyzer distractionAnalyzer;

    private ScheduledFuture<?> pollingFuture;
    private final long pollIntervalMs;

    public ActivityTrackingService() {
        this(new WindowsNativeTrackingProvider(), null,
                Executors.newSingleThreadScheduledExecutor(r -> {
                    Thread t = new Thread(r, "FlowState-ActivityTracker");
                    t.setDaemon(true);
                    return t;
                }), true, 1500L);
    }

    public ActivityTrackingService(ActivityTrackingProvider trackingProvider,
                                   ActivityRepository activityRepository,
                                   ScheduledExecutorService scheduler,
                                   boolean ownsScheduler,
                                   long pollIntervalMs) {
        this.trackingProvider = trackingProvider != null ? trackingProvider : new WindowsNativeTrackingProvider();
        this.activityRepository = activityRepository;
        this.scheduler = scheduler;
        this.ownsScheduler = ownsScheduler;
        this.pollIntervalMs = pollIntervalMs;
    }

    public static ActivityTrackingService getInstance() {
        return INSTANCE;
    }

    public synchronized void start() {
        if (pollingFuture != null && !pollingFuture.isCancelled()) {
            logger.warn("ActivityTrackingService is already running.");
            return;
        }

        if (!trackingProvider.isSupported()) {
            logger.warn("Native tracking provider is not supported on this platform. Polling disabled.");
            return;
        }

        logger.info("Starting ActivityTrackingService with poll interval {}ms", pollIntervalMs);
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

    public void setDistractionAnalyzer(DistractionAnalyzer analyzer) {
        this.distractionAnalyzer = analyzer;
        if (analyzer != null) {
            this.categoryResolver = analyzer::resolveCategory;
            this.exclusionPredicate = analyzer::isExcluded;
        }
    }

    public DistractionAnalyzer getDistractionAnalyzer() {
        return distractionAnalyzer;
    }

    public void addListener(ActivityChangeListener listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    public void removeListener(ActivityChangeListener listener) {
        listeners.remove(listener);
    }

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
            Instant now = sample.timestamp() != null ? sample.timestamp() : Instant.now();

            if (exclusionPredicate.apply(processName)) {
                finalizeCurrentActivity(now);
                return;
            }

            ApplicationActivity current = currentActivity.get();

            if (current == null) {
                AppCategory category = categoryResolver.apply(processName);
                ApplicationActivity newActivity = new ApplicationActivity(
                        processName, sample.windowTitle(), now, null, category
                );
                currentActivity.set(newActivity);
                notifyActivityChanged(newActivity, null);
                return;
            }

            boolean sameApp = processName.equalsIgnoreCase(current.getApplicationName());
            boolean sameWindow = sample.windowTitle().equals(current.getWindowTitle());

            if (sameApp && sameWindow) {
                current.close(now);
            } else {
                finalizeCurrentActivity(now);

                AppCategory category = categoryResolver.apply(processName);
                ApplicationActivity newActivity = new ApplicationActivity(
                        processName, sample.windowTitle(), now, null, category
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
            if (prev.getDurationSeconds() >= 1 && activityRepository != null) {
                try {
                    activityRepository.save(prev);
                } catch (Exception e) {
                    logger.warn("Failed to persist application activity: {}", e.getMessage());
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
