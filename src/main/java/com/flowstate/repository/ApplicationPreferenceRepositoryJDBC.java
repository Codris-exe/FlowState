package com.flowstate.repository;

import com.flowstate.config.DatabaseManager;
import com.flowstate.model.AppCategory;
import com.flowstate.model.ApplicationPreference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * JDBC implementation of ApplicationPreferenceRepository using prepared statements.
 */
public class ApplicationPreferenceRepositoryJDBC implements ApplicationPreferenceRepository {
    private static final Logger logger = LoggerFactory.getLogger(ApplicationPreferenceRepositoryJDBC.class);
    private final DatabaseManager databaseManager;

    public ApplicationPreferenceRepositoryJDBC() {
        this(DatabaseManager.getInstance());
    }

    public ApplicationPreferenceRepositoryJDBC(DatabaseManager databaseManager) {
        this.databaseManager = Objects.requireNonNull(databaseManager, "databaseManager must not be null");
    }

    @Override
    public ApplicationPreference save(ApplicationPreference preference) throws SQLException {
        Objects.requireNonNull(preference, "preference must not be null");

        String sql = """
            INSERT INTO application_preferences
            (application_identifier, display_name, category, is_excluded)
            VALUES (?, ?, ?, ?)
            """;

        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            String identifier = preference.getApplicationIdentifier() != null
                    ? preference.getApplicationIdentifier().trim().toLowerCase() : "";
            stmt.setString(1, identifier);
            stmt.setString(2, preference.getDisplayName() != null ? preference.getDisplayName().trim() : identifier);
            stmt.setString(3, preference.getCategory() != null ? preference.getCategory().name() : AppCategory.NEUTRAL.name());
            stmt.setBoolean(4, preference.isExcluded());

            int affected = stmt.executeUpdate();
            if (affected == 0) {
                throw new SQLException("Saving preference failed, no rows affected.");
            }

            try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    preference.setId(generatedKeys.getLong(1));
                }
            }
            return preference;
        }
    }

    @Override
    public boolean update(ApplicationPreference preference) throws SQLException {
        Objects.requireNonNull(preference, "preference must not be null");
        if (preference.getApplicationIdentifier() == null) {
            throw new IllegalArgumentException("Cannot update preference without applicationIdentifier");
        }

        String sql = """
            UPDATE application_preferences
            SET display_name = ?, category = ?, is_excluded = ?
            WHERE application_identifier = ?
            """;

        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, preference.getDisplayName());
            stmt.setString(2, preference.getCategory() != null ? preference.getCategory().name() : AppCategory.NEUTRAL.name());
            stmt.setBoolean(3, preference.isExcluded());
            stmt.setString(4, preference.getApplicationIdentifier().trim().toLowerCase());

            return stmt.executeUpdate() > 0;
        }
    }

    @Override
    public Optional<ApplicationPreference> findByIdentifier(String identifier) throws SQLException {
        if (identifier == null || identifier.isBlank()) return Optional.empty();

        String sql = "SELECT * FROM application_preferences WHERE application_identifier = ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, identifier.trim().toLowerCase());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        }
        return Optional.empty();
    }

    @Override
    public List<ApplicationPreference> findAll() throws SQLException {
        String sql = "SELECT * FROM application_preferences ORDER BY display_name ASC";
        List<ApplicationPreference> list = new ArrayList<>();

        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                list.add(mapRow(rs));
            }
        }
        return list;
    }

    @Override
    public boolean deleteByIdentifier(String identifier) throws SQLException {
        if (identifier == null || identifier.isBlank()) return false;

        String sql = "DELETE FROM application_preferences WHERE application_identifier = ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, identifier.trim().toLowerCase());
            return stmt.executeUpdate() > 0;
        }
    }

    @Override
    public void upsert(String identifier, String displayName, AppCategory category, boolean isExcluded) throws SQLException {
        if (identifier == null || identifier.isBlank()) return;

        String cleanIdentifier = identifier.trim().toLowerCase();
        String cleanDisplayName = (displayName != null && !displayName.isBlank()) ? displayName.trim() : cleanIdentifier;
        AppCategory cleanCategory = category != null ? category : AppCategory.NEUTRAL;

        String sql = """
            INSERT INTO application_preferences (application_identifier, display_name, category, is_excluded)
            VALUES (?, ?, ?, ?)
            ON CONFLICT (application_identifier)
            DO UPDATE SET
                display_name = EXCLUDED.display_name,
                category = EXCLUDED.category,
                is_excluded = EXCLUDED.is_excluded
            """;

        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, cleanIdentifier);
            stmt.setString(2, cleanDisplayName);
            stmt.setString(3, cleanCategory.name());
            stmt.setBoolean(4, isExcluded);

            stmt.executeUpdate();
            logger.debug("Upserted preference for app '{}' (category={}, excluded={})", cleanIdentifier, cleanCategory, isExcluded);
        }
    }

    @Override
    public List<String> findExcludedIdentifiers() throws SQLException {
        String sql = "SELECT application_identifier FROM application_preferences WHERE is_excluded = TRUE";
        List<String> list = new ArrayList<>();

        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                list.add(rs.getString("application_identifier").toLowerCase());
            }
        }
        return list;
    }

    public ApplicationPreference mapRow(ResultSet rs) throws SQLException {
        Long id = rs.getLong("id");
        String identifier = rs.getString("application_identifier");
        String displayName = rs.getString("display_name");
        String categoryStr = rs.getString("category");
        boolean isExcluded = rs.getBoolean("is_excluded");

        return new ApplicationPreference(id, identifier, displayName, AppCategory.fromString(categoryStr), isExcluded);
    }
}
