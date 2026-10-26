package com.flowstate;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Transparent, rule-based distraction analyzer (no black-box machine learning).
 * Classifies applications as PRODUCTIVE, NEUTRAL, or DISTRACTING.
 */
public class DistractionAnalyzer {
    private static final DistractionAnalyzer INSTANCE = new DistractionAnalyzer();
    private final Map<String, Activity.Category> customRules = new ConcurrentHashMap<>();

    public static DistractionAnalyzer getInstance() {
        return INSTANCE;
    }

    /**
     * Resolves the Category for an application process.
     */
    public Activity.Category resolveCategory(String processName) {
        if (processName == null || processName.isBlank()) {
            return Activity.Category.NEUTRAL;
        }
        String key = processName.trim().toLowerCase();
        if (customRules.containsKey(key)) {
            return customRules.get(key);
        }

        // Built-in intelligent defaults
        if (isProductive(key)) return Activity.Category.PRODUCTIVE;
        if (isDistracting(key)) return Activity.Category.DISTRACTING;
        return Activity.Category.NEUTRAL;
    }

    /**
     * Flags when a user transitions from productive work into a distraction.
     */
    public boolean isDistraction(Activity.Category previous, Activity.Category current) {
        return previous == Activity.Category.PRODUCTIVE && current == Activity.Category.DISTRACTING;
    }

    public void setCustomRule(String processName, Activity.Category category) {
        if (processName != null && category != null) {
            customRules.put(processName.trim().toLowerCase(), category);
        }
    }

    private boolean isProductive(String name) {
        return name.contains("idea") || name.contains("code") || name.contains("eclipse")
                || name.contains("studio") || name.contains("terminal") || name.contains("powershell")
                || name.contains("cmd") || name.contains("git") || name.contains("sublime")
                || name.contains("notepad++");
    }

    private boolean isDistracting(String name) {
        return name.contains("discord") || name.contains("steam") || name.contains("spotify")
                || name.contains("epicgames") || name.contains("netflix") || name.contains("tiktok")
                || name.contains("telegram");
    }
}
