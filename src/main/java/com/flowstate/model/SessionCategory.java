package com.flowstate.model;

/**
 * Standard categories for focus sessions.
 */
public enum SessionCategory {
    CODING("Coding"),
    STUDYING("Studying"),
    READING("Reading"),
    WRITING("Writing"),
    OTHER("Other");

    private final String displayName;

    SessionCategory(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static SessionCategory fromString(String text) {
        if (text == null || text.trim().isEmpty()) {
            return OTHER;
        }
        for (SessionCategory cat : values()) {
            if (cat.name().equalsIgnoreCase(text.trim()) || cat.displayName.equalsIgnoreCase(text.trim())) {
                return cat;
            }
        }
        return OTHER;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
