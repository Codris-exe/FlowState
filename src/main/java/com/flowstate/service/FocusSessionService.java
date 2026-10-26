package com.flowstate.service;

import com.flowstate.model.FocusSession;
import com.flowstate.model.SessionCategory;
import com.flowstate.model.SessionStatus;
import com.flowstate.repository.FocusSessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Thread-safe service managing focus session lifecycle, duration tracking,
 * and timer tick dispatch. Enforces single-active-session concurrency.
 */
public class FocusSessionService {
    private static final Logger logger = LoggerFactory.getLogger(FocusSessionService.class);
    private static final FocusSessionService INSTANCE = new FocusSessionService();

    private final AtomicReference<FocusSession> activeSession = new AtomicReference<>(null);
    private final List<FocusSessionListener> listeners = new CopyOnWriteArrayList<>();
    private FocusSessionRepository repository;

    private final ScheduledExecutorService tickerExecutor;
    private final boolean ownsExecutor;
    private ScheduledFuture<?> tickerFuture;

    public FocusSessionService() {
        this(Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "FlowState-FocusTimer");
            t.setDaemon(true);
            return t;
        }), true, null);
    }

    public FocusSessionService(FocusSessionRepository repository) {
        this(Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "FlowState-FocusTimer");
            t.setDaemon(true);
            return t;
        }), true, repository);
    }

    public FocusSessionService(ScheduledExecutorService tickerExecutor) {
        this(tickerExecutor, false, null);
    }

    public FocusSessionService(ScheduledExecutorService tickerExecutor, boolean ownsExecutor, FocusSessionRepository repository) {
        this.tickerExecutor = Objects.requireNonNull(tickerExecutor, "tickerExecutor must not be null");
        this.ownsExecutor = ownsExecutor;
        this.repository = repository;
    }

    public static FocusSessionService getInstance() {
        return INSTANCE;
    }

    public void setRepository(FocusSessionRepository repository) {
        this.repository = repository;
    }

    public FocusSessionRepository getRepository() {
        return repository;
    }

    /**
     * Starts a new focus session.
     *
     * @throws IllegalStateException if an active session is already running or paused
     */
    public synchronized FocusSession startSession(String title, SessionCategory category, long targetDurationSeconds) {
        FocusSession current = activeSession.get();
        if (current != null && current.getStatus().isActive()) {
            throw new IllegalStateException("An active focus session ('" + current.getTitle() + "') is already in progress.");
        }

        FocusSession session = new FocusSession(title, category, targetDurationSeconds);
        session.start(Instant.now());

        if (repository != null) {
            try {
                repository.save(session);
            } catch (Exception e) {
                logger.error("Failed to persist new focus session to database: {}", e.getMessage());
            }
        }

        activeSession.set(session);

        logger.info("Started focus session: id={}, title='{}', target={}s", session.getId(), session.getTitle(), targetDurationSeconds);

        startTicker();
        notifySessionStarted(session);
        return session;
    }

    /**
     * Pauses the active focus session.
     *
     * @throws IllegalStateException if there is no active session or it is not running
     */
    public synchronized void pauseSession() {
        FocusSession session = activeSession.get();
        if (session == null || session.getStatus() != SessionStatus.RUNNING) {
            throw new IllegalStateException("Cannot pause: No running focus session.");
        }

        session.pause(Instant.now());
        if (repository != null && session.getId() != null) {
            try {
                repository.update(session);
            } catch (Exception e) {
                logger.warn("Failed to update paused session in database: {}", e.getMessage());
            }
        }
        logger.info("Paused focus session: '{}'", session.getTitle());
        notifySessionPaused(session);
    }

    /**
     * Resumes the paused focus session.
     *
     * @throws IllegalStateException if there is no active session or it is not paused
     */
    public synchronized void resumeSession() {
        FocusSession session = activeSession.get();
        if (session == null || session.getStatus() != SessionStatus.PAUSED) {
            throw new IllegalStateException("Cannot resume: Session is not paused.");
        }

        session.resume(Instant.now());
        if (repository != null && session.getId() != null) {
            try {
                repository.update(session);
            } catch (Exception e) {
                logger.warn("Failed to update resumed session in database: {}", e.getMessage());
            }
        }
        logger.info("Resumed focus session: '{}'", session.getTitle());
        notifySessionResumed(session);
    }

    /**
     * Completes the active focus session, recording completion notes and final duration.
     *
     * @throws IllegalStateException if there is no active session
     */
    public synchronized FocusSession completeSession(String notes) {
        FocusSession session = activeSession.get();
        if (session == null || !session.getStatus().isActive()) {
            throw new IllegalStateException("Cannot complete: No active focus session.");
        }

        stopTicker();
        if (notes != null && !notes.isBlank()) {
            session.setNotes(notes.trim());
        }
        session.complete(Instant.now());

        if (repository != null) {
            try {
                if (session.getId() != null) {
                    repository.update(session);
                } else {
                    repository.save(session);
                }
            } catch (Exception e) {
                logger.error("Failed to persist completed focus session: {}", e.getMessage());
            }
        }

        activeSession.set(null);

        logger.info("Completed focus session: '{}' with actual duration {}s", session.getTitle(), session.getActualDurationSeconds());
        notifySessionCompleted(session);
        return session;
    }

    /**
     * Cancels and discards the active focus session.
     */
    public synchronized FocusSession cancelSession() {
        FocusSession session = activeSession.get();
        if (session == null || !session.getStatus().isActive()) {
            throw new IllegalStateException("Cannot cancel: No active focus session.");
        }

        stopTicker();
        session.cancel(Instant.now());

        if (repository != null && session.getId() != null) {
            try {
                repository.update(session);
            } catch (Exception e) {
                logger.error("Failed to update cancelled focus session: {}", e.getMessage());
            }
        }

        activeSession.set(null);

        logger.info("Cancelled focus session: '{}'", session.getTitle());
        notifySessionCancelled(session);
        return session;
    }

    public synchronized void recordInterruption() {
        FocusSession session = activeSession.get();
        if (session != null && session.getStatus() == SessionStatus.RUNNING) {
            session.incrementInterruptionCount();
            logger.debug("Interruption recorded for session '{}'. Total count: {}", session.getTitle(), session.getInterruptionCount());
        }
    }

    private synchronized void startTicker() {
        stopTicker();
        tickerFuture = tickerExecutor.scheduleAtFixedRate(this::tick, 1, 1, TimeUnit.SECONDS);
    }

    private synchronized void stopTicker() {
        if (tickerFuture != null && !tickerFuture.isCancelled()) {
            tickerFuture.cancel(false);
            tickerFuture = null;
        }
    }

    private void tick() {
        try {
            FocusSession session = activeSession.get();
            if (session != null && session.getStatus().isActive()) {
                Instant now = Instant.now();
                long elapsed = session.getElapsedSeconds(now);
                long remaining = session.getRemainingSeconds(now);
                notifyTimerTick(session, elapsed, remaining);
            }
        } catch (Exception e) {
            logger.error("Error during timer tick: {}", e.getMessage(), e);
        }
    }

    // Listener registration
    public void addListener(FocusSessionListener listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    public void removeListener(FocusSessionListener listener) {
        listeners.remove(listener);
    }

    private void notifySessionStarted(FocusSession session) {
        for (FocusSessionListener l : listeners) {
            try {
                l.onSessionStarted(session);
            } catch (Exception e) {
                logger.warn("Listener threw exception on session start: {}", e.getMessage());
            }
        }
    }

    private void notifySessionPaused(FocusSession session) {
        for (FocusSessionListener l : listeners) {
            try {
                l.onSessionPaused(session);
            } catch (Exception e) {
                logger.warn("Listener threw exception on session pause: {}", e.getMessage());
            }
        }
    }

    private void notifySessionResumed(FocusSession session) {
        for (FocusSessionListener l : listeners) {
            try {
                l.onSessionResumed(session);
            } catch (Exception e) {
                logger.warn("Listener threw exception on session resume: {}", e.getMessage());
            }
        }
    }

    private void notifySessionCompleted(FocusSession session) {
        for (FocusSessionListener l : listeners) {
            try {
                l.onSessionCompleted(session);
            } catch (Exception e) {
                logger.warn("Listener threw exception on session completion: {}", e.getMessage());
            }
        }
    }

    private void notifySessionCancelled(FocusSession session) {
        for (FocusSessionListener l : listeners) {
            try {
                l.onSessionCancelled(session);
            } catch (Exception e) {
                logger.warn("Listener threw exception on session cancel: {}", e.getMessage());
            }
        }
    }

    private void notifyTimerTick(FocusSession session, long elapsed, long remaining) {
        for (FocusSessionListener l : listeners) {
            try {
                l.onTimerTick(session, elapsed, remaining);
            } catch (Exception e) {
                logger.warn("Listener threw exception on timer tick: {}", e.getMessage());
            }
        }
    }

    // Query methods
    public FocusSession getActiveSession() {
        return activeSession.get();
    }

    public boolean hasActiveSession() {
        FocusSession session = activeSession.get();
        return session != null && session.getStatus().isActive();
    }

    public boolean isRunning() {
        FocusSession session = activeSession.get();
        return session != null && session.getStatus() == SessionStatus.RUNNING;
    }

    public boolean isPaused() {
        FocusSession session = activeSession.get();
        return session != null && session.getStatus() == SessionStatus.PAUSED;
    }

    public synchronized void shutdown() {
        stopTicker();
        if (ownsExecutor && !tickerExecutor.isShutdown()) {
            tickerExecutor.shutdownNow();
        }
    }
}
