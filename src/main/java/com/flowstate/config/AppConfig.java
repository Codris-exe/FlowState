package com.flowstate.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Properties;

/**
 * Centralized application configuration.
 * Loads configuration from:
 * 1. application.properties in local working directory (if present)
 * 2. application.properties on the classpath
 * 3. Environment variables (FLOWSTATE_DB_URL, FLOWSTATE_DB_USER, FLOWSTATE_DB_PASSWORD)
 * 4. Safe built-in defaults
 */
public class AppConfig {
    private static final Logger logger = LoggerFactory.getLogger(AppConfig.class);
    private static final AppConfig INSTANCE = new AppConfig();

    private String dbUrl;
    private String dbUser;
    private String dbPassword;
    private int maxPoolSize;
    private int minIdle;
    private long connectionTimeoutMs;

    public AppConfig() {
        loadConfiguration();
    }

    public static AppConfig getInstance() {
        return INSTANCE;
    }

    public void reload() {
        loadConfiguration();
    }

    private void loadConfiguration() {
        Properties props = new Properties();

        // 1. Load from classpath
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("application.properties")) {
            if (is != null) {
                props.load(is);
                logger.info("Loaded configuration from classpath: application.properties");
            }
        } catch (Exception e) {
            logger.warn("Could not read classpath application.properties: {}", e.getMessage());
        }

        // 2. Load from local file in working directory (overrides classpath)
        File localFile = new File("application.properties");
        if (localFile.exists() && localFile.canRead()) {
            try (FileInputStream fis = new FileInputStream(localFile)) {
                props.load(fis);
                logger.info("Loaded configuration from local file: {}", localFile.getAbsolutePath());
            } catch (Exception e) {
                logger.warn("Could not read local application.properties: {}", e.getMessage());
            }
        }

        // 3. Fallback defaults
        this.dbUrl = props.getProperty("db.url", "jdbc:postgresql://localhost:5432/flowstate");
        this.dbUser = props.getProperty("db.user", "postgres");
        this.dbPassword = props.getProperty("db.password", "postgres");
        this.maxPoolSize = Integer.parseInt(props.getProperty("db.pool.maxSize", "10"));
        this.minIdle = Integer.parseInt(props.getProperty("db.pool.minIdle", "2"));
        this.connectionTimeoutMs = Long.parseLong(props.getProperty("db.pool.timeoutMs", "30000"));

        // 4. Environment variable overrides (highest precedence)
        String envUrl = System.getenv("FLOWSTATE_DB_URL");
        if (envUrl != null && !envUrl.isBlank()) {
            this.dbUrl = envUrl.trim();
        }

        String envUser = System.getenv("FLOWSTATE_DB_USER");
        if (envUser != null && !envUser.isBlank()) {
            this.dbUser = envUser.trim();
        }

        String envPass = System.getenv("FLOWSTATE_DB_PASSWORD");
        if (envPass != null) {
            this.dbPassword = envPass;
        }

        logger.info("Database configuration initialized for URL: {}", this.dbUrl);
    }

    public String getDbUrl() {
        return dbUrl;
    }

    public void setDbUrl(String dbUrl) {
        this.dbUrl = dbUrl;
    }

    public String getDbUser() {
        return dbUser;
    }

    public void setDbUser(String dbUser) {
        this.dbUser = dbUser;
    }

    public String getDbPassword() {
        return dbPassword;
    }

    public void setDbPassword(String dbPassword) {
        this.dbPassword = dbPassword;
    }

    public int getMaxPoolSize() {
        return maxPoolSize;
    }

    public void setMaxPoolSize(int maxPoolSize) {
        this.maxPoolSize = maxPoolSize;
    }

    public int getMinIdle() {
        return minIdle;
    }

    public void setMinIdle(int minIdle) {
        this.minIdle = minIdle;
    }

    public long getConnectionTimeoutMs() {
        return connectionTimeoutMs;
    }

    public void setConnectionTimeoutMs(long connectionTimeoutMs) {
        this.connectionTimeoutMs = connectionTimeoutMs;
    }
}
