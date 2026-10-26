package com.flowstate.repository;

import com.flowstate.model.FocusSession;

import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Data Access Object (DAO) interface for persisting and querying FocusSession records.
 */
public interface FocusSessionRepository {

    /**
     * Inserts a new focus session and populates its generated database ID.
     */
    FocusSession save(FocusSession session) throws SQLException;

    /**
     * Updates an existing focus session record.
     */
    boolean update(FocusSession session) throws SQLException;

    /**
     * Finds a session by its database primary key.
     */
    Optional<FocusSession> findById(Long id) throws SQLException;

    /**
     * Finds all recorded focus sessions ordered by start time descending.
     */
    List<FocusSession> findAll() throws SQLException;

    /**
     * Finds sessions within a specified start time window.
     */
    List<FocusSession> findByDateRange(Instant start, Instant end) throws SQLException;

    /**
     * Retrieves the most recent N sessions.
     */
    List<FocusSession> findRecent(int limit) throws SQLException;

    /**
     * Deletes a focus session by primary key.
     */
    boolean deleteById(Long id) throws SQLException;

    /**
     * Counts the total number of completed focus sessions.
     */
    long countCompletedSessions() throws SQLException;

    /**
     * Calculates the sum of actual duration seconds for completed sessions.
     */
    long sumTotalFocusSeconds() throws SQLException;

    /**
     * Calculates today's total focus seconds from midnight UTC.
     */
    long sumTodayFocusSeconds(Instant startOfToday) throws SQLException;
}
