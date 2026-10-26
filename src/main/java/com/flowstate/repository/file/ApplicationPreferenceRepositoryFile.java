package com.flowstate.repository.file;

import com.fasterxml.jackson.core.type.TypeReference;
import com.flowstate.model.AppCategory;
import com.flowstate.model.ApplicationPreference;
import com.flowstate.repository.ApplicationPreferenceRepository;
import com.flowstate.storage.FileStorageManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.stream.Collectors;

/**
 * File-based implementation of ApplicationPreferenceRepository using Java Input/Output streams.
 * Persists user application classifications to a local file.
 */
public class ApplicationPreferenceRepositoryFile implements ApplicationPreferenceRepository {
    private static final Logger logger = LoggerFactory.getLogger(ApplicationPreferenceRepositoryFile.class);

    private final FileStorageManager storageManager;
    private final Path filePath;
    private final List<ApplicationPreference> cache = new ArrayList<>();
    private long idSequence = 1L;

    public ApplicationPreferenceRepositoryFile() {
        this(FileStorageManager.getInstance());
    }

    public ApplicationPreferenceRepositoryFile(FileStorageManager storageManager) {
        this.storageManager = Objects.requireNonNull(storageManager, "storageManager must not be null");
        this.filePath = storageManager.getPreferencesFilePath();
        loadFromFile();
    }

    private void loadFromFile() {
        storageManager.writeLock().lock();
        try {
            if (Files.exists(filePath)) {
                try (InputStream fis = new FileInputStream(filePath.toFile());
                     BufferedInputStream bis = new BufferedInputStream(fis)) {
                    List<ApplicationPreference> loaded = storageManager.getObjectMapper().readValue(bis, new TypeReference<List<ApplicationPreference>>() {});
                    cache.clear();
                    cache.addAll(loaded);
                    for (ApplicationPreference p : cache) {
                        if (p.getId() != null && p.getId() >= idSequence) {
                            idSequence = p.getId() + 1;
                        }
                    }
                    logger.debug("Loaded {} preferences using FileInputStream: {}", cache.size(), filePath);
                }
            }
        } catch (Exception e) {
            logger.warn("Could not load preferences from file {}: {}", filePath, e.getMessage());
        } finally {
            storageManager.writeLock().unlock();
        }
    }

    private void saveToFile() {
        storageManager.writeLock().lock();
        try {
            Path tempPath = filePath.resolveSibling(filePath.getFileName().toString() + ".tmp");
            try (OutputStream fos = new FileOutputStream(tempPath.toFile());
                 BufferedOutputStream bos = new BufferedOutputStream(fos)) {
                storageManager.getObjectMapper().writeValue(bos, cache);
                bos.flush();
            }
            Files.move(tempPath, filePath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            logger.debug("Saved {} preferences using FileOutputStream: {}", cache.size(), filePath);
        } catch (IOException e) {
            logger.error("Failed to write preferences to file: {}", e.getMessage(), e);
        } finally {
            storageManager.writeLock().unlock();
        }
    }

    @Override
    public ApplicationPreference save(ApplicationPreference preference) {
        Objects.requireNonNull(preference, "preference must not be null");
        storageManager.writeLock().lock();
        try {
            if (preference.getId() == null) {
                preference.setId(idSequence++);
            }
            String id = preference.getApplicationIdentifier() != null ? preference.getApplicationIdentifier().toLowerCase() : "";
            cache.removeIf(p -> Objects.equals(p.getApplicationIdentifier(), id));
            cache.add(preference);
            saveToFile();
            return preference;
        } finally {
            storageManager.writeLock().unlock();
        }
    }

    @Override
    public boolean update(ApplicationPreference preference) {
        Objects.requireNonNull(preference, "preference must not be null");
        if (preference.getApplicationIdentifier() == null) {
            throw new IllegalArgumentException("Cannot update preference without applicationIdentifier");
        }
        storageManager.writeLock().lock();
        try {
            String id = preference.getApplicationIdentifier().toLowerCase();
            for (int i = 0; i < cache.size(); i++) {
                if (Objects.equals(cache.get(i).getApplicationIdentifier(), id)) {
                    cache.set(i, preference);
                    saveToFile();
                    return true;
                }
            }
            return false;
        } finally {
            storageManager.writeLock().unlock();
        }
    }

    @Override
    public Optional<ApplicationPreference> findByIdentifier(String identifier) {
        if (identifier == null || identifier.isBlank()) return Optional.empty();
        storageManager.readLock().lock();
        try {
            String clean = identifier.trim().toLowerCase();
            return cache.stream().filter(p -> Objects.equals(p.getApplicationIdentifier(), clean)).findFirst();
        } finally {
            storageManager.readLock().unlock();
        }
    }

    @Override
    public List<ApplicationPreference> findAll() {
        storageManager.readLock().lock();
        try {
            return cache.stream()
                    .sorted(Comparator.comparing(ApplicationPreference::getDisplayName, String.CASE_INSENSITIVE_ORDER))
                    .collect(Collectors.toList());
        } finally {
            storageManager.readLock().unlock();
        }
    }

    @Override
    public boolean deleteByIdentifier(String identifier) {
        if (identifier == null || identifier.isBlank()) return false;
        storageManager.writeLock().lock();
        try {
            String clean = identifier.trim().toLowerCase();
            boolean removed = cache.removeIf(p -> Objects.equals(p.getApplicationIdentifier(), clean));
            if (removed) {
                saveToFile();
            }
            return removed;
        } finally {
            storageManager.writeLock().unlock();
        }
    }

    @Override
    public void upsert(String identifier, String displayName, AppCategory category, boolean isExcluded) {
        if (identifier == null || identifier.isBlank()) return;
        storageManager.writeLock().lock();
        try {
            String cleanId = identifier.trim().toLowerCase();
            String cleanName = (displayName != null && !displayName.isBlank()) ? displayName.trim() : cleanId;
            AppCategory cleanCat = category != null ? category : AppCategory.NEUTRAL;

            Optional<ApplicationPreference> existing = cache.stream()
                    .filter(p -> Objects.equals(p.getApplicationIdentifier(), cleanId))
                    .findFirst();

            if (existing.isPresent()) {
                ApplicationPreference p = existing.get();
                p.setDisplayName(cleanName);
                p.setCategory(cleanCat);
                p.setExcluded(isExcluded);
            } else {
                ApplicationPreference p = new ApplicationPreference(idSequence++, cleanId, cleanName, cleanCat, isExcluded);
                cache.add(p);
            }
            saveToFile();
        } finally {
            storageManager.writeLock().unlock();
        }
    }

    @Override
    public List<String> findExcludedIdentifiers() {
        storageManager.readLock().lock();
        try {
            return cache.stream()
                    .filter(ApplicationPreference::isExcluded)
                    .map(ApplicationPreference::getApplicationIdentifier)
                    .collect(Collectors.toList());
        } finally {
            storageManager.readLock().unlock();
        }
    }
}
