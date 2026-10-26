package com.flowstate.model;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Domain entity representing an active application usage interval.
 */
public class ApplicationActivity {
    private Long id;
    private String applicationName;
    private String windowTitle;
    private Instant startedAt;
    private Instant endedAt;
    private long durationSeconds;
    private Long sessionId;
    private AppCategory activityCategory;

    public ApplicationActivity() {
        this.activityCategory = AppCategory.NEUTRAL;
        this.startedAt = Instant.now();
        this.durationSeconds = 0;
    }

    public ApplicationActivity(String applicationName, String windowTitle, Instant startedAt,
                               Long sessionId, AppCategory activityCategory) {
        this.applicationName = applicationName != null ? applicationName.trim() : "Unknown";
        this.windowTitle = windowTitle != null ? windowTitle.trim() : "";
        this.startedAt = startedAt != null ? startedAt : Instant.now();
        this.sessionId = sessionId;
        this.activityCategory = activityCategory != null ? activityCategory : AppCategory.NEUTRAL;
        this.durationSeconds = 0;
    }

    public ApplicationActivity(Long id, String applicationName, String windowTitle,
                               Instant startedAt, Instant endedAt, long durationSeconds,
                               Long sessionId, AppCategory activityCategory) {
        this.id = id;
        this.applicationName = applicationName;
        this.windowTitle = windowTitle;
        this.startedAt = startedAt;
        this.endedAt = endedAt;
        this.durationSeconds = durationSeconds;
        this.sessionId = sessionId;
        this.activityCategory = activityCategory != null ? activityCategory : AppCategory.NEUTRAL;
    }

    public void close(Instant endInstant) {
        this.endedAt = endInstant != null ? endInstant : Instant.now();
        if (this.startedAt != null && this.endedAt.isAfter(this.startedAt)) {
            this.durationSeconds = Duration.between(this.startedAt, this.endedAt).toSeconds();
        } else {
            this.durationSeconds = 0;
        }
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getApplicationName() {
        return applicationName;
    }

    public void setApplicationName(String applicationName) {
        this.applicationName = applicationName;
    }

    public String getWindowTitle() {
        return windowTitle;
    }

    public void setWindowTitle(String windowTitle) {
        this.windowTitle = windowTitle;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getEndedAt() {
        return endedAt;
    }

    public void setEndedAt(Instant endedAt) {
        this.endedAt = endedAt;
    }

    public long getDurationSeconds() {
        return durationSeconds;
    }

    public void setDurationSeconds(long durationSeconds) {
        this.durationSeconds = durationSeconds;
    }

    public Long getSessionId() {
        return sessionId;
    }

    public void setSessionId(Long sessionId) {
        this.sessionId = sessionId;
    }

    public AppCategory getActivityCategory() {
        return activityCategory;
    }

    public void setActivityCategory(AppCategory activityCategory) {
        this.activityCategory = activityCategory;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ApplicationActivity that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "ApplicationActivity{" +
                "id=" + id +
                ", app='" + applicationName + '\'' +
                ", category=" + activityCategory +
                ", duration=" + durationSeconds + "s" +
                ", sessionId=" + sessionId +
                '}';
    }
}
