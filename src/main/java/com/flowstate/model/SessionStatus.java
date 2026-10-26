package com.flowstate.model;

/**
 * Lifecycle states of a focus session.
 */
public enum SessionStatus {
    PENDING("Pending"),
    RUNNING("Running"),
    PAUSED("Paused"),
    COMPLETED("Completed"),
    CANCELLED("Cancelled");

    private final String displayName;

    SessionStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static SessionStatus fromString(String text) {
        if (text == null || text.trim().isEmpty()) {
            return PENDING;
        }
        for (SessionStatus status : values()) {
            if (status.name().equalsIgnoreCase(text.trim()) || status.displayName.equalsIgnoreCase(text.trim())) {
                return status;
            }
        }
        return PENDING;
    }

    public boolean isActive() {
        return this == RUNNING || this == PAUSED;
    }
}
