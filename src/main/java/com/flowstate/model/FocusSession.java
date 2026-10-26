package com.flowstate.model;

import java.io.Serializable;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Core domain entity representing a focus session.
 * Uses timestamp-anchored elapsed math to prevent clock drift during minimize or sleep.
 */
public class FocusSession implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private String title;
    private SessionCategory category;
    private Instant startTime;
    private Instant endTime;
    private long targetDurationSeconds;
    private long actualDurationSeconds;
    private SessionStatus status;
    private String notes;
    private int interruptionCount;
    private Instant createdAt;

    // Transient pause tracking fields for accurate runtime calculation
    private Instant lastPauseInstant;
    private Duration accumulatedPauseDuration = Duration.ZERO;

    public FocusSession() {
        this.status = SessionStatus.PENDING;
        this.category = SessionCategory.CODING;
        this.targetDurationSeconds = 1500; // 25 minutes default
        this.actualDurationSeconds = 0;
        this.interruptionCount = 0;
        this.createdAt = Instant.now();
    }

    public FocusSession(String title, SessionCategory category, long targetDurationSeconds) {
        this();
        this.title = title != null && !title.isBlank() ? title.trim() : "Focus Session";
        this.category = category != null ? category : SessionCategory.CODING;
        this.targetDurationSeconds = targetDurationSeconds > 0 ? targetDurationSeconds : 1500;
    }

    public FocusSession(Long id, String title, SessionCategory category, Instant startTime, Instant endTime,
                        long targetDurationSeconds, long actualDurationSeconds, SessionStatus status,
                        String notes, int interruptionCount, Instant createdAt) {
        this.id = id;
        this.title = title;
        this.category = category != null ? category : SessionCategory.OTHER;
        this.startTime = startTime;
        this.endTime = endTime;
        this.targetDurationSeconds = targetDurationSeconds;
        this.actualDurationSeconds = actualDurationSeconds;
        this.status = status != null ? status : SessionStatus.PENDING;
        this.notes = notes;
        this.interruptionCount = interruptionCount;
        this.createdAt = createdAt != null ? createdAt : Instant.now();
    }

    /**
     * Calculates the true elapsed focus time in seconds up to the provided timestamp.
     */
    public long getElapsedSeconds(Instant now) {
        if (startTime == null) {
            return actualDurationSeconds;
        }

        if (status == SessionStatus.COMPLETED || status == SessionStatus.CANCELLED) {
            return actualDurationSeconds;
        }

        Instant currentOrEnd = (endTime != null) ? endTime : (now != null ? now : Instant.now());
        if (currentOrEnd.isBefore(startTime)) {
            return 0;
        }

        long rawSeconds = Duration.between(startTime, currentOrEnd).toSeconds();
        long pauseSeconds = accumulatedPauseDuration.toSeconds();

        // If currently paused, account for ongoing pause duration
        if (status == SessionStatus.PAUSED && lastPauseInstant != null) {
            pauseSeconds += Duration.between(lastPauseInstant, currentOrEnd).toSeconds();
        }

        long activeSeconds = Math.max(0, rawSeconds - pauseSeconds);
        return activeSeconds;
    }

    public long getRemainingSeconds(Instant now) {
        long elapsed = getElapsedSeconds(now);
        return Math.max(0, targetDurationSeconds - elapsed);
    }

    public double getProgressFraction(Instant now) {
        if (targetDurationSeconds <= 0) return 1.0;
        return Math.min(1.0, (double) getElapsedSeconds(now) / (double) targetDurationSeconds);
    }

    public void start(Instant startInstant) {
        this.startTime = startInstant != null ? startInstant : Instant.now();
        this.status = SessionStatus.RUNNING;
        this.accumulatedPauseDuration = Duration.ZERO;
        this.lastPauseInstant = null;
    }

    public void pause(Instant pauseInstant) {
        if (this.status == SessionStatus.RUNNING) {
            this.status = SessionStatus.PAUSED;
            this.lastPauseInstant = pauseInstant != null ? pauseInstant : Instant.now();
        }
    }

    public void resume(Instant resumeInstant) {
        if (this.status == SessionStatus.PAUSED) {
            Instant now = resumeInstant != null ? resumeInstant : Instant.now();
            if (this.lastPauseInstant != null && now.isAfter(this.lastPauseInstant)) {
                this.accumulatedPauseDuration = this.accumulatedPauseDuration.plus(
                        Duration.between(this.lastPauseInstant, now)
                );
            }
            this.lastPauseInstant = null;
            this.status = SessionStatus.RUNNING;
        }
    }

    public void complete(Instant completionInstant) {
        Instant now = completionInstant != null ? completionInstant : Instant.now();
        if (this.status == SessionStatus.PAUSED && this.lastPauseInstant != null) {
            this.accumulatedPauseDuration = this.accumulatedPauseDuration.plus(
                    Duration.between(this.lastPauseInstant, now)
            );
            this.lastPauseInstant = null;
        }
        this.endTime = now;
        this.actualDurationSeconds = getElapsedSeconds(now);
        this.status = SessionStatus.COMPLETED;
    }

    public void cancel(Instant cancelInstant) {
        Instant now = cancelInstant != null ? cancelInstant : Instant.now();
        this.endTime = now;
        this.actualDurationSeconds = getElapsedSeconds(now);
        this.status = SessionStatus.CANCELLED;
    }

    public void incrementInterruptionCount() {
        this.interruptionCount++;
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public SessionCategory getCategory() {
        return category;
    }

    public void setCategory(SessionCategory category) {
        this.category = category;
    }

    public Instant getStartTime() {
        return startTime;
    }

    public void setStartTime(Instant startTime) {
        this.startTime = startTime;
    }

    public Instant getEndTime() {
        return endTime;
    }

    public void setEndTime(Instant endTime) {
        this.endTime = endTime;
    }

    public long getTargetDurationSeconds() {
        return targetDurationSeconds;
    }

    public void setTargetDurationSeconds(long targetDurationSeconds) {
        this.targetDurationSeconds = targetDurationSeconds;
    }

    public long getActualDurationSeconds() {
        return actualDurationSeconds;
    }

    public void setActualDurationSeconds(long actualDurationSeconds) {
        this.actualDurationSeconds = actualDurationSeconds;
    }

    public SessionStatus getStatus() {
        return status;
    }

    public void setStatus(SessionStatus status) {
        this.status = status;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public int getInterruptionCount() {
        return interruptionCount;
    }

    public void setInterruptionCount(int interruptionCount) {
        this.interruptionCount = interruptionCount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Duration getAccumulatedPauseDuration() {
        return accumulatedPauseDuration;
    }

    public void setAccumulatedPauseDuration(Duration accumulatedPauseDuration) {
        this.accumulatedPauseDuration = accumulatedPauseDuration != null ? accumulatedPauseDuration : Duration.ZERO;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FocusSession that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "FocusSession{" +
                "id=" + id +
                ", title='" + title + '\'' +
                ", category=" + category +
                ", status=" + status +
                ", target=" + targetDurationSeconds + "s" +
                ", actual=" + actualDurationSeconds + "s" +
                ", interruptions=" + interruptionCount +
                '}';
    }
}
