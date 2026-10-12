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
        String sql = "SELECT e.*, pl.name as pipeline_name, pr.name as project_name " +
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
        String sql = "SELECT e.*, pl.name as pipeline_name, pr.name as project_name " +
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
        String sql = "SELECT e.*, pl.name as pipeline_name, pr.name as project_name " +
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
        String sql = "SELECT e.*, pl.name as pipeline_name, pr.name as project_name " +
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
        String sql = "SELECT e.*, pl.name as pipeline_name, pr.name as project_name " +
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
        String sql = "INSERT INTO executions (id, pipeline_id, project_id, execution_number, status, trigger_type, started_at, finished_at, duration_seconds, created_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
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
            ps.setTimestamp(10, execution.getCreatedAt() != null ? execution.getCreatedAt() : new Timestamp(System.currentTimeMillis()));
            ps.executeUpdate();
            logger.info("Saved execution: #{} ({})", execution.getExecutionNumber(), execution.getId());
        } catch (SQLException e) {
            logger.error("Failed to save execution: {}", e.getMessage());
            throw new DatabaseException("Failed to save execution: " + e.getMessage(), e);
        }
    }

    public void update(Execution execution) {
        String sql = "UPDATE executions SET status = ?, started_at = ?, finished_at = ?, duration_seconds = ? WHERE id = ?";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, execution.getStatus().name());
            ps.setTimestamp(2, execution.getStartedAt());
            ps.setTimestamp(3, execution.getFinishedAt());
            ps.setDouble(4, execution.getDurationSeconds());
            ps.setString(5, execution.getId());
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.error("Failed to update execution: {}", e.getMessage());
            throw new DatabaseException("Failed to update execution", e);
        }
    }

    public List<StageExecution> findStageExecutionsByExecutionId(String executionId) {
        String sql = "SELECT se.* FROM stage_executions se LEFT JOIN pipeline_stages ps ON se.stage_id = ps.id WHERE se.execution_id = ? ORDER BY ps.stage_order ASC, se.id ASC";
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
                    se.setStatus(StageExecutionStatus.fromString(rs.getString("status")));
                    se.setExitCode(rs.getInt("exit_code"));
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
        String sql = "INSERT INTO stage_executions (id, execution_id, stage_id, stage_name, stage_type, status, exit_code, started_at, finished_at, duration_seconds, error_message) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE status=VALUES(status), exit_code=VALUES(exit_code), started_at=VALUES(started_at), " +
                "finished_at=VALUES(finished_at), duration_seconds=VALUES(duration_seconds), error_message=VALUES(error_message)";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, se.getId());
            ps.setString(2, se.getExecutionId());
            ps.setString(3, se.getStageId());
            ps.setString(4, se.getStageName());
            ps.setString(5, se.getStageType().name());
            ps.setString(6, se.getStatus().name());
            ps.setInt(7, se.getExitCode());
            ps.setTimestamp(8, se.getStartedAt());
            ps.setTimestamp(9, se.getFinishedAt());
            ps.setDouble(10, se.getDurationSeconds());
            ps.setString(11, se.getErrorMessage());
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.error("Failed to save stage execution: {}", e.getMessage());
        }
    }

    public void updateStageExecution(StageExecution se) {
        saveStageExecution(se);
    }

    public int countByStatus(ExecutionStatus status) {
        String sql = "SELECT COUNT(*) FROM executions WHERE status = ?";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status.name());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException e) {
            logger.error("Failed to count executions by status: {}", e.getMessage());
        }
        return 0;
    }

    public int countAll() {
        String sql = "SELECT COUNT(*) FROM executions";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) {
            logger.error("Failed to count executions: {}", e.getMessage());
        }
        return 0;
    }

    public double getAverageDurationSeconds() {
        String sql = "SELECT AVG(duration_seconds) FROM executions WHERE duration_seconds > 0 AND status IN ('SUCCESS', 'FAILED')";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return rs.getDouble(1);
            }
        } catch (SQLException e) {
            logger.error("Failed to get average duration: {}", e.getMessage());
        }
        return 0.0;
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
        return ex;
    }
}
