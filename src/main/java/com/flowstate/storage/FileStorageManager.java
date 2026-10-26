package com.flowstate.storage;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Thread-safe file storage manager using Java NIO and Jackson for structured JSON persistence.
 * Replaces external SQL database requirements with transparent, human-readable local files.
 */
public class FileStorageManager {
    private static final Logger logger = LoggerFactory.getLogger(FileStorageManager.class);
    private static final FileStorageManager INSTANCE = new FileStorageManager();

    private final Path dataDirectory;
    private final ObjectMapper objectMapper;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    public FileStorageManager() {
        this(Paths.get("data"));
    }

    public FileStorageManager(Path dataDirectory) {
        this.dataDirectory = dataDirectory;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        this.objectMapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

        initialize();
    }

    public static FileStorageManager getInstance() {
        return INSTANCE;
    }

    public void initialize() {
        try {
            if (!Files.exists(dataDirectory)) {
                Files.createDirectories(dataDirectory);
                logger.info("Created local file storage directory at: {}", dataDirectory.toAbsolutePath());
            }
        } catch (IOException e) {
            logger.error("Failed to initialize file storage directory: {}", e.getMessage(), e);
        }
    }

    public Path getDataDirectory() {
        return dataDirectory;
    }

    public Path getSessionsFilePath() {
        return dataDirectory.resolve("sessions.json");
    }

    public Path getActivitiesFilePath() {
        return dataDirectory.resolve("activities.json");
    }

    public Path getPreferencesFilePath() {
        return dataDirectory.resolve("preferences.json");
    }

    public ObjectMapper getObjectMapper() {
        return objectMapper;
    }

    public ReentrantReadWriteLock.ReadLock readLock() {
        return lock.readLock();
    }

    public ReentrantReadWriteLock.WriteLock writeLock() {
        return lock.writeLock();
    }
}
