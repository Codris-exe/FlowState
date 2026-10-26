package com.flowstate.repository.file;

import com.fasterxml.jackson.core.type.TypeReference;
import com.flowstate.model.ApplicationActivity;
import com.flowstate.repository.ActivityRepository;
import com.flowstate.storage.FileStorageManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * File-based implementation of ActivityRepository using Java Input/Output streams.
 * Persists application activity intervals into a local file.
 */
public class ActivityRepositoryFile implements ActivityRepository {
    private static final Logger logger = LoggerFactory.getLogger(ActivityRepositoryFile.class);

    private final FileStorageManager storageManager;
    private final Path filePath;
    private final List<ApplicationActivity> cache = new ArrayList<>();
    private long idSequence = 1L;

    public ActivityRepositoryFile() {
        this(FileStorageManager.getInstance());
    }

    public ActivityRepositoryFile(FileStorageManager storageManager) {
        this.storageManager = Objects.requireNonNull(storageManager, "storageManager must not be null");
        this.filePath = storageManager.getActivitiesFilePath();
        loadFromFile();
    }

    private void loadFromFile() {
        storageManager.writeLock().lock();
        try {
            if (Files.exists(filePath)) {
                try (InputStream fis = new FileInputStream(filePath.toFile());
                     BufferedInputStream bis = new BufferedInputStream(fis)) {
                    List<ApplicationActivity> loaded = storageManager.getObjectMapper().readValue(bis, new TypeReference<List<ApplicationActivity>>() {});
                    cache.clear();
                    cache.addAll(loaded);
                    for (ApplicationActivity a : cache) {
                        if (a.getId() != null && a.getId() >= idSequence) {
                            idSequence = a.getId() + 1;
                        }
                    }
                    logger.debug("Loaded {} activity records using FileInputStream: {}", cache.size(), filePath);
                }
            }
        } catch (Exception e) {
            logger.warn("Could not load activities from file {}: {}", filePath, e.getMessage());
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
            logger.debug("Saved {} activities using FileOutputStream: {}", cache.size(), filePath);
        } catch (IOException e) {
            logger.error("Failed to write activities to file: {}", e.getMessage(), e);
        } finally {
            storageManager.writeLock().unlock();
        }
    }

    @Override
    public ApplicationActivity save(ApplicationActivity activity) {
        Objects.requireNonNull(activity, "activity must not be null");
        storageManager.writeLock().lock();
        try {
            if (activity.getId() == null) {
                activity.setId(idSequence++);
            }
            cache.removeIf(a -> Objects.equals(a.getId(), activity.getId()));
            cache.add(activity);
            saveToFile();
            return activity;
        } finally {
            storageManager.writeLock().unlock();
        }
    }

    @Override
    public boolean update(ApplicationActivity activity) {
        Objects.requireNonNull(activity, "activity must not be null");
        if (activity.getId() == null) {
            throw new IllegalArgumentException("Cannot update activity without an ID");
        }
        storageManager.writeLock().lock();
        try {
            for (int i = 0; i < cache.size(); i++) {
                if (Objects.equals(cache.get(i).getId(), activity.getId())) {
                    cache.set(i, activity);
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
    public Optional<ApplicationActivity> findById(Long id) {
        if (id == null) return Optional.empty();
        storageManager.readLock().lock();
        try {
            return cache.stream().filter(a -> Objects.equals(a.getId(), id)).findFirst();
        } finally {
            storageManager.readLock().unlock();
        }
    }

    @Override
    public List<ApplicationActivity> findBySessionId(Long sessionId) {
        if (sessionId == null) return Collections.emptyList();
        storageManager.readLock().lock();
        try {
            return cache.stream()
                    .filter(a -> Objects.equals(a.getSessionId(), sessionId))
                    .sorted(Comparator.comparing(ApplicationActivity::getStartedAt))
                    .collect(Collectors.toList());
        } finally {
            storageManager.readLock().unlock();
        }
    }

    @Override
    public List<ApplicationActivity> findByDateRange(Instant start, Instant end) {
        storageManager.readLock().lock();
        try {
            Instant min = start != null ? start : Instant.EPOCH;
            Instant max = end != null ? end : Instant.now();
            return cache.stream()
                    .filter(a -> a.getStartedAt() != null && !a.getStartedAt().isBefore(min) && !a.getStartedAt().isAfter(max))
                    .sorted((a, b) -> Objects.compare(b.getStartedAt(), a.getStartedAt(), Comparator.nullsLast(Comparator.naturalOrder())))
                    .collect(Collectors.toList());
        } finally {
            storageManager.readLock().unlock();
        }
    }

    @Override
    public List<ApplicationActivity> findRecent(int limit) {
        int safeLimit = limit > 0 ? limit : 20;
        storageManager.readLock().lock();
        try {
            return cache.stream()
                    .sorted((a, b) -> Objects.compare(b.getStartedAt(), a.getStartedAt(), Comparator.nullsLast(Comparator.naturalOrder())))
                    .limit(safeLimit)
                    .collect(Collectors.toList());
        } finally {
            storageManager.readLock().unlock();
        }
    }

    @Override
    public long deleteBySessionId(Long sessionId) {
        if (sessionId == null) return 0;
        storageManager.writeLock().lock();
        try {
            int prevSize = cache.size();
            cache.removeIf(a -> Objects.equals(a.getSessionId(), sessionId));
            int removed = prevSize - cache.size();
            if (removed > 0) {
                saveToFile();
            }
            return removed;
        } finally {
            storageManager.writeLock().unlock();
        }
    }

    @Override
    public long clearAll() {
        storageManager.writeLock().lock();
        try {
            int size = cache.size();
            cache.clear();
            saveToFile();
            return size;
        } finally {
            storageManager.writeLock().unlock();
        }
    }

    @Override
    public Map<String, Long> getTopApplicationsDuration(Instant start, Instant end, int limit) {
        int safeLimit = limit > 0 ? limit : 5;
        storageManager.readLock().lock();
        try {
            Instant min = start != null ? start : Instant.EPOCH;
            Instant max = end != null ? end : Instant.now();

            return cache.stream()
                    .filter(a -> a.getStartedAt() != null && !a.getStartedAt().isBefore(min) && !a.getStartedAt().isAfter(max))
                    .collect(Collectors.groupingBy(
                            ApplicationActivity::getApplicationName,
                            Collectors.summingLong(ApplicationActivity::getDurationSeconds)
                    ))
                    .entrySet().stream()
                    .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                    .limit(safeLimit)
                    .collect(Collectors.toMap(
                            Map.Entry::getKey,
                            Map.Entry::getValue,
                            (e1, e2) -> e1,
                            LinkedHashMap::new
                    ));
        } finally {
            storageManager.readLock().unlock();
        }
    }
}
