package com.flowstate.tracking;

import com.flowstate.model.ApplicationActivity;

/**
 * Listener interface for application activity transitions and tracking state updates.
 */
public interface ActivityChangeListener {

    /**
     * Triggered when the user switches to a different active application or window.
     */
    void onActivityChanged(ApplicationActivity currentActivity, ApplicationActivity previousActivity);

    /**
     * Triggered when background tracking is paused or resumed.
     */
    default void onTrackingStateChanged(boolean trackingEnabled) {}
}
