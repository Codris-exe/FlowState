package com.flowstate.storage;

import com.flowstate.repository.ActivityRepository;
import com.flowstate.repository.ApplicationPreferenceRepository;
import com.flowstate.repository.FocusSessionRepository;
import com.flowstate.repository.file.ActivityRepositoryFile;
import com.flowstate.repository.file.ApplicationPreferenceRepositoryFile;
import com.flowstate.repository.file.FocusSessionRepositoryFile;

/**
 * Factory providing singleton repository instances that persist
 * data to local files using Java InputStream and OutputStream.
 */
public class RepositoryFactory {
    private static final FocusSessionRepository sessionRepository = new FocusSessionRepositoryFile();
    private static final ActivityRepository activityRepository = new ActivityRepositoryFile();
    private static final ApplicationPreferenceRepository preferenceRepository = new ApplicationPreferenceRepositoryFile();

    public static FocusSessionRepository getFocusSessionRepository() {
        return sessionRepository;
    }

    public static ActivityRepository getActivityRepository() {
        return activityRepository;
    }

    public static ApplicationPreferenceRepository getApplicationPreferenceRepository() {
        return preferenceRepository;
    }
}
