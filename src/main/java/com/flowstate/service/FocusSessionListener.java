package com.flowstate.service;

import com.flowstate.model.FocusSession;

/**
 * Event listener interface for receiving timer ticks and focus session state changes.
 */
public interface FocusSessionListener {

    /**
     * Invoked when a new focus session starts.
     */
    default void onSessionStarted(FocusSession session) {}

    /**
     * Invoked when the active focus session is paused.
     */
    default void onSessionPaused(FocusSession session) {}

    /**
     * Invoked when a paused focus session resumes running.
     */
    default void onSessionResumed(FocusSession session) {}

    /**
     * Invoked when a focus session completes successfully.
     */
    default void onSessionCompleted(FocusSession session) {}

    /**
     * Invoked when a focus session is cancelled or discarded early.
     */
    default void onSessionCancelled(FocusSession session) {}

    /**
     * Invoked on every timer tick (default once per second) while session is active.
     *
     * @param session the active session
     * @param elapsedSeconds total focused seconds elapsed so far
     * @param remainingSeconds remaining seconds until target duration
     */
    default void onTimerTick(FocusSession session, long elapsedSeconds, long remainingSeconds) {}
}
