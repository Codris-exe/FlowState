package com.flowstate.analytics;

import com.flowstate.model.AppCategory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DistractionAnalyzerTest {

    @Test
    @DisplayName("isDistraction only flags transitions from PRODUCTIVE to DISTRACTING")
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
    @DisplayName("resolveCategory uses default heuristic keywords for common developer and distracting tools")
    void testDefaultCategoryHeuristics() {
        DistractionAnalyzer analyzer = new DistractionAnalyzer();

        assertThat(analyzer.resolveCategory("idea64.exe")).isEqualTo(AppCategory.PRODUCTIVE);
        assertThat(analyzer.resolveCategory("code.exe")).isEqualTo(AppCategory.PRODUCTIVE);
        assertThat(analyzer.resolveCategory("terminal.exe")).isEqualTo(AppCategory.PRODUCTIVE);

        assertThat(analyzer.resolveCategory("discord.exe")).isEqualTo(AppCategory.DISTRACTING);
        assertThat(analyzer.resolveCategory("spotify.exe")).isEqualTo(AppCategory.DISTRACTING);
        assertThat(analyzer.resolveCategory("steam.exe")).isEqualTo(AppCategory.DISTRACTING);

        assertThat(analyzer.resolveCategory("explorer.exe")).isEqualTo(AppCategory.NEUTRAL);
    }
}
