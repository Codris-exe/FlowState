package com.flowstate.model;

/**
 * Classification category for desktop applications.
 */
public enum AppCategory {
    PRODUCTIVE("Productive"),
    DISTRACTING("Distracting"),
    NEUTRAL("Neutral");

    private final String displayName;

    AppCategory(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static AppCategory fromString(String text) {
        if (text == null || text.trim().isEmpty()) {
            return NEUTRAL;
        }
        for (AppCategory cat : values()) {
            if (cat.name().equalsIgnoreCase(text.trim()) || cat.displayName.equalsIgnoreCase(text.trim())) {
                return cat;
            }
        }
        return NEUTRAL;
    }
}
