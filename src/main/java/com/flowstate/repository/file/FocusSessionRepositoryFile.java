package com.flowstate.repository.file;

import com.fasterxml.jackson.core.type.TypeReference;
import com.flowstate.model.FocusSession;
import com.flowstate.model.SessionStatus;
import com.flowstate.repository.FocusSessionRepository;
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
 * File-based implementation of FocusSessionRepository using Java Input/Output streams.
 * Persists focus sessions to local files without requiring PostgreSQL.
 */
public class FocusSessionRepositoryFile implements FocusSessionRepository {
    private static final Logger logger = LoggerFactory.getLogger(FocusSessionRepositoryFile.class);

    private final FileStorageManager storageManager;
    private final Path filePath;
    private final List<FocusSession> cache = new ArrayList<>();
    private long idSequence = 1L;

    public FocusSessionRepositoryFile() {
        this(FileStorageManager.getInstance());
    }

    public FocusSessionRepositoryFile(FileStorageManager storageManager) {
        this.storageManager = Objects.requireNonNull(storageManager, "storageManager must not be null");
        this.filePath = storageManager.getSessionsFilePath();
        loadFromFile();
    }

    private void loadFromFile() {
        storageManager.writeLock().lock();
        try {
            if (Files.exists(filePath)) {
                try (InputStream fis = new FileInputStream(filePath.toFile());
                     BufferedInputStream bis = new BufferedInputStream(fis)) {
                    List<FocusSession> loaded = storageManager.getObjectMapper().readValue(bis, new TypeReference<List<FocusSession>>() {});
                    cache.clear();
                    cache.addAll(loaded);
                    for (FocusSession s : cache) {
                        if (s.getId() != null && s.getId() >= idSequence) {
                            idSequence = s.getId() + 1;
                        }
                    }
                    logger.debug("Loaded {} focus sessions using FileInputStream: {}", cache.size(), filePath);
                }
            }
        } catch (Exception e) {
            logger.warn("Could not load sessions from file {}: {}", filePath, e.getMessage());
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
            logger.debug("Saved {} focus sessions using FileOutputStream: {}", cache.size(), filePath);
        } catch (IOException e) {
            logger.error("Failed to write sessions to file: {}", e.getMessage(), e);
        } finally {
            storageManager.writeLock().unlock();
        }
    }

    @Override
    public FocusSession save(FocusSession session) {
        Objects.requireNonNull(session, "session must not be null");
        storageManager.writeLock().lock();
        try {
            if (session.getId() == null) {
                session.setId(idSequence++);
            }
            cache.removeIf(s -> Objects.equals(s.getId(), session.getId()));
            cache.add(session);
            saveToFile();
            return session;
        } finally {
            storageManager.writeLock().unlock();
        }
    }

    @Override
    public boolean update(FocusSession session) {
        Objects.requireNonNull(session, "session must not be null");
        if (session.getId() == null) {
            throw new IllegalArgumentException("Cannot update session without an ID");
        }
        storageManager.writeLock().lock();
        try {
            for (int i = 0; i < cache.size(); i++) {
                if (Objects.equals(cache.get(i).getId(), session.getId())) {
                    cache.set(i, session);
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
    public Optional<FocusSession> findById(Long id) {
        if (id == null) return Optional.empty();
        storageManager.readLock().lock();
        try {
            return cache.stream().filter(s -> Objects.equals(s.getId(), id)).findFirst();
        } finally {
            storageManager.readLock().unlock();
        }
    }

    @Override
    public List<FocusSession> findAll() {
        storageManager.readLock().lock();
        try {
            return cache.stream()
                    .sorted((a, b) -> Objects.compare(b.getStartTime(), a.getStartTime(), Comparator.nullsLast(Comparator.naturalOrder())))
                    .collect(Collectors.toList());
        } finally {
            storageManager.readLock().unlock();
        }
    }

    @Override
    public List<FocusSession> findByDateRange(Instant start, Instant end) {
        storageManager.readLock().lock();
        try {
            Instant min = start != null ? start : Instant.EPOCH;
            Instant max = end != null ? end : Instant.now();
            return cache.stream()
                    .filter(s -> s.getStartTime() != null && !s.getStartTime().isBefore(min) && !s.getStartTime().isAfter(max))
                    .sorted((a, b) -> Objects.compare(b.getStartTime(), a.getStartTime(), Comparator.nullsLast(Comparator.naturalOrder())))
                    .collect(Collectors.toList());
        } finally {
            storageManager.readLock().unlock();
        }
    }

    @Override
    public List<FocusSession> findRecent(int limit) {
        int safeLimit = limit > 0 ? limit : 10;
        storageManager.readLock().lock();
        try {
            return cache.stream()
                    .sorted((a, b) -> Objects.compare(b.getStartTime(), a.getStartTime(), Comparator.nullsLast(Comparator.naturalOrder())))
                    .limit(safeLimit)
                    .collect(Collectors.toList());
        } finally {
            storageManager.readLock().unlock();
        }
    }

    @Override
    public boolean deleteById(Long id) {
        if (id == null) return false;
        storageManager.writeLock().lock();
        try {
            boolean removed = cache.removeIf(s -> Objects.equals(s.getId(), id));
            if (removed) {
                saveToFile();
            }
            return removed;
        } finally {
            storageManager.writeLock().unlock();
        }
    }

    @Override
    public long countCompletedSessions() {
        storageManager.readLock().lock();
        try {
            return cache.stream().filter(s -> s.getStatus() == SessionStatus.COMPLETED).count();
        } finally {
            storageManager.readLock().unlock();
        }
    }

    @Override
    public long sumTotalFocusSeconds() {
        storageManager.readLock().lock();
        try {
            return cache.stream()
                    .filter(s -> s.getStatus() == SessionStatus.COMPLETED)
                    .mapToLong(FocusSession::getActualDurationSeconds)
                    .sum();
        } finally {
            storageManager.readLock().unlock();
        }
    }

    @Override
    public long sumTodayFocusSeconds(Instant startOfToday) {
        storageManager.readLock().lock();
        try {
            Instant min = startOfToday != null ? startOfToday : Instant.EPOCH;
            return cache.stream()
                    .filter(s -> s.getStatus() == SessionStatus.COMPLETED && s.getStartTime() != null && !s.getStartTime().isBefore(min))
                    .mapToLong(FocusSession::getActualDurationSeconds)
                    .sum();
        } finally {
            storageManager.readLock().unlock();
        }
    }
}
