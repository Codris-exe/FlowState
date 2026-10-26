package com.flowstate.ui;

import com.flowstate.model.AppCategory;
import com.flowstate.model.ApplicationActivity;
import com.flowstate.ui.model.AppUsageSummary;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class DashboardControllerTest {

    @Test
    @DisplayName("AppUsageSummary correctly formats seconds into human readable duration")
    void testAppUsageSummaryFormatting() {
        AppUsageSummary shortApp = new AppUsageSummary("notepad.exe", 45L, AppCategory.NEUTRAL);
        assertThat(shortApp.getFormattedDuration()).isEqualTo("45s");

        AppUsageSummary mediumApp = new AppUsageSummary("code.exe", 1530L, AppCategory.PRODUCTIVE);
        assertThat(mediumApp.getFormattedDuration()).isEqualTo("25m 30s");

        AppUsageSummary longApp = new AppUsageSummary("idea64.exe", 7500L, AppCategory.PRODUCTIVE);
        assertThat(longApp.getFormattedDuration()).isEqualTo("2h 5m");
    }

    @Test
    @DisplayName("ApplicationActivity domain model properties format properly for UI presentation")
    void testApplicationActivityDisplayProperties() {
        ApplicationActivity activity = new ApplicationActivity(
                "idea64.exe",
                "FlowState - Main.java",
                Instant.now().minusSeconds(300),
                null,
                AppCategory.PRODUCTIVE
        );
        activity.setEndedAt(Instant.now());
        activity.setDurationSeconds(300L);

        assertThat(activity.getApplicationName()).isEqualTo("idea64.exe");
        assertThat(activity.getWindowTitle()).isEqualTo("FlowState - Main.java");
        assertThat(activity.getDurationSeconds()).isEqualTo(300L);
        assertThat(activity.getActivityCategory()).isEqualTo(AppCategory.PRODUCTIVE);
    }
}
