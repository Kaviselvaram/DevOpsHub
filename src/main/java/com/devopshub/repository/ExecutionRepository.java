package com.devopshub.repository;

import com.devopshub.config.DatabaseManager;
import com.devopshub.exception.DatabaseException;
import com.devopshub.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ExecutionRepository {
    private static final Logger logger = LoggerFactory.getLogger(ExecutionRepository.class);
    private final DatabaseManager dbManager;

    public ExecutionRepository() {
        this.dbManager = DatabaseManager.getInstance();
    }

    public ExecutionRepository(DatabaseManager dbManager) {
        this.dbManager = dbManager;
    }

    public List<Execution> findAll() {
        String sql = "SELECT e.*, pl.name as pipeline_name, pr.name as project_name, pr.project_type as project_type, pr.repository_url as project_repository_url " +
                "FROM executions e " +
                "JOIN pipelines pl ON e.pipeline_id = pl.id " +
                "JOIN projects pr ON e.project_id = pr.id " +
                "ORDER BY e.created_at DESC";
        List<Execution> list = new ArrayList<>();
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapExecution(rs));
            }
        } catch (SQLException e) {
            logger.error("Failed to query executions: {}", e.getMessage());
            throw new DatabaseException("Failed to query executions", e);
        }

        for (Execution ex : list) {
            ex.setStageExecutions(findStageExecutionsByExecutionId(ex.getId()));
        }

        return list;
    }

    public List<Execution> findRecent(int limit) {
        String sql = "SELECT e.*, pl.name as pipeline_name, pr.name as project_name, pr.project_type as project_type, pr.repository_url as project_repository_url " +
                "FROM executions e " +
                "JOIN pipelines pl ON e.pipeline_id = pl.id " +
                "JOIN projects pr ON e.project_id = pr.id " +
                "ORDER BY e.created_at DESC LIMIT ?";
        List<Execution> list = new ArrayList<>();
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapExecution(rs));
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to query recent executions: {}", e.getMessage());
            throw new DatabaseException("Failed to query recent executions", e);
        }

        for (Execution ex : list) {
            ex.setStageExecutions(findStageExecutionsByExecutionId(ex.getId()));
        }

        return list;
    }

    public List<Execution> findByProjectId(String projectId) {
        String sql = "SELECT e.*, pl.name as pipeline_name, pr.name as project_name, pr.project_type as project_type, pr.repository_url as project_repository_url " +
                "FROM executions e " +
                "JOIN pipelines pl ON e.pipeline_id = pl.id " +
                "JOIN projects pr ON e.project_id = pr.id " +
                "WHERE e.project_id = ? " +
                "ORDER BY e.created_at DESC";
        List<Execution> list = new ArrayList<>();
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, projectId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapExecution(rs));
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to query executions by project id: {}", e.getMessage());
            throw new DatabaseException("Failed to query executions by project id", e);
        }

        for (Execution ex : list) {
            ex.setStageExecutions(findStageExecutionsByExecutionId(ex.getId()));
        }

        return list;
    }

    public List<Execution> findByPipelineId(String pipelineId) {
        String sql = "SELECT e.*, pl.name as pipeline_name, pr.name as project_name, pr.project_type as project_type, pr.repository_url as project_repository_url " +
                "FROM executions e " +
                "JOIN pipelines pl ON e.pipeline_id = pl.id " +
                "JOIN projects pr ON e.project_id = pr.id " +
                "WHERE e.pipeline_id = ? " +
                "ORDER BY e.created_at DESC";
        List<Execution> list = new ArrayList<>();
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, pipelineId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapExecution(rs));
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to query executions by pipeline id: {}", e.getMessage());
            throw new DatabaseException("Failed to query executions by pipeline id", e);
        }

        for (Execution ex : list) {
            ex.setStageExecutions(findStageExecutionsByExecutionId(ex.getId()));
        }

        return list;
    }

    public Optional<Execution> findById(String id) {
        String sql = "SELECT e.*, pl.name as pipeline_name, pr.name as project_name, pr.project_type as project_type, pr.repository_url as project_repository_url " +
                "FROM executions e " +
                "JOIN pipelines pl ON e.pipeline_id = pl.id " +
                "JOIN projects pr ON e.project_id = pr.id " +
                "WHERE e.id = ?";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Execution ex = mapExecution(rs);
                    ex.setStageExecutions(findStageExecutionsByExecutionId(ex.getId()));
                    return Optional.of(ex);
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to query execution by id: {}", e.getMessage());
            throw new DatabaseException("Failed to query execution by id", e);
        }
        return Optional.empty();
    }

    public int getNextExecutionNumber(String pipelineId) {
        String sql = "SELECT COALESCE(MAX(execution_number), 0) + 1 FROM executions WHERE pipeline_id = ?";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, pipelineId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to get next execution number: {}", e.getMessage());
        }
        return 1;
    }

    public void save(Execution execution) {
        String sql = "INSERT INTO executions (id, pipeline_id, project_id, execution_number, status, trigger_type, started_at, finished_at, duration_seconds, commit_hash, artifact_path, created_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, execution.getId());
            ps.setString(2, execution.getPipelineId());
            ps.setString(3, execution.getProjectId());
            ps.setInt(4, execution.getExecutionNumber());
            ps.setString(5, execution.getStatus().name());
            ps.setString(6, execution.getTriggerType().name());
            ps.setTimestamp(7, execution.getStartedAt());
            ps.setTimestamp(8, execution.getFinishedAt());
            ps.setDouble(9, execution.getDurationSeconds());
            ps.setString(10, execution.getCommitHash());
            ps.setString(11, execution.getArtifactPath());
            ps.setTimestamp(12, execution.getCreatedAt() != null ? execution.getCreatedAt() : new Timestamp(System.currentTimeMillis()));
            ps.executeUpdate();
            logger.info("Saved execution: #{} ({})", execution.getExecutionNumber(), execution.getId());
        } catch (SQLException e) {
            logger.error("Failed to save execution: {}", e.getMessage());
            throw new DatabaseException("Failed to save execution: " + e.getMessage(), e);
        }
    }

    public void update(Execution execution) {
        String sql = "UPDATE executions SET status = ?, started_at = ?, finished_at = ?, duration_seconds = ?, commit_hash = ?, artifact_path = ? WHERE id = ?";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, execution.getStatus().name());
            ps.setTimestamp(2, execution.getStartedAt());
            ps.setTimestamp(3, execution.getFinishedAt());
            ps.setDouble(4, execution.getDurationSeconds());
            ps.setString(5, execution.getCommitHash());
            ps.setString(6, execution.getArtifactPath());
            ps.setString(7, execution.getId());
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.error("Failed to update execution: {}", e.getMessage());
            throw new DatabaseException("Failed to update execution", e);
        }
    }

    public List<StageExecution> findStageExecutionsByExecutionId(String executionId) {
        String sql = "SELECT se.*, COALESCE(se.command, ps.command) as resolved_command FROM stage_executions se LEFT JOIN pipeline_stages ps ON se.stage_id = ps.id WHERE se.execution_id = ? ORDER BY ps.stage_order ASC, se.id ASC";
        List<StageExecution> list = new ArrayList<>();
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, executionId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    StageExecution se = new StageExecution();
                    se.setId(rs.getString("id"));
                    se.setExecutionId(rs.getString("execution_id"));
                    se.setStageId(rs.getString("stage_id"));
                    se.setStageName(rs.getString("stage_name"));
                    se.setStageType(StageType.fromString(rs.getString("stage_type")));
                    se.setCommand(rs.getString("resolved_command"));
                    se.setStatus(StageExecutionStatus.fromString(rs.getString("status")));
                    se.setExitCode(rs.getInt("exit_code"));
                    se.setExitCodeSet(se.getStatus() == StageExecutionStatus.SUCCESS || se.getStatus() == StageExecutionStatus.FAILED);
                    se.setStartedAt(rs.getTimestamp("started_at"));
                    se.setFinishedAt(rs.getTimestamp("finished_at"));
                    se.setDurationSeconds(rs.getDouble("duration_seconds"));
                    se.setErrorMessage(rs.getString("error_message"));
                    list.add(se);
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to query stage executions: {}", e.getMessage());
        }
        return list;
    }

    public void saveStageExecution(StageExecution se) {
        String sql = "INSERT INTO stage_executions (id, execution_id, stage_id, stage_name, stage_type, command, status, exit_code, started_at, finished_at, duration_seconds, error_message) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE command=VALUES(command), status=VALUES(status), exit_code=VALUES(exit_code), started_at=VALUES(started_at), " +
                "finished_at=VALUES(finished_at), duration_seconds=VALUES(duration_seconds), error_message=VALUES(error_message)";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, se.getId());
            ps.setString(2, se.getExecutionId());
            ps.setString(3, se.getStageId());
            ps.setString(4, se.getStageName());
            ps.setString(5, se.getStageType().name());
            ps.setString(6, se.getCommand());
            ps.setString(7, se.getStatus().name());
            ps.setInt(8, se.getExitCode());
            ps.setTimestamp(9, se.getStartedAt());
            ps.setTimestamp(10, se.getFinishedAt());
            ps.setDouble(11, se.getDurationSeconds());
            ps.setString(12, se.getErrorMessage());
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.error("Failed to save stage execution: {}", e.getMessage());
        }
    }

    public void updateStageExecution(StageExecution se) {
        saveStageExecution(se);
    }

    public int countByStatus(ExecutionStatus status) {
        return countByStatus(status, null);
    }

    public int countByStatus(ExecutionStatus status, Timestamp since) {
        String sql = since == null ?
                "SELECT COUNT(*) FROM executions WHERE status = ?" :
                "SELECT COUNT(*) FROM executions WHERE status = ? AND created_at >= ?";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status.name());
            if (since != null) {
                ps.setTimestamp(2, since);
            }
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException e) {
            logger.error("Failed to count executions by status: {}", e.getMessage());
        }
        return 0;
    }

    public int countAll() {
        return countAll(null);
    }

    public int countAll(Timestamp since) {
        String sql = since == null ?
                "SELECT COUNT(*) FROM executions" :
                "SELECT COUNT(*) FROM executions WHERE created_at >= ?";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            if (since != null) {
                ps.setTimestamp(1, since);
            }
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException e) {
            logger.error("Failed to count executions: {}", e.getMessage());
        }
        return 0;
    }

    public double getAverageDurationSeconds() {
        return getAverageDurationSeconds(null);
    }

    public double getAverageDurationSeconds(Timestamp since) {
        String sql = since == null ?
                "SELECT AVG(duration_seconds) FROM executions WHERE duration_seconds > 0 AND status IN ('SUCCESS', 'FAILED')" :
                "SELECT AVG(duration_seconds) FROM executions WHERE duration_seconds > 0 AND status IN ('SUCCESS', 'FAILED') AND created_at >= ?";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            if (since != null) {
                ps.setTimestamp(1, since);
            }
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getDouble(1);
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to get average duration: {}", e.getMessage());
        }
        return 0.0;
    }

    public StageOperationalStats getStageStats(List<StageType> types) {
        if (types == null || types.isEmpty()) return new StageOperationalStats(0, 0, 0, 0.0);
        StringBuilder placeholders = new StringBuilder();
        for (int i = 0; i < types.size(); i++) {
            if (i > 0) placeholders.append(",");
            placeholders.append("?");
        }
        String sql = "SELECT COUNT(*) as total_runs, " +
                "SUM(CASE WHEN status = 'FAILED' THEN 1 ELSE 0 END) as failed_runs, " +
                "SUM(CASE WHEN status = 'RUNNING' THEN 1 ELSE 0 END) as running_runs, " +
                "SUM(duration_seconds) as total_duration " +
                "FROM stage_executions WHERE stage_type IN (" + placeholders + ")";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < types.size(); i++) {
                ps.setString(i + 1, types.get(i).name());
            }
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    int total = rs.getInt("total_runs");
                    int failed = rs.getInt("failed_runs");
                    int running = rs.getInt("running_runs");
                    double dur = rs.getDouble("total_duration");
                    return new StageOperationalStats(total, failed, running, dur);
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to get stage stats: {}", e.getMessage());
        }
        return new StageOperationalStats(0, 0, 0, 0.0);
    }

    public static class StageOperationalStats {
        private final int totalRuns;
        private final int failedRuns;
        private final int runningRuns;
        private final double totalDurationSeconds;

        public StageOperationalStats(int totalRuns, int failedRuns, int runningRuns, double totalDurationSeconds) {
            this.totalRuns = totalRuns;
            this.failedRuns = failedRuns;
            this.runningRuns = runningRuns;
            this.totalDurationSeconds = totalDurationSeconds;
        }

        public int getTotalRuns() { return totalRuns; }
        public int getFailedRuns() { return failedRuns; }
        public int getRunningRuns() { return runningRuns; }
        public double getTotalDurationSeconds() { return totalDurationSeconds; }
    }

    public void delete(String executionId) {
        String sql = "DELETE FROM executions WHERE id = ?";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, executionId);
            ps.executeUpdate();
            logger.info("Deleted execution: {}", executionId);
        } catch (SQLException e) {
            logger.error("Failed to delete execution {}: {}", executionId, e.getMessage());
            throw new DatabaseException("Failed to delete execution", e);
        }
    }

    public int cleanFinishedExecutions() {
        String sql = "DELETE FROM executions WHERE status IN ('SUCCESS', 'FAILED', 'CANCELLED', 'BLOCKED', 'TIMED_OUT')";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            int deleted = ps.executeUpdate();
            logger.info("Cleaned {} finished executions from audit history", deleted);
            return deleted;
        } catch (SQLException e) {
            logger.error("Failed to clean finished executions: {}", e.getMessage());
            throw new DatabaseException("Failed to clean execution history", e);
        }
    }

    public int deleteAll() {
        String sql = "DELETE FROM executions";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            int deleted = ps.executeUpdate();
            logger.info("Deleted all {} executions from audit history", deleted);
            return deleted;
        } catch (SQLException e) {
            logger.error("Failed to delete all executions: {}", e.getMessage());
            throw new DatabaseException("Failed to purge executions", e);
        }
    }

    private Execution mapExecution(ResultSet rs) throws SQLException {
        Execution ex = new Execution();
        ex.setId(rs.getString("id"));
        ex.setPipelineId(rs.getString("pipeline_id"));
        ex.setProjectId(rs.getString("project_id"));
        ex.setExecutionNumber(rs.getInt("execution_number"));
        ex.setStatus(ExecutionStatus.fromString(rs.getString("status")));
        ex.setTriggerType(TriggerType.fromString(rs.getString("trigger_type")));
        ex.setStartedAt(rs.getTimestamp("started_at"));
        ex.setFinishedAt(rs.getTimestamp("finished_at"));
        ex.setDurationSeconds(rs.getDouble("duration_seconds"));
        ex.setCreatedAt(rs.getTimestamp("created_at"));
        ex.setPipelineName(rs.getString("pipeline_name"));
        ex.setProjectName(rs.getString("project_name"));
        try {
            ex.setCommitHash(rs.getString("commit_hash"));
        } catch (SQLException ignored) {}
        try {
            ex.setArtifactPath(rs.getString("artifact_path"));
        } catch (SQLException ignored) {}
        try {
            ex.setProjectType(rs.getString("project_type"));
        } catch (SQLException ignored) {}
        try {
            ex.setProjectRepositoryUrl(rs.getString("project_repository_url"));
        } catch (SQLException ignored) {}
        return ex;
    }
}
