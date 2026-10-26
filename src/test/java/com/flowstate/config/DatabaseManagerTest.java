package com.flowstate.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DatabaseManagerTest {

    @Test
    @DisplayName("AppConfig loads default database settings")
    void testAppConfigDefaults() {
        AppConfig config = new AppConfig();
        assertThat(config.getDbUrl()).isNotNull().contains("jdbc:postgresql:");
        assertThat(config.getDbUser()).isNotNull();
        assertThat(config.getMaxPoolSize()).isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("DatabaseManager produces informative recovery diagnostics on failure")
    void testDiagnosticsOnFailure() {
        AppConfig badConfig = new AppConfig();
        // Point to an invalid port that will definitely fail immediately
        badConfig.setDbUrl("jdbc:postgresql://localhost:54329/nonexistent_flowstate_test_db");
        badConfig.setConnectionTimeoutMs(1000);

        DatabaseManager dbManager = new DatabaseManager(badConfig);
        boolean initResult = dbManager.initialize();

        assertThat(initResult).isFalse();
        assertThat(dbManager.isHealthy()).isFalse();

        String diagnostics = dbManager.getDiagnostics();
        assertThat(diagnostics).isNotNull();
        assertThat(diagnostics).contains("=== FlowState Database Diagnostics ===");
        assertThat(diagnostics).contains("Recommended Recovery Steps");
        assertThat(diagnostics).contains("services.msc");
        assertThat(diagnostics).contains("FLOWSTATE_DB_URL");
    }
}
