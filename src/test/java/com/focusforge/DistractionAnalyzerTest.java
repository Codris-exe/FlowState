package com.focusforge;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DistractionAnalyzerTest {

    @Test
    @DisplayName("isDistraction only flags transitions from PRODUCTIVE to DISTRACTING")
    void testDistractionClassification() {
        DistractionAnalyzer analyzer = new DistractionAnalyzer();

        assertThat(analyzer.isDistraction(Activity.Category.PRODUCTIVE, Activity.Category.DISTRACTING)).isTrue();
        assertThat(analyzer.isDistraction(Activity.Category.PRODUCTIVE, Activity.Category.NEUTRAL)).isFalse();
        assertThat(analyzer.isDistraction(Activity.Category.NEUTRAL, Activity.Category.PRODUCTIVE)).isFalse();
        assertThat(analyzer.isDistraction(Activity.Category.DISTRACTING, Activity.Category.PRODUCTIVE)).isFalse();
    }

    @Test
    @DisplayName("resolveCategory identifies common developer tools and distracting apps")
    void testDefaultCategoryHeuristics() {
        DistractionAnalyzer analyzer = new DistractionAnalyzer();

        assertThat(analyzer.resolveCategory("idea64.exe")).isEqualTo(Activity.Category.PRODUCTIVE);
        assertThat(analyzer.resolveCategory("code.exe")).isEqualTo(Activity.Category.PRODUCTIVE);
        assertThat(analyzer.resolveCategory("terminal.exe")).isEqualTo(Activity.Category.PRODUCTIVE);

        assertThat(analyzer.resolveCategory("discord.exe")).isEqualTo(Activity.Category.DISTRACTING);
        assertThat(analyzer.resolveCategory("steam.exe")).isEqualTo(Activity.Category.DISTRACTING);

        assertThat(analyzer.resolveCategory("explorer.exe")).isEqualTo(Activity.Category.NEUTRAL);
    }
}

