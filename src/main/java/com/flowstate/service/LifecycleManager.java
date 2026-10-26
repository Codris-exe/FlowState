package com.flowstate.service;

import com.flowstate.config.DatabaseManager;
import com.flowstate.tracking.ActivityTrackingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Coordinates deterministic application lifecycle shutdown and resource cleanup.
 *
 * Registers JVM shutdown hooks and handles graceful termination of background
 * scheduled executor threads and database connection pools.
 */
public class LifecycleManager {
    private static final Logger logger = LoggerFactory.getLogger(LifecycleManager.class);
    private static final LifecycleManager INSTANCE = new LifecycleManager();

    private final AtomicBoolean shutdownExecuted = new AtomicBoolean(false);
    private Thread shutdownHook;

    private LifecycleManager() {
        registerShutdownHook();
    }

    public static LifecycleManager getInstance() {
        return INSTANCE;
    }

    private void registerShutdownHook() {
        shutdownHook = new Thread(this::shutdownAll, "FlowState-ShutdownHook");
        try {
            Runtime.getRuntime().addShutdownHook(shutdownHook);
            logger.info("Registered JVM shutdown hook for FlowState.");
        } catch (IllegalStateException ignored) {
            // JVM already shutting down
        }
    }

    /**
     * Executes clean shutdown of all background services, schedulers, and connection pools.
     */
    public synchronized void shutdownAll() {
        if (!shutdownExecuted.compareAndSet(false, true)) {
            return;
        }

        logger.info("Executing graceful system shutdown...");

        // 1. Terminate native background activity tracking
        try {
            ActivityTrackingService.getInstance().shutdown();
            logger.info("Activity tracking service stopped.");
        } catch (Exception e) {
            logger.warn("Error during activity tracking shutdown: {}", e.getMessage());
        }

        // 2. Terminate focus session timer scheduler
        try {
            FocusSessionService.getInstance().shutdown();
            logger.info("Focus session timer service stopped.");
        } catch (Exception e) {
            logger.warn("Error during focus session shutdown: {}", e.getMessage());
        }

        // 3. Close HikariCP database connection pool
        try {
            DatabaseManager.getInstance().close();
            logger.info("Database connection pool closed.");
        } catch (Exception e) {
            logger.warn("Error closing database connection pool: {}", e.getMessage());
        }

        logger.info("FlowState system shutdown completed cleanly.");
    }
}
