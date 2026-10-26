package com.flowstate.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.stream.Collectors;

/**
 * Manages the PostgreSQL HikariCP connection pool, automated schema migrations,
 * and connection health diagnostics with guided recovery.
 */
public class DatabaseManager implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(DatabaseManager.class);
    private static final DatabaseManager INSTANCE = new DatabaseManager();

    private final AppConfig config;
    private HikariDataSource dataSource;
    private boolean initialized = false;
    private String lastErrorMessage;

    public DatabaseManager() {
        this(AppConfig.getInstance());
    }

    public DatabaseManager(AppConfig config) {
        this.config = config != null ? config : AppConfig.getInstance();
    }

    public static DatabaseManager getInstance() {
        return INSTANCE;
    }

    /**
     * Initializes the HikariCP connection pool and runs schema migrations.
     * Does NOT throw an unhandled exception if the database is offline; instead flags error state
     * so the UI can display diagnostics.
     */
    public synchronized boolean initialize() {
        if (initialized && dataSource != null && !dataSource.isClosed()) {
            return true;
        }

        try {
            logger.info("Initializing HikariCP pool for PostgreSQL at {}", config.getDbUrl());
            HikariConfig hikariConfig = new HikariConfig();
            hikariConfig.setDriverClassName("org.postgresql.Driver");
            hikariConfig.setJdbcUrl(config.getDbUrl());
            hikariConfig.setUsername(config.getDbUser());
            hikariConfig.setPassword(config.getDbPassword());
            hikariConfig.setMaximumPoolSize(config.getMaxPoolSize());
            hikariConfig.setMinimumIdle(config.getMinIdle());
            hikariConfig.setConnectionTimeout(config.getConnectionTimeoutMs());
            hikariConfig.setPoolName("FlowStateHikariPool");
            hikariConfig.setLeakDetectionThreshold(60000); // 60s leak warning

            // Performance optimizations
            hikariConfig.addDataSourceProperty("reWriteBatchedInserts", "true");
            hikariConfig.addDataSourceProperty("cachePrepStmts", "true");
            hikariConfig.addDataSourceProperty("prepStmtCacheSize", "250");
            hikariConfig.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");

            this.dataSource = new HikariDataSource(hikariConfig);
            this.initialized = true;
            this.lastErrorMessage = null;

            logger.info("HikariCP pool initialized successfully.");

            // Run database schema migration
            migrateSchema();
            return true;
        } catch (Exception e) {
            this.lastErrorMessage = e.getMessage();
            logger.error("Failed to initialize database connection pool: {}", e.getMessage());
            this.initialized = false;
            return false;
        }
    }

    /**
     * Executes the DDL script in db/schema.sql to ensure all tables, indexes,
     * and default rows are created.
     */
    public synchronized void migrateSchema() throws SQLException {
        if (!isHealthy()) {
            throw new SQLException("Cannot migrate schema: Database is not reachable.");
        }

        try (InputStream is = getClass().getClassLoader().getResourceAsStream("db/schema.sql")) {
            if (is == null) {
                throw new IllegalStateException("db/schema.sql not found on classpath");
            }

            String sql = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))
                    .lines()
                    .collect(Collectors.joining("\n"));

            try (Connection conn = getConnection();
                 Statement stmt = conn.createStatement()) {
                logger.info("Executing database schema migration...");
                stmt.execute(sql);
                logger.info("Database schema migration completed successfully.");
            }
        } catch (Exception e) {
            logger.error("Error executing schema migration: {}", e.getMessage(), e);
            if (e instanceof SQLException sqlEx) {
                throw sqlEx;
            }
            throw new SQLException("Schema migration failed: " + e.getMessage(), e);
        }
    }

    /**
     * Gets a connection from the pool.
     */
    public Connection getConnection() throws SQLException {
        if (dataSource == null || dataSource.isClosed()) {
            throw new SQLException("Connection pool is not open. Call initialize() first.");
        }
        return dataSource.getConnection();
    }

    /**
     * Validates if the connection pool can query the database.
     */
    public boolean isHealthy() {
        if (dataSource == null || dataSource.isClosed()) {
            return false;
        }
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            return stmt.execute("SELECT 1");
        } catch (Exception e) {
            this.lastErrorMessage = e.getMessage();
            return false;
        }
    }

    /**
     * Generates structured diagnostic information and recovery instructions
     * for display in application logs or in a recovery dialog.
     */
    public String getDiagnostics() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== FlowState Database Diagnostics ===\n");
        sb.append("Target JDBC URL: ").append(config.getDbUrl()).append("\n");
        sb.append("Target Username: ").append(config.getDbUser()).append("\n");
        sb.append("Pool Status: ").append(dataSource != null && !dataSource.isClosed() ? "ACTIVE" : "INACTIVE").append("\n");
        if (lastErrorMessage != null) {
            sb.append("Last Error: ").append(lastErrorMessage).append("\n");
        }
        sb.append("\nRecommended Recovery Steps:\n");
        sb.append("1. Verify that the PostgreSQL service is running:\n");
        sb.append("   - On Windows: Press Win+R, type 'services.msc', and ensure 'postgresql-x64-...' is Running.\n");
        sb.append("2. Verify that the target database 'flowstate' exists:\n");
        sb.append("   - Run: psql -U postgres -c \"CREATE DATABASE flowstate;\"\n");
        sb.append("3. Verify credentials in application.properties or set environment variables:\n");
        sb.append("   - FLOWSTATE_DB_URL=jdbc:postgresql://localhost:5432/flowstate\n");
        sb.append("   - FLOWSTATE_DB_USER=postgres\n");
        sb.append("   - FLOWSTATE_DB_PASSWORD=your_password\n");
        sb.append("4. Click 'Retry Connection' in the FlowState application.\n");
        return sb.toString();
    }

    public String getLastErrorMessage() {
        return lastErrorMessage;
    }

    @Override
    public synchronized void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            logger.info("Closing HikariCP connection pool.");
            dataSource.close();
        }
        this.initialized = false;
    }
}
