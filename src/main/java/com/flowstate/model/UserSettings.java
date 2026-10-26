package com.flowstate.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Global application and user preferences entity.
 */
public class UserSettings {
    private Long id;
    private int dailyFocusGoalMinutes;
    private String theme;
    private boolean trackingEnabled;
    private Instant createdAt;
    private Instant updatedAt;

    public UserSettings() {
        this.dailyFocusGoalMinutes = 120; // 2 hours default
        this.theme = "DARK";
        this.trackingEnabled = true;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public UserSettings(int dailyFocusGoalMinutes, String theme, boolean trackingEnabled) {
        this();
        this.dailyFocusGoalMinutes = dailyFocusGoalMinutes > 0 ? dailyFocusGoalMinutes : 120;
        this.theme = theme != null ? theme.trim().toUpperCase() : "DARK";
        this.trackingEnabled = trackingEnabled;
    }

    public UserSettings(Long id, int dailyFocusGoalMinutes, String theme, boolean trackingEnabled,
                        Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.dailyFocusGoalMinutes = dailyFocusGoalMinutes;
        this.theme = theme != null ? theme : "DARK";
        this.trackingEnabled = trackingEnabled;
        this.createdAt = createdAt != null ? createdAt : Instant.now();
        this.updatedAt = updatedAt != null ? updatedAt : Instant.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public int getDailyFocusGoalMinutes() {
        return dailyFocusGoalMinutes;
    }

    public void setDailyFocusGoalMinutes(int dailyFocusGoalMinutes) {
        this.dailyFocusGoalMinutes = dailyFocusGoalMinutes;
        this.updatedAt = Instant.now();
    }

    public String getTheme() {
        return theme;
    }

    public void setTheme(String theme) {
        this.theme = theme;
        this.updatedAt = Instant.now();
    }

    public boolean isTrackingEnabled() {
        return trackingEnabled;
    }

    public void setTrackingEnabled(boolean trackingEnabled) {
        this.trackingEnabled = trackingEnabled;
        this.updatedAt = Instant.now();
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof UserSettings that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "UserSettings{" +
                "dailyGoal=" + dailyFocusGoalMinutes + "m" +
                ", theme='" + theme + '\'' +
                ", tracking=" + trackingEnabled +
                '}';
    }
}
