package com.flowstate.analytics;

/**
 * Value object summarizing distraction and focus metrics for a specific focus session.
 */
public record SessionDistractionMetrics(
        Long sessionId,
        long totalSessionSeconds,
        long productiveSeconds,
        long distractingSeconds,
        long neutralSeconds,
        double focusPercentage,
        int interruptionCount,
        int applicationSwitchCount,
        long longestUninterruptedStreakSeconds
) {
    public static SessionDistractionMetrics empty(Long sessionId) {
        return new SessionDistractionMetrics(sessionId, 0, 0, 0, 0, 0.0, 0, 0, 0);
    }

    public String formattedFocusPercentage() {
        return String.format("%.1f%%", focusPercentage);
    }

    public String formattedLongestStreak() {
        long minutes = longestUninterruptedStreakSeconds / 60;
        long seconds = longestUninterruptedStreakSeconds % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }
}
