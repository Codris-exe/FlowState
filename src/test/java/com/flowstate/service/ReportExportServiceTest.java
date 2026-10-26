package com.flowstate.service;

import com.flowstate.model.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReportExportServiceTest {

    private final ReportExportService exportService = new ReportExportService();

    @Test
    @DisplayName("escapeRfc4180 properly escapes commas, quotes, and newlines")
    void testRfc4180Escaping() {
        // Plain text
        assertThat(exportService.escapeRfc4180("SimpleText")).isEqualTo("SimpleText");

        // Comma
        assertThat(exportService.escapeRfc4180("Coding, Algorithms")).isEqualTo("\"Coding, Algorithms\"");

        // Embedded double quote
        assertThat(exportService.escapeRfc4180("Focus \"Deep\" Block")).isEqualTo("\"Focus \"\"Deep\"\" Block\"");

        // Newline
        assertThat(exportService.escapeRfc4180("Line1\nLine2")).isEqualTo("\"Line1\nLine2\"");
    }

    @Test
    @DisplayName("exportSessionsToCsv creates valid RFC 4180 CSV file using Java NIO")
    void testExportSessionsCreatesValidCsv(@TempDir Path tempDir) throws IOException {
        Path csvFile = tempDir.resolve("focus_sessions_export.csv");

        FocusSession s1 = new FocusSession(101L, "Write Algorithms, Part 1", SessionCategory.CODING,
                Instant.parse("2026-10-26T10:00:00Z"), Instant.parse("2026-10-26T10:30:00Z"),
                1800, 1800, SessionStatus.COMPLETED, "Done with \"graphs\"!", 1, Instant.now());

        exportService.exportSessionsToCsv(csvFile, List.of(s1), true);

        assertThat(Files.exists(csvFile)).isTrue();
        List<String> lines = Files.readAllLines(csvFile);
        assertThat(lines.size()).isGreaterThanOrEqualTo(2);
        assertThat(lines.get(0)).contains("id,title,category");
        assertThat(lines.get(1)).contains("101");
        assertThat(lines.get(1)).contains("\"Write Algorithms, Part 1\"");
        assertThat(lines.get(1)).contains("\"Done with \"\"graphs\"\"!\"");
    }

    @Test
    @DisplayName("exportSessionsToCsv protects against accidental overwrites unless overwrite=true")
    void testOverwriteProtectionThrowsFileAlreadyExistsException(@TempDir Path tempDir) throws IOException {
        Path csvFile = tempDir.resolve("existing_file.csv");
        Files.writeString(csvFile, "dummy existing content");

        FocusSession s1 = new FocusSession("Session", SessionCategory.OTHER, 1500L);

        assertThatThrownBy(() -> exportService.exportSessionsToCsv(csvFile, List.of(s1), false))
                .isInstanceOf(FileAlreadyExistsException.class);
    }

    @Test
    @DisplayName("Preferences export and import round-trip accurately")
    void testPreferenceExportAndImportRoundTrip(@TempDir Path tempDir) throws IOException {
        Path prefFile = tempDir.resolve("prefs.csv");

        ApplicationPreference p1 = new ApplicationPreference("code.exe", "VS Code", AppCategory.PRODUCTIVE, false);
        ApplicationPreference p2 = new ApplicationPreference("discord.exe", "Discord", AppCategory.DISTRACTING, false);
        ApplicationPreference p3 = new ApplicationPreference("keepass.exe", "KeePass", AppCategory.NEUTRAL, true);

        exportService.exportPreferencesToCsv(prefFile, List.of(p1, p2, p3), true);

        List<ApplicationPreference> imported = exportService.importPreferencesFromCsv(prefFile);
        assertThat(imported).hasSize(3);
        assertThat(imported.get(0).getApplicationIdentifier()).isEqualTo("code.exe");
        assertThat(imported.get(0).getCategory()).isEqualTo(AppCategory.PRODUCTIVE);
        assertThat(imported.get(2).isExcluded()).isTrue();
    }
}
