package com.flowstate.ui.model;

import com.flowstate.model.AppCategory;

/**
 * Table presentation model for top application usage metrics.
 */
public class AppUsageSummary {
    private final String applicationName;
    private final long durationSeconds;
    private final String formattedDuration;
    private final AppCategory category;

    public AppUsageSummary(String applicationName, long durationSeconds, AppCategory category) {
        this.applicationName = applicationName;
        this.durationSeconds = durationSeconds;
        this.category = category != null ? category : AppCategory.NEUTRAL;

        long hours = durationSeconds / 3600;
        long minutes = (durationSeconds % 3600) / 60;
        long seconds = durationSeconds % 60;

        if (hours > 0) {
            this.formattedDuration = String.format("%dh %dm", hours, minutes);
        } else if (minutes > 0) {
            this.formattedDuration = String.format("%dm %ds", minutes, seconds);
        } else {
            this.formattedDuration = String.format("%ds", seconds);
        }
    }

    public String getApplicationName() {
        return applicationName;
    }

    public String getFormattedDuration() {
        return formattedDuration;
    }

    public AppCategory getCategory() {
        return category;
    }

    public String getCategoryName() {
        return category.getDisplayName();
    }
}
