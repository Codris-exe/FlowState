package com.flowstate.ui;

import com.flowstate.model.AppCategory;
import com.flowstate.model.FocusSession;
import com.flowstate.model.SessionCategory;
import com.flowstate.model.SessionStatus;
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
    @DisplayName("FocusSession domain model properties format properly for UI presentation")
    void testFocusSessionDisplayProperties() {
        FocusSession session = new FocusSession(
                55L,
                "Deep Architecture Design",
                SessionCategory.CODING,
                Instant.now(),
                Instant.now().plusSeconds(1800),
                1800L,
                1750L,
                SessionStatus.COMPLETED,
                "Architected repositories",
                1,
                Instant.now()
        );

        assertThat(session.getTitle()).isEqualTo("Deep Architecture Design");
        assertThat(session.getCategory()).isEqualTo(SessionCategory.CODING);
        assertThat(session.getActualDurationSeconds()).isEqualTo(1750L);
        assertThat(session.getInterruptionCount()).isEqualTo(1);
        assertThat(session.getStatus()).isEqualTo(SessionStatus.COMPLETED);
    }
}
