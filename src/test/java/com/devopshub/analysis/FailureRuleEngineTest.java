package com.devopshub.analysis;

import com.devopshub.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FailureRuleEngineTest {

    private FailureRuleEngine engine;

    @BeforeEach
    void setUp() {
        engine = new FailureRuleEngine();
    }

    @Test
    @DisplayName("Slide 8 Example: HTTP 401 vs 500 in LoginService triggers AssertionFailureRule")
    void testSlide8Http401vs500Failure() {
        StageExecution failedStage = new StageExecution("se-1", "exec-1", "stage-test", "Unit & Integration Tests", StageType.TEST);
        failedStage.setStatus(StageExecutionStatus.FAILED);
        failedStage.setExitCode(1);

        List<ExecutionLog> logs = new ArrayList<>();
        logs.add(new ExecutionLog("exec-1", "se-1", "Unit & Integration Tests", LogLevel.INFO, LogStream.STDOUT, "[10:42:08.114] TEST LoginServiceTest"));
        logs.add(new ExecutionLog("exec-1", "se-1", "Unit & Integration Tests", LogLevel.ERROR, LogStream.STDERR, "[10:42:09.021] expected HTTP 401"));
        logs.add(new ExecutionLog("exec-1", "se-1", "Unit & Integration Tests", LogLevel.ERROR, LogStream.STDERR, "[10:42:09.023] received HTTP 500"));
        logs.add(new ExecutionLog("exec-1", "se-1", "Unit & Integration Tests", LogLevel.ERROR, LogStream.STDERR, "[10:42:09.026] exception at LoginService"));

        FailureDiagnosis diagnosis = engine.analyze(failedStage, logs);

        assertNotNull(diagnosis);
        assertEquals(FailureCategory.ASSERTION_FAILURE, diagnosis.getCategory());
        assertEquals("RULE_ASSERTION_FAILURE_01", diagnosis.getRuleId());
        assertTrue(diagnosis.getTitle().contains("HTTP Status Code Mismatch"));
        assertTrue(diagnosis.getLikelyCause().contains("LoginService"));
        assertTrue(diagnosis.getSuggestedAction().contains("Inspect LoginService error handling before rerun"));
        assertTrue(diagnosis.getEvidenceExcerpt().contains("expected HTTP 401"));
        assertTrue(diagnosis.getEvidenceExcerpt().contains("received HTTP 500"));
    }

    @Test
    @DisplayName("Compilation error triggers CompilationErrorRule")
    void testCompilationFailure() {
        StageExecution failedStage = new StageExecution("se-2", "exec-2", "stage-build", "Build Application", StageType.BUILD);
        failedStage.setStatus(StageExecutionStatus.FAILED);
        failedStage.setExitCode(1);

        List<ExecutionLog> logs = new ArrayList<>();
        logs.add(new ExecutionLog("exec-2", "se-2", "Build Application", LogLevel.ERROR, LogStream.STDERR, "[ERROR] /src/Main.java:[14,22] cannot find symbol"));
        logs.add(new ExecutionLog("exec-2", "se-2", "Build Application", LogLevel.ERROR, LogStream.STDERR, "  symbol:   method execute()"));
        logs.add(new ExecutionLog("exec-2", "se-2", "Build Application", LogLevel.ERROR, LogStream.STDERR, "[INFO] 1 error"));

        FailureDiagnosis diagnosis = engine.analyze(failedStage, logs);

        assertNotNull(diagnosis);
        assertEquals(FailureCategory.COMPILATION_ERROR, diagnosis.getCategory());
        assertEquals("RULE_COMPILATION_ERROR_02", diagnosis.getRuleId());
        assertTrue(diagnosis.getTitle().contains("Compilation Failure"));
        assertTrue(diagnosis.getSuggestedAction().contains("recompile locally"));
    }

    @Test
    @DisplayName("Missing dependency triggers MissingDependencyRule")
    void testMissingDependencyFailure() {
        StageExecution failedStage = new StageExecution("se-3", "exec-3", "stage-build", "Build Application", StageType.BUILD);
        failedStage.setStatus(StageExecutionStatus.FAILED);
        failedStage.setExitCode(1);

        List<ExecutionLog> logs = new ArrayList<>();
        logs.add(new ExecutionLog("exec-3", "se-3", "Build Application", LogLevel.ERROR, LogStream.STDERR, "Could not resolve dependencies for project"));
        logs.add(new ExecutionLog("exec-3", "se-3", "Build Application", LogLevel.ERROR, LogStream.STDERR, "java.lang.ClassNotFoundException: org.example.MissingClass"));

        FailureDiagnosis diagnosis = engine.analyze(failedStage, logs);

        assertNotNull(diagnosis);
        assertEquals(FailureCategory.MISSING_DEPENDENCY, diagnosis.getCategory());
        assertEquals("RULE_DEPENDENCY_03", diagnosis.getRuleId());
        assertTrue(diagnosis.getSuggestedAction().contains("mvn dependency:resolve"));
    }

    @Test
    @DisplayName("Database connection refused triggers DatabaseConnectionRule")
    void testDatabaseConnectionFailure() {
        StageExecution failedStage = new StageExecution("se-4", "exec-4", "stage-test", "Integration Tests", StageType.TEST);
        failedStage.setStatus(StageExecutionStatus.FAILED);
        failedStage.setExitCode(1);

        List<ExecutionLog> logs = new ArrayList<>();
        logs.add(new ExecutionLog("exec-4", "se-4", "Integration Tests", LogLevel.ERROR, LogStream.STDERR, "Communications link failure: Connection refused"));

        FailureDiagnosis diagnosis = engine.analyze(failedStage, logs);

        assertNotNull(diagnosis);
        assertEquals(FailureCategory.DATABASE_ERROR, diagnosis.getCategory());
        assertEquals("RULE_DATABASE_04", diagnosis.getRuleId());
        assertTrue(diagnosis.getSuggestedAction().contains("MySQL is running on port 3306"));
    }

    @Test
    @DisplayName("Timeout exit code 124 triggers TimeoutRule")
    void testTimeoutFailure() {
        StageExecution failedStage = new StageExecution("se-5", "exec-5", "stage-test", "Performance Tests", StageType.TEST);
        failedStage.setStatus(StageExecutionStatus.FAILED);
        failedStage.setExitCode(124);

        List<ExecutionLog> logs = new ArrayList<>();
        logs.add(new ExecutionLog("exec-5", "se-5", "Performance Tests", LogLevel.ERROR, LogStream.STDERR, "Stage execution timed out after 300 seconds."));

        FailureDiagnosis diagnosis = engine.analyze(failedStage, logs);

        assertNotNull(diagnosis);
        assertEquals(FailureCategory.TIMEOUT, diagnosis.getCategory());
        assertEquals("RULE_TIMEOUT_05", diagnosis.getRuleId());
        assertTrue(diagnosis.getTitle().contains("Exceeded Timeout"));
    }

    @Test
    @DisplayName("Unclassified failure triggers fallback diagnosis")
    void testFallbackDiagnosis() {
        StageExecution failedStage = new StageExecution("se-6", "exec-6", "stage-custom", "Custom Script", StageType.DEPLOY);
        failedStage.setStatus(StageExecutionStatus.FAILED);
        failedStage.setExitCode(7);

        List<ExecutionLog> logs = new ArrayList<>();
        logs.add(new ExecutionLog("exec-6", "se-6", "Custom Script", LogLevel.ERROR, LogStream.STDERR, "Unrecognized custom error 0xDEADBEEF"));

        FailureDiagnosis diagnosis = engine.analyze(failedStage, logs);

        assertNotNull(diagnosis);
        assertEquals(FailureCategory.UNKNOWN, diagnosis.getCategory());
        assertEquals("RULE_DEFAULT_FALLBACK", diagnosis.getRuleId());
    }
}
