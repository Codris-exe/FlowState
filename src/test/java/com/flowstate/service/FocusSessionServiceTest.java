package com.flowstate.service;

import com.flowstate.model.FocusSession;
import com.flowstate.model.SessionCategory;
import com.flowstate.model.SessionStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FocusSessionServiceTest {

    private FocusSessionService sessionService;

    @BeforeEach
    void setUp() {
        sessionService = new FocusSessionService();
    }

    @AfterEach
    void tearDown() {
        if (sessionService.hasActiveSession()) {
            sessionService.cancelSession();
        }
        sessionService.shutdown();
    }

    @Test
    @DisplayName("Starting a focus session initializes running state and notifies listener")
    void testStartSession() {
        AtomicBoolean startedNotified = new AtomicBoolean(false);
        sessionService.addListener(new FocusSessionListener() {
            @Override
            public void onSessionStarted(FocusSession session) {
                startedNotified.set(true);
            }
        });

        FocusSession session = sessionService.startSession("Writing Paper", SessionCategory.WRITING, 1800);

        assertThat(session).isNotNull();
        assertThat(session.getTitle()).isEqualTo("Writing Paper");
        assertThat(session.getCategory()).isEqualTo(SessionCategory.WRITING);
        assertThat(session.getTargetDurationSeconds()).isEqualTo(1800);
        assertThat(session.getStatus()).isEqualTo(SessionStatus.RUNNING);
        assertThat(session.getStartTime()).isNotNull();
        assertThat(sessionService.hasActiveSession()).isTrue();
        assertThat(sessionService.isRunning()).isTrue();
        assertThat(startedNotified.get()).isTrue();
    }

    @Test
    @DisplayName("Enforce single active focus session concurrency guard")
    void testSingleActiveSessionEnforcement() {
        sessionService.startSession("First Session", SessionCategory.CODING, 1500);

        assertThatThrownBy(() -> sessionService.startSession("Second Session", SessionCategory.STUDYING, 1200))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already in progress");
    }

    @Test
    @DisplayName("Pause and resume lifecycle transitions update session status")
    void testPauseAndResume() {
        FocusSession session = sessionService.startSession("Coding Feature", SessionCategory.CODING, 1500);

        sessionService.pauseSession();
        assertThat(session.getStatus()).isEqualTo(SessionStatus.PAUSED);
        assertThat(sessionService.isPaused()).isTrue();
        assertThat(sessionService.isRunning()).isFalse();

        sessionService.resumeSession();
        assertThat(session.getStatus()).isEqualTo(SessionStatus.RUNNING);
        assertThat(sessionService.isRunning()).isTrue();
        assertThat(sessionService.isPaused()).isFalse();
    }

    @Test
    @DisplayName("Completing session saves notes and clears active session")
    void testCompleteSession() {
        sessionService.startSession("Exam Prep", SessionCategory.STUDYING, 2400);

        FocusSession completed = sessionService.completeSession("Covered chapters 4 to 6.");

        assertThat(completed.getStatus()).isEqualTo(SessionStatus.COMPLETED);
        assertThat(completed.getEndTime()).isNotNull();
        assertThat(completed.getNotes()).isEqualTo("Covered chapters 4 to 6.");
        assertThat(sessionService.hasActiveSession()).isFalse();
    }

    @Test
    @DisplayName("Cancelling session sets status to CANCELLED and clears active session")
    void testCancelSession() {
        sessionService.startSession("Distracted Run", SessionCategory.OTHER, 1500);

        FocusSession cancelled = sessionService.cancelSession();

        assertThat(cancelled.getStatus()).isEqualTo(SessionStatus.CANCELLED);
        assertThat(cancelled.getEndTime()).isNotNull();
        assertThat(sessionService.hasActiveSession()).isFalse();
    }

    @Test
    @DisplayName("Timestamp math is accurate and accounts for pause periods without clock drift")
    void testTimerMathWithoutClockDrift() {
        FocusSession session = new FocusSession("Math Test", SessionCategory.CODING, 1500);
        Instant t0 = Instant.parse("2026-10-26T10:00:00Z");
        session.start(t0);

        // 10 minutes (600s) later
        Instant t1 = t0.plus(Duration.ofMinutes(10));
        assertThat(session.getElapsedSeconds(t1)).isEqualTo(600);
        assertThat(session.getRemainingSeconds(t1)).isEqualTo(900);

        // Pause at 10 minutes
        session.pause(t1);

        // Still paused 5 minutes later (t2) -> elapsed should still be 600s
        Instant t2 = t1.plus(Duration.ofMinutes(5));
        assertThat(session.getElapsedSeconds(t2)).isEqualTo(600);

        // Resume at t2
        session.resume(t2);

        // Check at t3 (15 minutes after resume, total 30 min from start, 5 min paused -> 25 min active = 1500s)
        Instant t3 = t2.plus(Duration.ofMinutes(15));
        assertThat(session.getElapsedSeconds(t3)).isEqualTo(1500);
        assertThat(session.getRemainingSeconds(t3)).isEqualTo(0);
        assertThat(session.getProgressFraction(t3)).isEqualTo(1.0);
    }

    @Test
    @DisplayName("Interruption count increments during running session")
    void testInterruptionRecording() {
        sessionService.startSession("Deep Focus", SessionCategory.CODING, 1500);
        assertThat(sessionService.getActiveSession().getInterruptionCount()).isEqualTo(0);

        sessionService.recordInterruption();
        sessionService.recordInterruption();
        assertThat(sessionService.getActiveSession().getInterruptionCount()).isEqualTo(2);

        // Paused session should ignore interruptions
        sessionService.pauseSession();
        sessionService.recordInterruption();
        assertThat(sessionService.getActiveSession().getInterruptionCount()).isEqualTo(2);
    }
}
