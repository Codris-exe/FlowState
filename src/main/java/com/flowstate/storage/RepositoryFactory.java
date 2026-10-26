package com.flowstate.storage;

import com.flowstate.repository.ActivityRepository;
import com.flowstate.repository.ApplicationPreferenceRepository;
import com.flowstate.repository.file.ActivityRepositoryFile;
import com.flowstate.repository.file.ApplicationPreferenceRepositoryFile;

/**
 * Factory providing singleton repository instances that persist
 * data to local files using Java InputStream and OutputStream.
 */
public class RepositoryFactory {
    private static final ActivityRepository activityRepository = new ActivityRepositoryFile();
    private static final ApplicationPreferenceRepository preferenceRepository = new ApplicationPreferenceRepositoryFile();

    public static ActivityRepository getActivityRepository() {
        return activityRepository;
    }

    public static ApplicationPreferenceRepository getApplicationPreferenceRepository() {
        return preferenceRepository;
    }
}
