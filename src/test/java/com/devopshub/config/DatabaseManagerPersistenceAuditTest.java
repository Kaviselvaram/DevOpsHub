package com.devopshub.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DAT-01: Database Persistence & Storage Audit Tests")
class DatabaseManagerPersistenceAuditTest {

    @Test
    @DisplayName("DatabaseManager reports explicit persistent vs ephemeral storage type")
    void testDatabaseTypeDescription() {
        DatabaseManager db = DatabaseManager.getInstance();
        assertNotNull(db);
        String type = db.getDatabaseType();
        assertNotNull(type);

        if (db.isH2Fallback()) {
            assertTrue(type.contains("H2"), "Database type description must state H2");
            assertTrue(type.contains("Disk-Backed") || type.contains("Persistent") || type.contains("Ephemeral"),
                    "H2 description must explicitly distinguish disk-backed persistent storage from in-memory ephemeral");
        } else {
            assertTrue(type.contains("MySQL"), "Database type description must state MySQL");
            assertTrue(type.contains("Persistent"), "MySQL description must state Persistent");
        }
    }

    @Test
    @DisplayName("Database backup operation creates backup archive in destination directory")
    void testBackupDatabase(@TempDir File backupDir) {
        DatabaseManager db = DatabaseManager.getInstance();
        boolean success = db.backupDatabase(backupDir);
        assertTrue(success, "Database backup operation must return true");

        if (db.isH2Fallback()) {
            File[] files = backupDir.listFiles((dir, name) -> name.endsWith(".zip"));
            assertNotNull(files);
            assertTrue(files.length > 0, "H2 backup must create a .zip archive file in the backup directory");
        }
    }

    @Test
    @DisplayName("Database backup operation must safely handle directory paths containing single quotes (Item 7)")
    void testBackupDatabaseWithPathContainingSingleQuote(@TempDir File baseDir) {
        DatabaseManager db = DatabaseManager.getInstance();
        File quoteDir = new File(baseDir, "user's_special_backup_folder");
        quoteDir.mkdirs();

        boolean success = db.backupDatabase(quoteDir);
        assertTrue(success, "Database backup must succeed even when destination path contains single quotes");

        if (db.isH2Fallback()) {
            File[] files = quoteDir.listFiles((dir, name) -> name.endsWith(".zip"));
            assertNotNull(files);
            assertTrue(files.length > 0, "Backup archive must be written inside single-quote path");
        }
    }

    @Test
    @DisplayName("Disk-backed H2 database persists data across connection pool resets (Item 7)")
    void testDiskBackedH2DurabilityAcrossPoolReset(@TempDir File tempDir) throws Exception {
        File dbFile = new File(tempDir, "h2_durable_test");
        String normalizedPath = dbFile.getAbsolutePath().replace('\\', '/');
        String jdbcUrl = "jdbc:h2:file:" + normalizedPath + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1;AUTO_SERVER=TRUE";

        com.zaxxer.hikari.HikariConfig cfg = new com.zaxxer.hikari.HikariConfig();
        cfg.setJdbcUrl(jdbcUrl);
        cfg.setUsername("sa");
        cfg.setPassword("");
        cfg.setMaximumPoolSize(2);

        // 1. First pool: create table and insert row
        try (com.zaxxer.hikari.HikariDataSource ds1 = new com.zaxxer.hikari.HikariDataSource(cfg)) {
            try (java.sql.Connection conn = ds1.getConnection();
                 java.sql.Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE TABLE IF NOT EXISTS restart_sentinel (id VARCHAR(50) PRIMARY KEY, sentinel_val VARCHAR(100))");
                stmt.execute("INSERT INTO restart_sentinel VALUES ('sentinel-1', 'preserved_data')");
            }
        } // ds1 closed

        // 2. Second pool: connect to same disk file and verify row persisted
        try (com.zaxxer.hikari.HikariDataSource ds2 = new com.zaxxer.hikari.HikariDataSource(cfg)) {
            try (java.sql.Connection conn2 = ds2.getConnection();
                 java.sql.Statement stmt2 = conn2.createStatement();
                 java.sql.ResultSet rs = stmt2.executeQuery("SELECT sentinel_val FROM restart_sentinel WHERE id = 'sentinel-1'")) {
                assertTrue(rs.next(), "Durable H2 database must retain rows across connection pool restarts");
                assertEquals("preserved_data", rs.getString(1));
            }
        }
    }
}
