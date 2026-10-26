package com.flowstate.tracking;

import java.time.Instant;
import java.util.Objects;

/**
 * Immutable value object holding information about an active foreground application window.
 */
public record ForegroundAppInfo(
        String processName,
        String windowTitle,
        Instant timestamp
) {
    public ForegroundAppInfo {
        processName = (processName != null && !processName.isBlank()) ? processName.trim() : "Unknown";
        windowTitle = (windowTitle != null) ? windowTitle.trim() : "";
        if (windowTitle.length() > 500) {
            windowTitle = windowTitle.substring(0, 497) + "...";
        }
        timestamp = (timestamp != null) ? timestamp : Instant.now();
    }

    public static ForegroundAppInfo of(String processName, String windowTitle) {
        return new ForegroundAppInfo(processName, windowTitle, Instant.now());
    }

    public boolean isSameApp(ForegroundAppInfo other) {
        if (other == null) return false;
        return Objects.equals(this.processName.toLowerCase(), other.processName.toLowerCase());
    }

    public boolean isSameWindow(ForegroundAppInfo other) {
        if (other == null) return false;
        return isSameApp(other) && Objects.equals(this.windowTitle, other.windowTitle);
    }
}
