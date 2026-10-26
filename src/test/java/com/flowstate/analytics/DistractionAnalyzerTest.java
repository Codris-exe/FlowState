package com.flowstate.analytics;

import com.flowstate.model.AppCategory;
import com.flowstate.model.ApplicationActivity;
import com.flowstate.model.FocusSession;
import com.flowstate.model.SessionCategory;
import com.flowstate.service.FocusSessionService;
import com.flowstate.tracking.ActivityTrackingProvider;
import com.flowstate.tracking.ActivityTrackingService;
import com.flowstate.tracking.ForegroundAppInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class DistractionAnalyzerTest {

    @Test
    @DisplayName("isDistraction only flags transitions from PRODUCTIVE to DISTRACTING (DIST-02, DIST-03)")
    void testDistractionClassificationRules() {
        DistractionAnalyzer analyzer = new DistractionAnalyzer();

        // Must be true: productive -> distracting
        assertThat(analyzer.isDistraction(AppCategory.PRODUCTIVE, AppCategory.DISTRACTING)).isTrue();

        // Must be false: neutral transitions (IDE to documentation/terminal)
        assertThat(analyzer.isDistraction(AppCategory.PRODUCTIVE, AppCategory.NEUTRAL)).isFalse();
        assertThat(analyzer.isDistraction(AppCategory.NEUTRAL, AppCategory.PRODUCTIVE)).isFalse();
        assertThat(analyzer.isDistraction(AppCategory.NEUTRAL, AppCategory.NEUTRAL)).isFalse();

        // Must be false: returning to work or remaining distracted
        assertThat(analyzer.isDistraction(AppCategory.DISTRACTING, AppCategory.PRODUCTIVE)).isFalse();
        assertThat(analyzer.isDistraction(AppCategory.DISTRACTING, AppCategory.DISTRACTING)).isFalse();
        assertThat(analyzer.isDistraction(null, AppCategory.DISTRACTING)).isFalse();
    }

    @Test
    @DisplayName("analyzeSession calculates accurate focus percentage and longest uninterrupted streak (DIST-04, DIST-05)")
    void testSessionAnalysisCalculatesFocusPercentageAndLongestStreak() {
        DistractionAnalyzer analyzer = new DistractionAnalyzer();

        Instant t0 = Instant.parse("2026-10-26T10:00:00Z");
        List<ApplicationActivity> activities = new ArrayList<>();

        // 1. Coding in IDE (PRODUCTIVE, 20m = 1200s)
        activities.add(new ApplicationActivity(1L, "idea64.exe", "Main.java", t0, t0.plusSeconds(1200), 1200L, 10L, AppCategory.PRODUCTIVE));

        // 2. Reading API docs in browser (NEUTRAL, 5m = 300s)
        activities.add(new ApplicationActivity(2L, "chrome.exe", "Docs", t0.plusSeconds(1200), t0.plusSeconds(1500), 300L, 10L, AppCategory.NEUTRAL));

        // 3. Social media break (DISTRACTING, 5m = 300s)
        activities.add(new ApplicationActivity(3L, "discord.exe", "Chat", t0.plusSeconds(1500), t0.plusSeconds(1800), 300L, 10L, AppCategory.DISTRACTING));

        // 4. Back to IDE (PRODUCTIVE, 30m = 1800s)
        activities.add(new ApplicationActivity(4L, "idea64.exe", "Feature.java", t0.plusSeconds(1800), t0.plusSeconds(3600), 1800L, 10L, AppCategory.PRODUCTIVE));

        SessionDistractionMetrics metrics = analyzer.analyzeSession(10L, activities, 3600L);

        assertThat(metrics.sessionId()).isEqualTo(10L);
        assertThat(metrics.totalSessionSeconds()).isEqualTo(3600L);
        assertThat(metrics.productiveSeconds()).isEqualTo(3000L); // 1200 + 1800
        assertThat(metrics.neutralSeconds()).isEqualTo(300L);
        assertThat(metrics.distractingSeconds()).isEqualTo(300L);

        // Focus percentage: 3000 / 3600 = 83.333%
        assertThat(metrics.focusPercentage()).isCloseTo(83.33, within(0.1));

        // Streak: Act 1 (1200s) + Act 2 (300s) = 1500s. Interrupted by Act 3. Act 4 = 1800s.
        // Longest streak should be 1800s.
        assertThat(metrics.longestUninterruptedStreakSeconds()).isEqualTo(1800L);
    }

    @Test
    @DisplayName("DistractionAnalyzer triggers FocusSessionService interruption when switching to distracting app")
    void testActivityTrackingServiceTriggersInterruptionOnDistraction() {
        FocusSessionService sessionService = new FocusSessionService(Executors.newSingleThreadScheduledExecutor());
        FocusSession session = sessionService.startSession("Distraction Test", SessionCategory.CODING, 1800);

        DistractionAnalyzer analyzer = new DistractionAnalyzer();
        analyzer.setPreference("idea64.exe", "IntelliJ IDEA", AppCategory.PRODUCTIVE, false);
        analyzer.setPreference("discord.exe", "Discord", AppCategory.DISTRACTING, false);
        analyzer.setPreference("chrome.exe", "Google Chrome", AppCategory.NEUTRAL, false);

        AtomicReference<ForegroundAppInfo> currentApp = new AtomicReference<>(
                new ForegroundAppInfo("idea64.exe", "Main.java", Instant.now())
        );

        ActivityTrackingProvider stubProvider = new ActivityTrackingProvider() {
            @Override
            public Optional<ForegroundAppInfo> getForegroundApp() {
                return Optional.ofNullable(currentApp.get());
            }

            @Override
            public boolean isSupported() { return true; }
        };

        ActivityTrackingService trackingService = new ActivityTrackingService(
                stubProvider, null, sessionService, Executors.newSingleThreadScheduledExecutor(), false, 1000L
        );
        trackingService.setDistractionAnalyzer(analyzer);

        // 1. Initial sample: productive app
        trackingService.pollForegroundApp();
        assertThat(session.getInterruptionCount()).isEqualTo(0);

        // 2. Switch to distracting app: Discord
        currentApp.set(new ForegroundAppInfo("discord.exe", "Chat", Instant.now().plusSeconds(60)));
        trackingService.pollForegroundApp();
        assertThat(session.getInterruptionCount()).isEqualTo(1); // Interruption incremented!

        // 3. Switch to neutral app: Chrome documentation
        currentApp.set(new ForegroundAppInfo("chrome.exe", "StackOverflow", Instant.now().plusSeconds(120)));
        trackingService.pollForegroundApp();
        assertThat(session.getInterruptionCount()).isEqualTo(1); // No new interruption from distracting -> neutral

        sessionService.shutdown();
        trackingService.shutdown();
    }
}
