package com.devopshub.repository;

import com.devopshub.config.DatabaseManager;
import com.devopshub.model.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class DatabaseIntegrationTest {

    private static DatabaseManager dbManager;
    private static ProjectRepository projectRepository;
    private static PipelineRepository pipelineRepository;
    private static ExecutionRepository executionRepository;
    private static LogRepository logRepository;
    private static DiagnosisRepository diagnosisRepository;

    @BeforeAll
    static void init() {
        dbManager = DatabaseManager.getInstance();
        projectRepository = new ProjectRepository(dbManager);
        pipelineRepository = new PipelineRepository(dbManager);
        executionRepository = new ExecutionRepository(dbManager);
        logRepository = new LogRepository(dbManager);
        diagnosisRepository = new DiagnosisRepository(dbManager);
    }

    @Test
    @DisplayName("Verify JDBC connection is valid")
    void testJdbcConnection() throws Exception {
        try (Connection conn = dbManager.getConnection()) {
            assertNotNull(conn);
            assertFalse(conn.isClosed());
            assertTrue(conn.isValid(2));
        }
    }

    @Test
    @DisplayName("Verify Project CRUD and persistence across queries")
    void testProjectPersistence() {
        String id = "test-proj-" + UUID.randomUUID();
        Project p = new Project(id, "Persistence Test " + System.currentTimeMillis(), "Desc", "https://github.com/vit/test.git", "main", "Java", "ACTIVE");
        projectRepository.save(p);

        Project fetched = projectRepository.findById(id).orElseThrow();
        assertEquals(p.getName(), fetched.getName());
        assertEquals(p.getRepositoryUrl(), fetched.getRepositoryUrl());

        fetched.setDescription("Updated Description");
        projectRepository.update(fetched);

        Project updated = projectRepository.findById(id).orElseThrow();
        assertEquals("Updated Description", updated.getDescription());

        projectRepository.delete(id);
        assertTrue(projectRepository.findById(id).isEmpty());
    }

    @Test
    @DisplayName("Verify Pipeline with Stages and Stage Dependencies persistence")
    void testPipelineAndStagesPersistence() {
        String projId = "proj-001";
        String pipeId = "test-pipe-" + UUID.randomUUID();

        Pipeline pipe = new Pipeline(pipeId, projId, "Integration Pipeline", "Desc", "SEQUENTIAL", true);
        Stage s1 = new Stage(UUID.randomUUID().toString(), pipeId, "S1", StageType.BUILD, 1, "echo 1", 100, true, null);
        Stage s2 = new Stage(UUID.randomUUID().toString(), pipeId, "S2", StageType.TEST, 2, "echo 2", 100, true, null);
        s2.getDependsOnStageIds().add(s1.getId());
        pipe.setStages(List.of(s1, s2));

        pipelineRepository.save(pipe);

        Pipeline fetched = pipelineRepository.findById(pipeId).orElseThrow();
        assertEquals("Integration Pipeline", fetched.getName());
        assertEquals(2, fetched.getStages().size());

        pipelineRepository.delete(pipeId);
        assertTrue(pipelineRepository.findById(pipeId).isEmpty());
    }

    @Test
    @DisplayName("Verify Log insertion and bounded retrieval")
    void testLogPersistence() {
        String execId = "exec-log-test-" + UUID.randomUUID();
        // Save parent execution first to satisfy foreign key
        Execution ex = new Execution(execId, "pipe-001", "proj-001", 999, ExecutionStatus.RUNNING, TriggerType.MANUAL);
        executionRepository.save(ex);

        ExecutionLog log1 = new ExecutionLog(execId, "se-1", "Build", LogLevel.INFO, LogStream.STDOUT, "Log line 1");
        ExecutionLog log2 = new ExecutionLog(execId, "se-1", "Build", LogLevel.ERROR, LogStream.STDERR, "Error log line 2");

        logRepository.saveBatch(List.of(log1, log2));

        List<ExecutionLog> logs = logRepository.findByExecutionId(execId);
        assertEquals(2, logs.size());
        assertTrue(logs.get(0).getMessage().contains("Log line 1"));
        assertTrue(logs.get(1).getMessage().contains("Error log line 2"));
    }

    @Test
    @DisplayName("Verify Diagnosis persistence and category counts")
    void testDiagnosisPersistence() {
        String execId = "exec-diag-test-" + UUID.randomUUID();
        // Save parent execution first to satisfy foreign key
        Execution ex = new Execution(execId, "pipe-001", "proj-001", 998, ExecutionStatus.FAILED, TriggerType.MANUAL);
        executionRepository.save(ex);

        String diagId = "diag-" + UUID.randomUUID();
        FailureDiagnosis d = new FailureDiagnosis(diagId, execId, "se-1", "Test Stage",
                FailureCategory.ASSERTION_FAILURE, "Test Failure", "Likely cause",
                "Evidence text", "Suggested recovery action", "RULE_TEST_01", "1.0");

        diagnosisRepository.save(d);

        FailureDiagnosis fetched = diagnosisRepository.findByExecutionId(execId).orElseThrow();
        assertEquals("Test Failure", fetched.getTitle());
        assertEquals(FailureCategory.ASSERTION_FAILURE, fetched.getCategory());
        assertEquals("RULE_TEST_01", fetched.getRuleId());
    }

    @Test
    @DisplayName("Verify Pipeline duplication with copied stages")
    void testPipelineDuplication() {
        String pipeId = "dup-pipe-" + UUID.randomUUID();
        Pipeline p = new Pipeline(pipeId, "proj-001", "Original Pipeline", "Desc", "SEQUENTIAL", true);
        Stage s1 = new Stage(UUID.randomUUID().toString(), pipeId, "S1", StageType.BUILD, 1, "echo 1", 60, true, null);
        p.setStages(List.of(s1));
        pipelineRepository.save(p);

        Pipeline copy = pipelineRepository.duplicate(pipeId);
        assertNotNull(copy);
        assertNotEquals(p.getId(), copy.getId());
        assertTrue(copy.getName().contains("(Copy)"));
        assertEquals(1, copy.getStages().size());
        assertNotEquals(s1.getId(), copy.getStages().get(0).getId());

        pipelineRepository.delete(pipeId);
        pipelineRepository.delete(copy.getId());
    }

    @Test
    @DisplayName("Verify query executions by project and by pipeline")
    void testExecutionQueryByProjectAndPipeline() {
        String execId = "query-test-" + UUID.randomUUID();
        Execution ex = new Execution(execId, "pipe-001", "proj-001", 1001, ExecutionStatus.SUCCESS, TriggerType.MANUAL);
        executionRepository.save(ex);

        List<Execution> byProj = executionRepository.findByProjectId("proj-001");
        assertFalse(byProj.isEmpty());
        assertTrue(byProj.stream().anyMatch(e -> e.getId().equals(execId)));

        List<Execution> byPipe = executionRepository.findByPipelineId("pipe-001");
        assertFalse(byPipe.isEmpty());
        assertTrue(byPipe.stream().anyMatch(e -> e.getId().equals(execId)));
    }

    @Test
    @DisplayName("Verify ping latency measurement returns valid milliseconds")
    void testPingLatency() {
        long latency = dbManager.pingLatencyMillis();
        assertTrue(latency >= 0, "Ping latency should be non-negative");
    }
}
