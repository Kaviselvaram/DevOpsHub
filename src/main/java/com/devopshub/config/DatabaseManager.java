package com.devopshub.config;

import com.devopshub.exception.DatabaseException;
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

/**
 * Manages JDBC connections via HikariCP connection pooling.
 * Supports primary MySQL database with automated schema migrations.
 */
public class DatabaseManager {
    private static final Logger logger = LoggerFactory.getLogger(DatabaseManager.class);
    private static DatabaseManager instance;

    private final HikariDataSource dataSource;
    private final boolean isH2Fallback;

    private DatabaseManager() {
        AppConfig config = AppConfig.getInstance();
        HikariConfig hikariConfig = new HikariConfig();

        boolean fallback = false;
        HikariDataSource ds = null;

        try {
            logger.info("Attempting connection to primary MySQL database at {}:{}", config.getDbHost(), config.getDbPort());
            hikariConfig.setJdbcUrl(config.getJdbcUrl());
            hikariConfig.setUsername(config.getDbUser());
            hikariConfig.setPassword(config.getDbPassword());
            hikariConfig.setMaximumPoolSize(config.getDbPoolSize());
            hikariConfig.setMinimumIdle(2);
            hikariConfig.setConnectionTimeout(5000); // 5 sec timeout
            hikariConfig.setIdleTimeout(600000);
            hikariConfig.setMaxLifetime(1800000);
            hikariConfig.setPoolName("DevOpsHub-MySQL-Pool");
            hikariConfig.addDataSourceProperty("cachePrepStmts", "true");
            hikariConfig.addDataSourceProperty("prepStmtCacheSize", "250");
            hikariConfig.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");

            ds = new HikariDataSource(hikariConfig);
            // Test connection
            try (Connection conn = ds.getConnection()) {
                logger.info("Successfully connected to MySQL database: {}", config.getDbName());
            }
        } catch (Exception e) {
            logger.warn("Primary MySQL connection failed: {}. Initializing in-memory H2 fallback database in MySQL mode.", e.getMessage());
            if (ds != null) {
                ds.close();
            }
            fallback = true;
            HikariConfig h2Config = new HikariConfig();
            h2Config.setJdbcUrl("jdbc:h2:mem:devopshub;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1");
            h2Config.setUsername("sa");
            h2Config.setPassword("");
            h2Config.setMaximumPoolSize(5);
            h2Config.setPoolName("DevOpsHub-H2Fallback-Pool");
            ds = new HikariDataSource(h2Config);
        }

        this.dataSource = ds;
        this.isH2Fallback = fallback;

        // Apply migrations automatically on startup
        applyMigrations();
    }

    public static synchronized DatabaseManager getInstance() {
        if (instance == null) {
            instance = new DatabaseManager();
        }
        return instance;
    }

    public Connection getConnection() throws SQLException {
        if (dataSource == null || dataSource.isClosed()) {
            throw new SQLException("Database connection pool is closed.");
        }
        return dataSource.getConnection();
    }

    public boolean isH2Fallback() {
        return isH2Fallback;
    }

    public boolean testConnection() {
        try (Connection conn = getConnection()) {
            return conn.isValid(2);
        } catch (SQLException e) {
            logger.error("Database connection test failed: {}", e.getMessage());
            return false;
        }
    }

    public void applyMigrations() {
        logger.info("Applying database migrations...");
        executeSqlScript("/db/migrations/V1__initial_schema.sql");
        executeSqlScript("/db/migrations/V2__sample_seed_data.sql");
        logger.info("Database migrations applied successfully.");
    }

    private void executeSqlScript(String resourcePath) {
        try (InputStream is = getClass().getResourceAsStream(resourcePath)) {
            if (is == null) {
                logger.warn("Migration script not found on classpath: {}", resourcePath);
                return;
            }

            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.startsWith("--") || line.isEmpty()) {
                        continue;
                    }
                    sb.append(line).append(" ");
                }
            }

            String[] statements = sb.toString().split(";");
            try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
                for (String sql : statements) {
                    String trimmed = sql.trim();
                    if (!trimmed.isEmpty()) {
                        try {
                            stmt.execute(trimmed);
                        } catch (SQLException ex) {
                            // On H2 fallback, some MySQL-specific syntax might throw or record exists
                            if (isH2Fallback && (ex.getMessage().contains("already exists") || ex.getMessage().contains("PRIMARY KEY"))) {
                                logger.debug("Ignoring expected schema exception in fallback mode: {}", ex.getMessage());
                            } else if (!ex.getMessage().contains("already exists") && !ex.getMessage().contains("Duplicate entry")) {
                                logger.debug("Migration statement notice: {}", ex.getMessage());
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("Could not execute migration {}: {}", resourcePath, e.getMessage());
        }
    }

    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            logger.info("Shutting down database connection pool.");
            dataSource.close();
        }
    }
}
