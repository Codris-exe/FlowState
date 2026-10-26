package com.focusforge;

import java.io.Serial;
import java.io.Serializable;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public class Activity implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    public enum Category {
        PRODUCTIVE, NEUTRAL, DISTRACTING
    }

    private Long id;
    private String applicationName;
    private String windowTitle;
    private Instant startedAt;
    private Instant endedAt;
    private long durationSeconds;
    private Category category;

    public Activity() {}

    public Activity(String applicationName, String windowTitle, Instant startedAt, Category category) {
        this.applicationName = applicationName != null ? applicationName : "Unknown";
        this.windowTitle = windowTitle != null ? windowTitle : "";
        this.startedAt = startedAt != null ? startedAt : Instant.now();
        this.category = category != null ? category : Category.NEUTRAL;
    }

    public Activity(Long id, String applicationName, String windowTitle, Instant startedAt, Instant endedAt, long durationSeconds, Category category) {
        this.id = id;
        this.applicationName = applicationName;
        this.windowTitle = windowTitle;
        this.startedAt = startedAt;
        this.endedAt = endedAt;
        this.durationSeconds = durationSeconds;
        this.category = category;
    }

    public void close(Instant endInstant) {
        this.endedAt = endInstant != null ? endInstant : Instant.now();
        if (this.startedAt != null && this.endedAt.isAfter(this.startedAt)) {
            this.durationSeconds = Math.max(1, Duration.between(this.startedAt, this.endedAt).toSeconds());
        } else {
            this.durationSeconds = 1;
        }
    }

    public String getFormattedDuration() {
        if (durationSeconds < 60) return durationSeconds + "s";
        long minutes = durationSeconds / 60;
        long hours = minutes / 60;
        long remainingMins = minutes % 60;
        if (hours > 0) return hours + "h " + remainingMins + "m";
        return minutes + "m " + (durationSeconds % 60) + "s";
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getApplicationName() { return applicationName; }

    public String getWindowTitle() { return windowTitle; }

    public Instant getStartedAt() { return startedAt; }

    public void setEndedAt(Instant endedAt) { this.endedAt = endedAt; }

    public long getDurationSeconds() { return durationSeconds; }
    public void setDurationSeconds(long durationSeconds) { this.durationSeconds = durationSeconds; }

    public Category getCategory() { return category; }
    public void setCategory(Category category) { this.category = category; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Activity that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Activity{" + applicationName + ", " + durationSeconds + "s, " + category + "}";
    }
}
