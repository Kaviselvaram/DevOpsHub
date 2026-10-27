package com.devopshub.repository;

import com.devopshub.config.AppConfig;
import com.devopshub.config.DatabaseManager;
import com.devopshub.model.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class IndependentPersistenceAuditTest {

    @Test
    @DisplayName("Audit 2.1: Verify active database mode at runtime without exposing credentials")
    void testActiveDatabaseModeRuntime() {
        DatabaseManager dbManager = DatabaseManager.getInstance();
        assertNotNull(dbManager);
        String dbType = dbManager.getDatabaseType();
        assertNotNull(dbType);
        System.out.println("AUDIT_EVIDENCE_ACTIVE_PERSISTENCE_MODE: " + dbType);
        System.out.println("AUDIT_EVIDENCE_IS_H2_FALLBACK: " + dbManager.isH2Fallback());
        assertTrue(dbManager.testConnection(), "Active database connection pool must be valid");
    }

    @Test
    @DisplayName("Audit 2.2: Verify restart durability against MySQL by resetting connection pool")
    void testRestartDurabilityAgainstMySQL() {
        DatabaseManager dbManager = DatabaseManager.getInstance();
        if (dbManager.isH2Fallback()) {
            System.out.println("SKIPPING restart durability against MySQL because currently in H2 fallback mode");
            return;
        }

        ProjectRepository projRepo = new ProjectRepository(dbManager);
        PipelineRepository pipeRepo = new PipelineRepository(dbManager);
        ExecutionRepository execRepo = new ExecutionRepository(dbManager);

        String projId = "proj-audit-restart-" + UUID.randomUUID();
        String pipeId = "pipe-audit-restart-" + UUID.randomUUID();
        String execId = "exec-audit-restart-" + UUID.randomUUID();

        try {
            // 1. Create project, pipeline, execution
            Project proj = new Project(projId, "Restart Durability Proj", "Audit", "https://github.com/example/repo.git", "main", "Java", "ACTIVE");
            projRepo.save(proj);

            Pipeline pipe = new Pipeline(pipeId, projId, "Restart Durability Pipe", "Desc", "SEQUENTIAL", true);
            Stage s1 = new Stage(UUID.randomUUID().toString(), pipeId, "S1", StageType.BUILD, 1, "echo 1", 30, true, null);
            pipe.setStages(List.of(s1));
            pipeRepo.save(pipe);

            Execution exec = new Execution(execId, pipeId, projId, 1, ExecutionStatus.SUCCESS, TriggerType.MANUAL);
            execRepo.save(exec);

            System.out.println("AUDIT_EVIDENCE: Created test entities before restart simulation");

            // 2. Simulate application shutdown / restart by closing connection pool and resetting singleton
            DatabaseManager.resetInstanceForTesting();

            // 3. Re-initialize DatabaseManager (new connection pool)
            DatabaseManager restartedDb = DatabaseManager.getInstance();
            assertFalse(restartedDb.isH2Fallback(), "Restarted DB should reconnect to MySQL");

            ProjectRepository restartedProjRepo = new ProjectRepository(restartedDb);
            PipelineRepository restartedPipeRepo = new PipelineRepository(restartedDb);
            ExecutionRepository restartedExecRepo = new ExecutionRepository(restartedDb);

            // 4. Verify entities exist across restart
            Project reloadedProj = restartedProjRepo.findById(projId).orElse(null);
            assertNotNull(reloadedProj, "Project must persist across database pool restart");
            assertEquals("Restart Durability Proj", reloadedProj.getName());

            Pipeline reloadedPipe = restartedPipeRepo.findById(pipeId).orElse(null);
            assertNotNull(reloadedPipe, "Pipeline must persist across database pool restart");
            assertEquals(1, reloadedPipe.getStages().size());

            Execution reloadedExec = restartedExecRepo.findById(execId).orElse(null);
            assertNotNull(reloadedExec, "Execution must persist across database pool restart");
            assertEquals(ExecutionStatus.SUCCESS, reloadedExec.getStatus());

            System.out.println("AUDIT_EVIDENCE: Successfully verified restart durability across connection pool recreation");

        } finally {
            // Cleanup
            DatabaseManager currentDb = DatabaseManager.getInstance();
            new ExecutionRepository(currentDb).delete(execId);
            new PipelineRepository(currentDb).delete(pipeId);
            new ProjectRepository(currentDb).delete(projId);
        }
    }

    @Test
    @DisplayName("Audit 2.3: Demonstrate in-memory H2 volatility on reset vs durable storage distinction")
    void testH2VolatilityDemonstration() throws Exception {
        // Direct test showing that H2 in-memory storage loses state when connection closes without DB_CLOSE_DELAY=-1 or on fresh pool
        String memUrl = "jdbc:h2:mem:audit_ephemeral_test;MODE=MySQL";
        com.zaxxer.hikari.HikariConfig cfg = new com.zaxxer.hikari.HikariConfig();
        cfg.setJdbcUrl(memUrl);
        cfg.setUsername("sa");
        cfg.setPassword("");
        cfg.setMaximumPoolSize(2);

        com.zaxxer.hikari.HikariDataSource ds = new com.zaxxer.hikari.HikariDataSource(cfg);
        try (Connection conn = ds.getConnection();
             java.sql.Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE ephemeral_test (id VARCHAR(50) PRIMARY KEY, data VARCHAR(100))");
            stmt.execute("INSERT INTO ephemeral_test VALUES ('1', 'volatile')");
        }
        ds.close(); // Close pool

        // Reconnect to a new in-memory instance
        com.zaxxer.hikari.HikariDataSource ds2 = new com.zaxxer.hikari.HikariDataSource(cfg);
        try (Connection conn2 = ds2.getConnection();
             java.sql.Statement stmt2 = conn2.createStatement()) {
            // Table should not exist or be empty in a fresh in-memory DB
            assertThrows(Exception.class, () -> stmt2.executeQuery("SELECT * FROM ephemeral_test"));
            System.out.println("AUDIT_EVIDENCE: Verified that ephemeral storage loses all data upon pool termination");
        } finally {
            ds2.close();
        }
    }
}
