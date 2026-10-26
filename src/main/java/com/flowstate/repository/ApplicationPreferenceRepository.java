package com.flowstate.repository;

import com.flowstate.model.AppCategory;
import com.flowstate.model.ApplicationPreference;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/**
 * Data Access Object (DAO) interface for persisting and managing user application preferences.
 */
public interface ApplicationPreferenceRepository {

    /**
     * Saves or inserts a new application preference.
     */
    ApplicationPreference save(ApplicationPreference preference) throws SQLException;

    /**
     * Updates an existing preference record.
     */
    boolean update(ApplicationPreference preference) throws SQLException;

    /**
     * Finds a preference by normalized application identifier (e.g. "chrome.exe").
     */
    Optional<ApplicationPreference> findByIdentifier(String identifier) throws SQLException;

    /**
     * Retrieves all saved application preferences.
     */
    List<ApplicationPreference> findAll() throws SQLException;

    /**
     * Deletes preference for an application identifier.
     */
    boolean deleteByIdentifier(String identifier) throws SQLException;

    /**
     * Inserts or updates an application preference (UPSERT) atomically.
     */
    void upsert(String identifier, String displayName, AppCategory category, boolean isExcluded) throws SQLException;

    /**
     * Retrieves all application identifiers currently flagged as excluded.
     */
    List<String> findExcludedIdentifiers() throws SQLException;
}
