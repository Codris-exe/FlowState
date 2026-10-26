package com.flowstate.analytics;

import com.flowstate.model.AppCategory;
import com.flowstate.model.ApplicationPreference;
import com.flowstate.repository.ApplicationPreferenceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Transparent, rule-based distraction analyzer (no black-box machine learning).
 *
 * Implements strict, explainable classification rules:
 * - Only switches from PRODUCTIVE to DISTRACTING are counted as distractions.
 * - Neutral switches (e.g. IDE to documentation or terminal) are NOT distractions.
 */
public class DistractionAnalyzer {
    private static final Logger logger = LoggerFactory.getLogger(DistractionAnalyzer.class);

    private final ApplicationPreferenceRepository preferenceRepository;
    private final Map<String, ApplicationPreference> preferenceCache = new ConcurrentHashMap<>();

    public DistractionAnalyzer() {
        this(null);
    }

    public DistractionAnalyzer(ApplicationPreferenceRepository preferenceRepository) {
        this.preferenceRepository = preferenceRepository;
        refreshCache();
    }

    /**
     * Refreshes the local memory cache from the repository if available.
     */
    public synchronized void refreshCache() {
        if (preferenceRepository == null) return;
        try {
            List<ApplicationPreference> all = preferenceRepository.findAll();
            preferenceCache.clear();
            for (ApplicationPreference p : all) {
                if (p.getApplicationIdentifier() != null) {
                    preferenceCache.put(p.getApplicationIdentifier().toLowerCase(), p);
                }
            }
            logger.debug("Refreshed preference cache with {} items", preferenceCache.size());
        } catch (Exception e) {
            logger.warn("Failed to load application preferences into cache: {}", e.getMessage());
        }
    }

    /**
     * Resolves the AppCategory for a given executable process name.
     */
    public AppCategory resolveCategory(String processName) {
        if (processName == null || processName.isBlank()) {
            return AppCategory.NEUTRAL;
        }
        String key = processName.trim().toLowerCase();
        ApplicationPreference pref = preferenceCache.get(key);
        if (pref != null) {
            return pref.getCategory();
        }

        // Built-in intelligent defaults for common applications
        if (isDefaultProductive(key)) return AppCategory.PRODUCTIVE;
        if (isDefaultDistracting(key)) return AppCategory.DISTRACTING;

        return AppCategory.NEUTRAL;
    }

    /**
     * Determines whether an application is flagged as excluded from tracking.
     */
    public boolean isExcluded(String processName) {
        if (processName == null || processName.isBlank()) {
            return false;
        }
        ApplicationPreference pref = preferenceCache.get(processName.trim().toLowerCase());
        return pref != null && pref.isExcluded();
    }

    /**
     * Determines whether a transition from previousCategory to currentCategory
     * constitutes an interruption/distraction.
     */
    public boolean isDistraction(AppCategory previousCategory, AppCategory currentCategory) {
        if (previousCategory == null || currentCategory == null) {
            return false;
        }
        return previousCategory == AppCategory.PRODUCTIVE && currentCategory == AppCategory.DISTRACTING;
    }

    public void setPreference(String identifier, String displayName, AppCategory category, boolean isExcluded) {
        String cleanId = identifier.trim().toLowerCase();
        ApplicationPreference pref = new ApplicationPreference(cleanId, displayName, category, isExcluded);
        preferenceCache.put(cleanId, pref);

        if (preferenceRepository != null) {
            try {
                preferenceRepository.upsert(cleanId, displayName, category, isExcluded);
            } catch (Exception e) {
                logger.error("Failed to persist application preference: {}", e.getMessage());
            }
        }
    }

    private boolean isDefaultProductive(String name) {
        return name.contains("idea") || name.contains("code") || name.contains("eclipse")
                || name.contains("studio") || name.contains("terminal") || name.contains("powershell")
                || name.contains("cmd.exe") || name.contains("git") || name.contains("sublime")
                || name.contains("notepad++");
    }

    private boolean isDefaultDistracting(String name) {
        return name.contains("steam") || name.contains("discord") || name.contains("spotify")
                || name.contains("epicgames") || name.contains("netflix") || name.contains("tiktok")
                || name.contains("telegram");
    }
}
