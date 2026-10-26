package com.flowstate.repository;

import com.flowstate.model.ApplicationActivity;

import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Data Access Object (DAO) interface for persisting and querying ApplicationActivity intervals.
 */
public interface ActivityRepository {

    /**
     * Inserts a new application activity record and sets its generated ID.
     */
    ApplicationActivity save(ApplicationActivity activity) throws SQLException;

    /**
     * Updates an existing activity interval (end time, duration, category).
     */
    boolean update(ApplicationActivity activity) throws SQLException;

    /**
     * Finds an activity record by primary key.
     */
    Optional<ApplicationActivity> findById(Long id) throws SQLException;

    /**
     * Finds all activities associated with a given focus session ID.
     */
    List<ApplicationActivity> findBySessionId(Long sessionId) throws SQLException;

    /**
     * Finds all activities logged within a timestamp interval.
     */
    List<ApplicationActivity> findByDateRange(Instant start, Instant end) throws SQLException;

    /**
     * Retrieves the most recent N activity records.
     */
    List<ApplicationActivity> findRecent(int limit) throws SQLException;

    /**
     * Deletes all activities associated with a specific focus session.
     */
    long deleteBySessionId(Long sessionId) throws SQLException;

    /**
     * Clears all activity records from the database.
     */
    long clearAll() throws SQLException;

    /**
     * Retrieves aggregated total duration in seconds grouped by application name.
     */
    Map<String, Long> getTopApplicationsDuration(Instant start, Instant end, int limit) throws SQLException;
}
