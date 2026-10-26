package com.flowstate.storage;

import com.flowstate.config.DatabaseManager;
import com.flowstate.repository.*;
import com.flowstate.repository.file.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Factory managing application repository implementations (File-Based vs JDBC-Based).
 * Defaults to File-based persistence for instant zero-setup execution.
 */
public class RepositoryFactory {
    private static final Logger logger = LoggerFactory.getLogger(RepositoryFactory.class);

    private static FocusSessionRepository sessionRepository = new FocusSessionRepositoryFile();
    private static ActivityRepository activityRepository = new ActivityRepositoryFile();
    private static ApplicationPreferenceRepository preferenceRepository = new ApplicationPreferenceRepositoryFile();
    private static boolean useFileStorage = true;

    public static synchronized void setFileStorageMode() {
        useFileStorage = true;
        sessionRepository = new FocusSessionRepositoryFile();
        activityRepository = new ActivityRepositoryFile();
        preferenceRepository = new ApplicationPreferenceRepositoryFile();
        logger.info("Configured FlowState for local JSON File Storage mode in './data'");
    }

    public static synchronized void setJdbcStorageMode() {
        useFileStorage = false;
        DatabaseManager db = DatabaseManager.getInstance();
        sessionRepository = new FocusSessionRepositoryJDBC(db);
        activityRepository = new ActivityRepositoryJDBC(db);
        preferenceRepository = new ApplicationPreferenceRepositoryJDBC(db);
        logger.info("Configured FlowState for PostgreSQL JDBC Storage mode");
    }

    public static FocusSessionRepository getFocusSessionRepository() {
        return sessionRepository;
    }

    public static ActivityRepository getActivityRepository() {
        return activityRepository;
    }

    public static ApplicationPreferenceRepository getApplicationPreferenceRepository() {
        return preferenceRepository;
    }

    public static boolean isFileStorageMode() {
        return useFileStorage;
    }
}
