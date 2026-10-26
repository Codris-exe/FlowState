package com.focusforge;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

public class ActivityStorage {
    private static final ActivityStorage INSTANCE = new ActivityStorage();

    private final Path filePath;
    private final ObjectMapper mapper;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final AtomicLong idSequence = new AtomicLong(1);

    public ActivityStorage() {
        this(Paths.get("data", "activities.json"));
    }

    public ActivityStorage(Path filePath) {
        this.filePath = filePath;
        this.mapper = new ObjectMapper();
        this.mapper.registerModule(new JavaTimeModule());
        this.mapper.enable(SerializationFeature.INDENT_OUTPUT);
        this.mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        this.mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

        initStorage();
    }

    public static ActivityStorage getInstance() {
        return INSTANCE;
    }

    private void initStorage() {
        try {
            Path parent = filePath.getParent();
            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent);
            }
            if (Files.exists(filePath)) {
                List<Activity> existing = loadActivities();
                long maxId = existing.stream().mapToLong(a -> a.getId() != null ? a.getId() : 0).max().orElse(0);
                idSequence.set(maxId + 1);
            }
        } catch (Exception e) {
            System.err.println("[ActivityStorage] Initialization warning: " + e.getMessage());
        }
    }

    public List<Activity> loadActivities() {
        lock.readLock().lock();
        try {
            File file = filePath.toFile();
            if (!file.exists() || file.length() == 0) {
                return new ArrayList<>();
            }
            try (InputStream fis = new FileInputStream(file);
                 InputStream bis = new BufferedInputStream(fis)) {
                return mapper.readValue(bis, new TypeReference<>() {});
            }
        } catch (Exception e) {
            System.err.println("[ActivityStorage] Failed to read activities: " + e.getMessage());
            return new ArrayList<>();
        } finally {
            lock.readLock().unlock();
        }
    }

    private void saveAll(List<Activity> activities) {
        lock.writeLock().lock();
        try {
            File file = filePath.toFile();
            try (OutputStream fos = new FileOutputStream(file);
                 OutputStream bos = new BufferedOutputStream(fos)) {
                mapper.writeValue(bos, activities);
            }
        } catch (Exception e) {
            System.err.println("[ActivityStorage] Failed to write activities: " + e.getMessage());
        } finally {
            lock.writeLock().unlock();
        }
    }

    public Activity save(Activity activity) {
        if (activity == null) return null;
        lock.writeLock().lock();
        try {
            List<Activity> all = loadActivities();
            if (activity.getId() == null) {
                activity.setId(idSequence.getAndIncrement());
            }
            all.add(activity);
            saveAll(all);
            return activity;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public List<Activity> findByDateRange(Instant start, Instant end) {
        return loadActivities().stream()
                .filter(a -> a.getStartedAt() != null)
                .filter(a -> !a.getStartedAt().isBefore(start) && a.getStartedAt().isBefore(end))
                .collect(Collectors.toList());
    }

    public List<Activity> findRecent(int limit) {
        List<Activity> all = loadActivities();
        int from = Math.max(0, all.size() - limit);
        List<Activity> sub = new ArrayList<>(all.subList(from, all.size()));
        Collections.reverse(sub);
        return sub;
    }

    public Map<String, Long> getTopApplicationsDuration(Instant start, Instant end, int limit) {
        return findByDateRange(start, end).stream()
                .collect(Collectors.groupingBy(Activity::getApplicationName, Collectors.summingLong(Activity::getDurationSeconds)))
                .entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(limit)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e1, LinkedHashMap::new));
    }
}
