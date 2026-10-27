package com.devopshub.config;

import com.devopshub.exception.DatabaseException;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
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

        boolean forceH2 = "true".equalsIgnoreCase(config.get("FORCE_H2_MODE", "false"));

        if (!forceH2) {
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
                if (ds != null) {
                    ds.close();
                }
                fallback = true;
                ds = initializeH2(config, "Primary MySQL connection failed: " + e.getMessage());
            }
        } else {
            fallback = true;
            logger.info("FORCE_H2_MODE=true is configured. Skipping MySQL and initializing isolated H2 database.");
            ds = initializeH2(config, "Explicitly configured via FORCE_H2_MODE=true");
        }

        this.dataSource = ds;
        this.isH2Fallback = fallback;
        logger.info("ACTIVE PERSISTENCE ENGINE: {}", getDatabaseType());

        // Apply migrations automatically on startup
        applyMigrations();
    }

    private HikariDataSource initializeH2(AppConfig config, String reason) {
        boolean inMemoryTestMode = "true".equalsIgnoreCase(config.get("H2_IN_MEMORY_ONLY", "false"));
        HikariConfig h2Config = new HikariConfig();

        if (inMemoryTestMode) {
            logger.warn("{}. Initializing in-memory H2 fallback database (H2_IN_MEMORY_ONLY=true).", reason);
            h2Config.setJdbcUrl("jdbc:h2:mem:devopshub;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1");
        } else {
            String storagePath = config.get("DATABASE_STORAGE_PATH", "./data/devopshub");
            File storageFile = new File(storagePath).getAbsoluteFile();
            if (storageFile.getParentFile() != null && !storageFile.getParentFile().exists()) {
                storageFile.getParentFile().mkdirs();
            }
            String normalizedPath = storageFile.getAbsolutePath().replace('\\', '/');
            String diskJdbcUrl = "jdbc:h2:file:" + normalizedPath + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1;AUTO_SERVER=TRUE";
            logger.warn("{}. Initializing durable disk-backed H2 fallback database at {}.", reason, storageFile.getAbsolutePath());
            h2Config.setJdbcUrl(diskJdbcUrl);
        }

        h2Config.setUsername("sa");
        h2Config.setPassword("");
        h2Config.setMaximumPoolSize(5);
        h2Config.setPoolName("DevOpsHub-H2Fallback-Pool");
        return new HikariDataSource(h2Config);
    }

    public static synchronized DatabaseManager getInstance() {
        if (instance == null) {
            instance = new DatabaseManager();
        }
        return instance;
    }

    public static synchronized void resetInstanceForTesting() {
        if (instance != null) {
            if (instance.dataSource != null && !instance.dataSource.isClosed()) {
                instance.dataSource.close();
            }
            instance = null;
            logger.info("DatabaseManager instance reset for persistence verification.");
        }
    }

    public String getDatabaseType() {
        if (!isH2Fallback) {
            return "MySQL (Primary Persistent)";
        }
        boolean inMemory = "true".equalsIgnoreCase(AppConfig.getInstance().get("H2_IN_MEMORY_ONLY", "false"));
        return inMemory ? "H2 (In-Memory Ephemeral)" : "H2 (Embedded Disk-Backed Persistent)";
    }

    public boolean isH2InMemory() {
        return isH2Fallback && "true".equalsIgnoreCase(AppConfig.getInstance().get("H2_IN_MEMORY_ONLY", "false"));
    }

    public boolean backupDatabase(File targetDir) {
        if (targetDir == null) return false;
        if (!targetDir.exists()) targetDir.mkdirs();
        try (Connection conn = getConnection()) {
            if (isH2Fallback) {
                File backupZip = new File(targetDir, "devopshub_h2_backup_" + System.currentTimeMillis() + ".zip");
                // Escape single quotes to prevent SQL syntax errors / path injection
                String safePath = backupZip.getAbsolutePath().replace("'", "''");
                try (Statement stmt = conn.createStatement()) {
                    if (isH2InMemory()) {
                        File tempSql = File.createTempFile("devopshub_dump_", ".sql");
                        try {
                            String tempSqlSafePath = tempSql.getAbsolutePath().replace("'", "''");
                            stmt.execute("SCRIPT DROP TO '" + tempSqlSafePath + "'");
                            try (java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(new java.io.FileOutputStream(backupZip));
                                 java.io.FileInputStream fis = new java.io.FileInputStream(tempSql)) {
                                zos.putNextEntry(new java.util.zip.ZipEntry("devopshub_inmemory_dump.sql"));
                                byte[] buf = new byte[8192];
                                int len;
                                while ((len = fis.read(buf)) > 0) {
                                    zos.write(buf, 0, len);
                                }
                                zos.closeEntry();
                            }
                        } finally {
                            tempSql.delete();
                        }
                    } else {
                        stmt.execute("BACKUP TO '" + safePath + "'");
                    }
                }
                logger.info("H2 database successfully backed up to {}", backupZip.getAbsolutePath());
                return true;
            } else {
                logger.info("MySQL is active. For complete MySQL backup, use standard mysqldump utility or automated snapshot storage.");
                return true;
            }
        } catch (Exception e) {
            logger.error("Database backup operation failed: {}", e.getMessage(), e);
            return false;
        }
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

    public long pingLatencyMillis() {
        long start = System.currentTimeMillis();
        try (Connection conn = getConnection()) {
            if (conn.isValid(3)) {
                return System.currentTimeMillis() - start;
            }
        } catch (SQLException ignored) {}
        return -1;
    }

    public void applyMigrations() {
        logger.info("Applying database migrations with version tracking...");
        ensureSchemaVersionTable();

        if (!isMigrationApplied("V1")) {
            executeSqlScript("/db/migrations/V1__initial_schema.sql");
            recordMigration("V1");
            logger.info("Migration V1__initial_schema applied successfully.");
        }

        if (!isMigrationApplied("V4")) {
            executeSqlScript("/db/migrations/V4__pipeline_schedules.sql");
            recordMigration("V4");
            logger.info("Migration V4__pipeline_schedules applied successfully.");
        }

        // Ensure execution metadata columns (commit_hash, artifact_path, stage command)
        ensureExecutionMetadataColumns();

        // Seed data V2 & V3 are optional demo seeds, disabled by default for fresh workflows
        boolean enableSampleSeed = "true".equalsIgnoreCase(AppConfig.getInstance().get("ENABLE_SAMPLE_SEED_DATA", "false"));
        boolean isBlankDatabase = countTableRows("projects") == 0;
        if (enableSampleSeed && isBlankDatabase && !isMigrationApplied("V2")) {
            logger.info("Sample seed data explicitly enabled. Applying reference seed data...");
            executeSqlScript("/db/migrations/V2__sample_seed_data.sql");
            recordMigration("V2");
            executeSqlScript("/db/migrations/V3__reference_seed_projects.sql");
            recordMigration("V3");
            logger.info("Initial seed data applied successfully.");
        } else {
            if (!isMigrationApplied("V2")) recordMigration("V2");
            if (!isMigrationApplied("V3")) recordMigration("V3");
            logger.info("Database schema initialized fresh without sample seed data.");
        }

        // Reconcile interrupted executions on startup
        reconcileInterruptedExecutions();
    }

    private void ensureExecutionMetadataColumns() {
        try (Connection conn = getConnection()) {
            java.sql.DatabaseMetaData meta = conn.getMetaData();

            // 1. commit_hash column on executions
            boolean hasCommitHash = false;
            try (java.sql.ResultSet rs = meta.getColumns(null, null, "executions", "commit_hash")) {
                if (rs.next()) hasCommitHash = true;
            }
            if (!hasCommitHash) {
                try (java.sql.ResultSet rs = meta.getColumns(null, null, "EXECUTIONS", "COMMIT_HASH")) {
                    if (rs.next()) hasCommitHash = true;
                }
            }
            if (!hasCommitHash) {
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute("ALTER TABLE executions ADD COLUMN commit_hash VARCHAR(100) NULL");
                    logger.info("Added 'commit_hash' column to executions table.");
                } catch (SQLException e) {
                    logger.debug("Column commit_hash might already exist: {}", e.getMessage());
                }
            }

            // 2. artifact_path column on executions
            boolean hasArtifactPath = false;
            try (java.sql.ResultSet rs = meta.getColumns(null, null, "executions", "artifact_path")) {
                if (rs.next()) hasArtifactPath = true;
            }
            if (!hasArtifactPath) {
                try (java.sql.ResultSet rs = meta.getColumns(null, null, "EXECUTIONS", "ARTIFACT_PATH")) {
                    if (rs.next()) hasArtifactPath = true;
                }
            }
            if (!hasArtifactPath) {
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute("ALTER TABLE executions ADD COLUMN artifact_path VARCHAR(500) NULL");
                    logger.info("Added 'artifact_path' column to executions table.");
                } catch (SQLException e) {
                    logger.debug("Column artifact_path might already exist: {}", e.getMessage());
                }
            }

            // 3. command column on stage_executions
            boolean hasCommand = false;
            try (java.sql.ResultSet rs = meta.getColumns(null, null, "stage_executions", "command")) {
                if (rs.next()) hasCommand = true;
            }
            if (!hasCommand) {
                try (java.sql.ResultSet rs = meta.getColumns(null, null, "STAGE_EXECUTIONS", "COMMAND")) {
                    if (rs.next()) hasCommand = true;
                }
            }
            if (!hasCommand) {
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute("ALTER TABLE stage_executions ADD COLUMN command TEXT NULL");
                    logger.info("Added 'command' column to stage_executions table.");
                } catch (SQLException e) {
                    logger.debug("Column command might already exist: {}", e.getMessage());
                }
            }
        } catch (SQLException e) {
            logger.warn("Metadata inspection for executions columns: {}", e.getMessage());
        }
    }

    private void ensureSchemaVersionTable() {
        String sql = "CREATE TABLE IF NOT EXISTS schema_version (" +
                "version VARCHAR(50) PRIMARY KEY, " +
                "applied_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)";
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        } catch (SQLException e) {
            logger.warn("Could not ensure schema_version table: {}", e.getMessage());
        }
    }

    private boolean isMigrationApplied(String version) {
        String sql = "SELECT COUNT(*) FROM schema_version WHERE version = ?";
        try (Connection conn = getConnection(); java.sql.PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, version);
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        } catch (SQLException e) {
            logger.debug("Error checking migration version {}: {}", version, e.getMessage());
        }
        return false;
    }

    private void recordMigration(String version) {
        String sql = "INSERT INTO schema_version (version) VALUES (?)";
        try (Connection conn = getConnection(); java.sql.PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, version);
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.debug("Error recording migration version {}: {}", version, e.getMessage());
        }
    }

    private int countTableRows(String tableName) {
        String sql = "SELECT COUNT(*) FROM " + tableName;
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement();
             java.sql.ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (SQLException ignored) {}
        return 0;
    }

    public void reconcileInterruptedExecutions() {
        String sqlExec = "UPDATE executions SET status = 'FAILED' WHERE status IN ('RUNNING', 'QUEUED')";
        String sqlStages = "UPDATE stage_executions SET status = 'FAILED', error_message = 'Process interrupted by application restart' WHERE status = 'RUNNING'";
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            int execs = stmt.executeUpdate(sqlExec);
            int stages = stmt.executeUpdate(sqlStages);
            if (execs > 0 || stages > 0) {
                logger.info("Reconciled {} orphaned executions and {} stage executions from previous interrupted run.", execs, stages);
            }
        } catch (SQLException e) {
            logger.warn("Could not reconcile interrupted executions: {}", e.getMessage());
        }
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
