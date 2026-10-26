package com.flowstate.service;

import com.flowstate.model.AppCategory;
import com.flowstate.model.ApplicationActivity;
import com.flowstate.model.ApplicationPreference;
import com.flowstate.model.FocusSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Report export engine using Java NIO and RFC 4180 compliant CSV formatting.
 */
public class ReportExportService {
    private static final Logger logger = LoggerFactory.getLogger(ReportExportService.class);
    private static final DateTimeFormatter ISO_FMT = DateTimeFormatter.ISO_OFFSET_DATE_TIME.withZone(ZoneId.systemDefault());

    /**
     * Exports a list of focus sessions to a CSV file.
     *
     * @throws FileAlreadyExistsException if target exists and overwrite is false
     */
    public void exportSessionsToCsv(Path targetFile, List<FocusSession> sessions, boolean overwrite) throws IOException {
        checkFileSafety(targetFile, overwrite);

        try (BufferedWriter writer = Files.newBufferedWriter(targetFile, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {

            // Header
            writer.write("id,title,category,start_time,end_time,target_duration_seconds,actual_duration_seconds,status,notes,interruption_count,created_at\r\n");

            for (FocusSession s : sessions) {
                String row = String.join(",",
                        String.valueOf(s.getId() != null ? s.getId() : ""),
                        escapeRfc4180(s.getTitle()),
                        escapeRfc4180(s.getCategory() != null ? s.getCategory().name() : ""),
                        formatInstant(s.getStartTime()),
                        formatInstant(s.getEndTime()),
                        String.valueOf(s.getTargetDurationSeconds()),
                        String.valueOf(s.getActualDurationSeconds()),
                        escapeRfc4180(s.getStatus() != null ? s.getStatus().name() : ""),
                        escapeRfc4180(s.getNotes()),
                        String.valueOf(s.getInterruptionCount()),
                        formatInstant(s.getCreatedAt())
                );
                writer.write(row);
                writer.write("\r\n");
            }
        }
        logger.info("Successfully exported {} sessions to CSV at: {}", sessions.size(), targetFile);
    }

    /**
     * Exports application activities to a CSV file.
     */
    public void exportActivitiesToCsv(Path targetFile, List<ApplicationActivity> activities, boolean overwrite) throws IOException {
        checkFileSafety(targetFile, overwrite);

        try (BufferedWriter writer = Files.newBufferedWriter(targetFile, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {

            // Header
            writer.write("id,application_name,window_title,started_at,ended_at,duration_seconds,session_id,activity_category\r\n");

            for (ApplicationActivity a : activities) {
                String row = String.join(",",
                        String.valueOf(a.getId() != null ? a.getId() : ""),
                        escapeRfc4180(a.getApplicationName()),
                        escapeRfc4180(a.getWindowTitle()),
                        formatInstant(a.getStartedAt()),
                        formatInstant(a.getEndedAt()),
                        String.valueOf(a.getDurationSeconds()),
                        String.valueOf(a.getSessionId() != null ? a.getSessionId() : ""),
                        escapeRfc4180(a.getActivityCategory() != null ? a.getActivityCategory().name() : "")
                );
                writer.write(row);
                writer.write("\r\n");
            }
        }
        logger.info("Successfully exported {} activities to CSV at: {}", activities.size(), targetFile);
    }

    /**
     * Exports application preferences to CSV for backup/sharing.
     */
    public void exportPreferencesToCsv(Path targetFile, List<ApplicationPreference> preferences, boolean overwrite) throws IOException {
        checkFileSafety(targetFile, overwrite);

        try (BufferedWriter writer = Files.newBufferedWriter(targetFile, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {

            writer.write("application_identifier,display_name,category,is_excluded\r\n");

            for (ApplicationPreference p : preferences) {
                String row = String.join(",",
                        escapeRfc4180(p.getApplicationIdentifier()),
                        escapeRfc4180(p.getDisplayName()),
                        escapeRfc4180(p.getCategory() != null ? p.getCategory().name() : AppCategory.NEUTRAL.name()),
                        String.valueOf(p.isExcluded())
                );
                writer.write(row);
                writer.write("\r\n");
            }
        }
    }

    /**
     * Imports application preferences from CSV.
     */
    public List<ApplicationPreference> importPreferencesFromCsv(Path sourceFile) throws IOException {
        if (!Files.exists(sourceFile)) {
            throw new NoSuchFileException("Preferences file does not exist: " + sourceFile);
        }

        List<ApplicationPreference> list = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(sourceFile, StandardCharsets.UTF_8)) {
            String line = reader.readLine(); // Skip header
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;
                String[] parts = line.split(",", -1);
                if (parts.length >= 4) {
                    String id = unescape(parts[0].trim());
                    String name = unescape(parts[1].trim());
                    AppCategory cat = AppCategory.fromString(parts[2].trim());
                    boolean excluded = Boolean.parseBoolean(parts[3].trim());
                    list.add(new ApplicationPreference(id, name, cat, excluded));
                }
            }
        }
        return list;
    }

    private void checkFileSafety(Path targetFile, boolean overwrite) throws IOException {
        if (targetFile.getParent() != null && !Files.exists(targetFile.getParent())) {
            Files.createDirectories(targetFile.getParent());
        }
        if (Files.exists(targetFile) && !overwrite) {
            throw new FileAlreadyExistsException(targetFile.toString());
        }
    }

    /**
     * Escapes a field according to RFC 4180 rules.
     */
    public String escapeRfc4180(String field) {
        if (field == null) {
            return "";
        }
        boolean mustQuote = field.contains(",") || field.contains("\"") || field.contains("\n") || field.contains("\r");
        if (!mustQuote) {
            return field;
        }

        String escaped = field.replace("\"", "\"\"");
        return "\"" + escaped + "\"";
    }

    private String unescape(String text) {
        if (text.startsWith("\"") && text.endsWith("\"") && text.length() >= 2) {
            String inner = text.substring(1, text.length() - 1);
            return inner.replace("\"\"", "\"");
        }
        return text;
    }

    private String formatInstant(Instant instant) {
        if (instant == null) return "";
        return ISO_FMT.format(instant);
    }
}
