package com.flowstate.tracking;

import java.util.Optional;

/**
 * Strategy interface for sampling the currently active foreground desktop window.
 */
public interface ActivityTrackingProvider {

    /**
     * Samples the currently active foreground application window.
     *
     * @return Optional containing the active application info, or empty if no active window is focused
     */
    Optional<ForegroundAppInfo> getForegroundApp();

    /**
     * Checks if this provider is supported on the current operating system.
     *
     * @return true if supported, false otherwise
     */
    boolean isSupported();
}
