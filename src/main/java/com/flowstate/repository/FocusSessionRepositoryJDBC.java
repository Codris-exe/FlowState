package com.flowstate.repository;

import com.flowstate.config.DatabaseManager;
import com.flowstate.model.FocusSession;
import com.flowstate.model.SessionCategory;
import com.flowstate.model.SessionStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Robust JDBC implementation of FocusSessionRepository using prepared statements.
 */
public class FocusSessionRepositoryJDBC implements FocusSessionRepository {
    private static final Logger logger = LoggerFactory.getLogger(FocusSessionRepositoryJDBC.class);
    private final DatabaseManager databaseManager;

    public FocusSessionRepositoryJDBC() {
        this(DatabaseManager.getInstance());
    }

    public FocusSessionRepositoryJDBC(DatabaseManager databaseManager) {
        this.databaseManager = Objects.requireNonNull(databaseManager, "databaseManager must not be null");
    }

    @Override
    public FocusSession save(FocusSession session) throws SQLException {
        Objects.requireNonNull(session, "session must not be null");

        String sql = """
            INSERT INTO focus_sessions
            (title, category, start_time, end_time, target_duration_seconds, actual_duration_seconds, status, notes, interruption_count, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            stmt.setString(1, session.getTitle() != null ? session.getTitle() : "Focus Session");
            stmt.setString(2, session.getCategory() != null ? session.getCategory().name() : SessionCategory.OTHER.name());
            stmt.setTimestamp(3, session.getStartTime() != null ? Timestamp.from(session.getStartTime()) : Timestamp.from(Instant.now()));

            if (session.getEndTime() != null) {
                stmt.setTimestamp(4, Timestamp.from(session.getEndTime()));
            } else {
                stmt.setNull(4, Types.TIMESTAMP_WITH_TIMEZONE);
            }

            stmt.setLong(5, session.getTargetDurationSeconds());
            stmt.setLong(6, session.getActualDurationSeconds());
            stmt.setString(7, session.getStatus() != null ? session.getStatus().name() : SessionStatus.PENDING.name());
            stmt.setString(8, session.getNotes());
            stmt.setInt(9, session.getInterruptionCount());
            stmt.setTimestamp(10, session.getCreatedAt() != null ? Timestamp.from(session.getCreatedAt()) : Timestamp.from(Instant.now()));

            int affectedRows = stmt.executeUpdate();
            if (affectedRows == 0) {
                throw new SQLException("Creating focus session failed, no rows affected.");
            }

            try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    session.setId(generatedKeys.getLong(1));
                }
            }

            logger.debug("Saved focus session to database with id: {}", session.getId());
            return session;
        }
    }

    @Override
    public boolean update(FocusSession session) throws SQLException {
        Objects.requireNonNull(session, "session must not be null");
        if (session.getId() == null) {
            throw new IllegalArgumentException("Cannot update session without an ID");
        }

        String sql = """
            UPDATE focus_sessions
            SET title = ?, category = ?, start_time = ?, end_time = ?,
                target_duration_seconds = ?, actual_duration_seconds = ?,
                status = ?, notes = ?, interruption_count = ?
            WHERE id = ?
            """;

        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, session.getTitle());
            stmt.setString(2, session.getCategory() != null ? session.getCategory().name() : SessionCategory.OTHER.name());
            stmt.setTimestamp(3, session.getStartTime() != null ? Timestamp.from(session.getStartTime()) : null);

            if (session.getEndTime() != null) {
                stmt.setTimestamp(4, Timestamp.from(session.getEndTime()));
            } else {
                stmt.setNull(4, Types.TIMESTAMP_WITH_TIMEZONE);
            }

            stmt.setLong(5, session.getTargetDurationSeconds());
            stmt.setLong(6, session.getActualDurationSeconds());
            stmt.setString(7, session.getStatus() != null ? session.getStatus().name() : SessionStatus.COMPLETED.name());
            stmt.setString(8, session.getNotes());
            stmt.setInt(9, session.getInterruptionCount());
            stmt.setLong(10, session.getId());

            int updated = stmt.executeUpdate();
            logger.debug("Updated focus session id {}: {} rows affected", session.getId(), updated);
            return updated > 0;
        }
    }

    @Override
    public Optional<FocusSession> findById(Long id) throws SQLException {
        if (id == null) return Optional.empty();

        String sql = "SELECT * FROM focus_sessions WHERE id = ?";
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
    public List<FocusSession> findAll() throws SQLException {
        String sql = "SELECT * FROM focus_sessions ORDER BY start_time DESC";
        List<FocusSession> list = new ArrayList<>();

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
    public List<FocusSession> findByDateRange(Instant start, Instant end) throws SQLException {
        String sql = """
            SELECT * FROM focus_sessions
            WHERE start_time >= ? AND start_time <= ?
            ORDER BY start_time DESC
            """;
        List<FocusSession> list = new ArrayList<>();

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
    public List<FocusSession> findRecent(int limit) throws SQLException {
        int safeLimit = limit > 0 ? limit : 10;
        String sql = "SELECT * FROM focus_sessions ORDER BY start_time DESC LIMIT ?";
        List<FocusSession> list = new ArrayList<>();

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
    public boolean deleteById(Long id) throws SQLException {
        if (id == null) return false;

        String sql = "DELETE FROM focus_sessions WHERE id = ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, id);
            return stmt.executeUpdate() > 0;
        }
    }

    @Override
    public long countCompletedSessions() throws SQLException {
        String sql = "SELECT COUNT(*) FROM focus_sessions WHERE status = 'COMPLETED'";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) {
                return rs.getLong(1);
            }
        }
        return 0;
    }

    @Override
    public long sumTotalFocusSeconds() throws SQLException {
        String sql = "SELECT COALESCE(SUM(actual_duration_seconds), 0) FROM focus_sessions WHERE status = 'COMPLETED'";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) {
                return rs.getLong(1);
            }
        }
        return 0;
    }

    @Override
    public long sumTodayFocusSeconds(Instant startOfToday) throws SQLException {
        String sql = """
            SELECT COALESCE(SUM(actual_duration_seconds), 0)
            FROM focus_sessions
            WHERE status = 'COMPLETED' AND start_time >= ?
            """;
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setTimestamp(1, Timestamp.from(startOfToday != null ? startOfToday : Instant.EPOCH));
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        }
        return 0;
    }

    public FocusSession mapRow(ResultSet rs) throws SQLException {
        Long id = rs.getLong("id");
        String title = rs.getString("title");
        String categoryStr = rs.getString("category");
        SessionCategory category = SessionCategory.fromString(categoryStr);

        Timestamp startTs = rs.getTimestamp("start_time");
        Instant startTime = startTs != null ? startTs.toInstant() : null;

        Timestamp endTs = rs.getTimestamp("end_time");
        Instant endTime = endTs != null ? endTs.toInstant() : null;

        long targetDuration = rs.getLong("target_duration_seconds");
        long actualDuration = rs.getLong("actual_duration_seconds");

        String statusStr = rs.getString("status");
        SessionStatus status = SessionStatus.fromString(statusStr);

        String notes = rs.getString("notes");
        int interruptions = rs.getInt("interruption_count");

        Timestamp createdTs = rs.getTimestamp("created_at");
        Instant createdAt = createdTs != null ? createdTs.toInstant() : Instant.now();

        return new FocusSession(id, title, category, startTime, endTime,
                targetDuration, actualDuration, status, notes, interruptions, createdAt);
    }
}
