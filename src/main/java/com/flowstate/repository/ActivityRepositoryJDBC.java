package com.flowstate.repository;

import com.flowstate.config.DatabaseManager;
import com.flowstate.model.AppCategory;
import com.flowstate.model.ApplicationActivity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.Instant;
import java.util.*;

/**
 * JDBC implementation of ActivityRepository using prepared statements and connection pooling.
 */
public class ActivityRepositoryJDBC implements ActivityRepository {
    private static final Logger logger = LoggerFactory.getLogger(ActivityRepositoryJDBC.class);
    private final DatabaseManager databaseManager;

    public ActivityRepositoryJDBC() {
        this(DatabaseManager.getInstance());
    }

    public ActivityRepositoryJDBC(DatabaseManager databaseManager) {
        this.databaseManager = Objects.requireNonNull(databaseManager, "databaseManager must not be null");
    }

    @Override
    public ApplicationActivity save(ApplicationActivity activity) throws SQLException {
        Objects.requireNonNull(activity, "activity must not be null");

        String sql = """
            INSERT INTO application_activity
            (application_name, window_title, started_at, ended_at, duration_seconds, session_id, activity_category)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """;

        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            stmt.setString(1, activity.getApplicationName() != null ? activity.getApplicationName() : "Unknown");
            stmt.setString(2, activity.getWindowTitle());
            stmt.setTimestamp(3, Timestamp.from(activity.getStartedAt() != null ? activity.getStartedAt() : Instant.now()));

            if (activity.getEndedAt() != null) {
                stmt.setTimestamp(4, Timestamp.from(activity.getEndedAt()));
            } else {
                stmt.setNull(4, Types.TIMESTAMP_WITH_TIMEZONE);
            }

            stmt.setLong(5, activity.getDurationSeconds());

            if (activity.getSessionId() != null) {
                stmt.setLong(6, activity.getSessionId());
            } else {
                stmt.setNull(6, Types.BIGINT);
            }

            stmt.setString(7, activity.getActivityCategory() != null ? activity.getActivityCategory().name() : AppCategory.NEUTRAL.name());

            int affectedRows = stmt.executeUpdate();
            if (affectedRows == 0) {
                throw new SQLException("Saving activity failed, no rows affected.");
            }

            try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    activity.setId(generatedKeys.getLong(1));
                }
            }

            logger.debug("Saved application activity id: {} for app: '{}'", activity.getId(), activity.getApplicationName());
            return activity;
        }
    }

    @Override
    public boolean update(ApplicationActivity activity) throws SQLException {
        Objects.requireNonNull(activity, "activity must not be null");
        if (activity.getId() == null) {
            throw new IllegalArgumentException("Cannot update activity without an ID");
        }

        String sql = """
            UPDATE application_activity
            SET ended_at = ?, duration_seconds = ?, activity_category = ?, session_id = ?
            WHERE id = ?
            """;

        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            if (activity.getEndedAt() != null) {
                stmt.setTimestamp(1, Timestamp.from(activity.getEndedAt()));
            } else {
                stmt.setNull(1, Types.TIMESTAMP_WITH_TIMEZONE);
            }

            stmt.setLong(2, activity.getDurationSeconds());
            stmt.setString(3, activity.getActivityCategory() != null ? activity.getActivityCategory().name() : AppCategory.NEUTRAL.name());

            if (activity.getSessionId() != null) {
                stmt.setLong(4, activity.getSessionId());
            } else {
                stmt.setNull(4, Types.BIGINT);
            }

            stmt.setLong(5, activity.getId());

            int rows = stmt.executeUpdate();
            return rows > 0;
        }
    }

    @Override
    public Optional<ApplicationActivity> findById(Long id) throws SQLException {
        if (id == null) return Optional.empty();

        String sql = "SELECT * FROM application_activity WHERE id = ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        }
        return Optional.empty();
    }

    @Override
    public List<ApplicationActivity> findBySessionId(Long sessionId) throws SQLException {
        if (sessionId == null) return Collections.emptyList();

        String sql = "SELECT * FROM application_activity WHERE session_id = ? ORDER BY started_at ASC";
        List<ApplicationActivity> list = new ArrayList<>();

        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, sessionId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        }
        return list;
    }

    @Override
    public List<ApplicationActivity> findByDateRange(Instant start, Instant end) throws SQLException {
        String sql = """
            SELECT * FROM application_activity
            WHERE started_at >= ? AND started_at <= ?
            ORDER BY started_at DESC
            """;
        List<ApplicationActivity> list = new ArrayList<>();

        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setTimestamp(1, Timestamp.from(start != null ? start : Instant.EPOCH));
            stmt.setTimestamp(2, Timestamp.from(end != null ? end : Instant.now()));

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        }
        return list;
    }

    @Override
    public List<ApplicationActivity> findRecent(int limit) throws SQLException {
        int safeLimit = limit > 0 ? limit : 20;
        String sql = "SELECT * FROM application_activity ORDER BY started_at DESC LIMIT ?";
        List<ApplicationActivity> list = new ArrayList<>();

        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, safeLimit);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        }
        return list;
    }

    @Override
    public long deleteBySessionId(Long sessionId) throws SQLException {
        if (sessionId == null) return 0;

        String sql = "DELETE FROM application_activity WHERE session_id = ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, sessionId);
            return stmt.executeUpdate();
        }
    }

    @Override
    public long clearAll() throws SQLException {
        String sql = "DELETE FROM application_activity";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            return stmt.executeUpdate();
        }
    }

    @Override
    public Map<String, Long> getTopApplicationsDuration(Instant start, Instant end, int limit) throws SQLException {
        int safeLimit = limit > 0 ? limit : 5;
        String sql = """
            SELECT application_name, COALESCE(SUM(duration_seconds), 0) AS total_seconds
            FROM application_activity
            WHERE started_at >= ? AND started_at <= ?
            GROUP BY application_name
            ORDER BY total_seconds DESC
            LIMIT ?
            """;
        Map<String, Long> result = new LinkedHashMap<>();

        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setTimestamp(1, Timestamp.from(start != null ? start : Instant.EPOCH));
            stmt.setTimestamp(2, Timestamp.from(end != null ? end : Instant.now()));
            stmt.setInt(3, safeLimit);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    result.put(rs.getString("application_name"), rs.getLong("total_seconds"));
                }
            }
        }
        return result;
    }

    public ApplicationActivity mapRow(ResultSet rs) throws SQLException {
        Long id = rs.getLong("id");
        String appName = rs.getString("application_name");
        String title = rs.getString("window_title");

        Timestamp startTs = rs.getTimestamp("started_at");
        Instant startedAt = startTs != null ? startTs.toInstant() : Instant.now();

        Timestamp endTs = rs.getTimestamp("ended_at");
        Instant endedAt = endTs != null ? endTs.toInstant() : null;

        long duration = rs.getLong("duration_seconds");

        long sessIdVal = rs.getLong("session_id");
        Long sessionId = rs.wasNull() ? null : sessIdVal;

        String catStr = rs.getString("activity_category");
        AppCategory category = AppCategory.fromString(catStr);

        return new ApplicationActivity(id, appName, title, startedAt, endedAt, duration, sessionId, category);
    }
}
