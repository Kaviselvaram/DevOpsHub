package com.devopshub.repository;

import com.devopshub.config.DatabaseManager;
import com.devopshub.exception.DatabaseException;
import com.devopshub.model.Pipeline;
import com.devopshub.model.Stage;
import com.devopshub.model.StageType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.*;

public class PipelineRepository {
    private static final Logger logger = LoggerFactory.getLogger(PipelineRepository.class);
    private final DatabaseManager dbManager;

    public PipelineRepository() {
        this.dbManager = DatabaseManager.getInstance();
    }

    public PipelineRepository(DatabaseManager dbManager) {
        this.dbManager = dbManager;
    }

    public List<Pipeline> findAll() {
        String sql = "SELECT pl.*, pr.name as project_name, " +
                "(SELECT COUNT(*) FROM executions e WHERE e.pipeline_id = pl.id) as execution_count, " +
                "(SELECT e.status FROM executions e WHERE e.pipeline_id = pl.id ORDER BY e.created_at DESC LIMIT 1) as latest_status " +
                "FROM pipelines pl " +
                "JOIN projects pr ON pl.project_id = pr.id " +
                "ORDER BY pl.created_at DESC";
        List<Pipeline> list = new ArrayList<>();
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Pipeline p = mapPipeline(rs);
                list.add(p);
            }
        } catch (SQLException e) {
            logger.error("Failed to query pipelines: {}", e.getMessage());
            throw new DatabaseException("Failed to query pipelines", e);
        }

        // Populate stages for each pipeline
        for (Pipeline p : list) {
            p.setStages(findStagesByPipelineId(p.getId()));
        }

        return list;
    }

    public List<Pipeline> findByProjectId(String projectId) {
        String sql = "SELECT pl.*, pr.name as project_name, " +
                "(SELECT COUNT(*) FROM executions e WHERE e.pipeline_id = pl.id) as execution_count, " +
                "(SELECT e.status FROM executions e WHERE e.pipeline_id = pl.id ORDER BY e.created_at DESC LIMIT 1) as latest_status " +
                "FROM pipelines pl " +
                "JOIN projects pr ON pl.project_id = pr.id " +
                "WHERE pl.project_id = ? " +
                "ORDER BY pl.created_at DESC";
        List<Pipeline> list = new ArrayList<>();
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, projectId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapPipeline(rs));
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to query pipelines by project: {}", e.getMessage());
            throw new DatabaseException("Failed to query pipelines by project", e);
        }

        for (Pipeline p : list) {
            p.setStages(findStagesByPipelineId(p.getId()));
        }

        return list;
    }

    public Optional<Pipeline> findById(String id) {
        String sql = "SELECT pl.*, pr.name as project_name, " +
                "(SELECT COUNT(*) FROM executions e WHERE e.pipeline_id = pl.id) as execution_count, " +
                "(SELECT e.status FROM executions e WHERE e.pipeline_id = pl.id ORDER BY e.created_at DESC LIMIT 1) as latest_status " +
                "FROM pipelines pl " +
                "JOIN projects pr ON pl.project_id = pr.id " +
                "WHERE pl.id = ?";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Pipeline p = mapPipeline(rs);
                    p.setStages(findStagesByPipelineId(p.getId()));
                    return Optional.of(p);
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to query pipeline by id: {}", e.getMessage());
            throw new DatabaseException("Failed to query pipeline by id", e);
        }
        return Optional.empty();
    }

    public void save(Pipeline pipeline) {
        String sql = "INSERT INTO pipelines (id, project_id, name, description, execution_mode, is_active, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, pipeline.getId());
            ps.setString(2, pipeline.getProjectId());
            ps.setString(3, pipeline.getName());
            ps.setString(4, pipeline.getDescription());
            ps.setString(5, pipeline.getExecutionMode());
            ps.setBoolean(6, pipeline.isActive());
            ps.setTimestamp(7, pipeline.getCreatedAt() != null ? pipeline.getCreatedAt() : new Timestamp(System.currentTimeMillis()));
            ps.setTimestamp(8, new Timestamp(System.currentTimeMillis()));
            ps.executeUpdate();
            logger.info("Saved pipeline: {} ({})", pipeline.getName(), pipeline.getId());

            // Save stages if any
            if (pipeline.getStages() != null) {
                for (Stage stage : pipeline.getStages()) {
                    stage.setPipelineId(pipeline.getId());
                    saveStage(stage);
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to save pipeline: {}", e.getMessage());
            throw new DatabaseException("Failed to save pipeline: " + e.getMessage(), e);
        }
    }

    public void update(Pipeline pipeline) {
        String sql = "UPDATE pipelines SET name = ?, description = ?, execution_mode = ?, is_active = ?, updated_at = ? WHERE id = ?";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, pipeline.getName());
            ps.setString(2, pipeline.getDescription());
            ps.setString(3, pipeline.getExecutionMode());
            ps.setBoolean(4, pipeline.isActive());
            ps.setTimestamp(5, new Timestamp(System.currentTimeMillis()));
            ps.setString(6, pipeline.getId());
            ps.executeUpdate();
            logger.info("Updated pipeline: {}", pipeline.getId());
        } catch (SQLException e) {
            logger.error("Failed to update pipeline: {}", e.getMessage());
            throw new DatabaseException("Failed to update pipeline", e);
        }
    }

    public void delete(String id) {
        String sql = "DELETE FROM pipelines WHERE id = ?";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            ps.executeUpdate();
            logger.info("Deleted pipeline: {}", id);
        } catch (SQLException e) {
            logger.error("Failed to delete pipeline: {}", e.getMessage());
            throw new DatabaseException("Failed to delete pipeline", e);
        }
    }

    public int count() {
        String sql = "SELECT COUNT(*) FROM pipelines";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            logger.error("Failed to count pipelines: {}", e.getMessage());
            throw new DatabaseException("Failed to count pipelines", e);
        }
        return 0;
    }

    public List<Stage> findStagesByPipelineId(String pipelineId) {
        String sql = "SELECT * FROM pipeline_stages WHERE pipeline_id = ? ORDER BY stage_order ASC";
        List<Stage> stages = new ArrayList<>();
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, pipelineId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Stage stage = new Stage();
                    stage.setId(rs.getString("id"));
                    stage.setPipelineId(rs.getString("pipeline_id"));
                    stage.setName(rs.getString("name"));
                    stage.setStageType(StageType.fromString(rs.getString("stage_type")));
                    stage.setStageOrder(rs.getInt("stage_order"));
                    stage.setCommand(rs.getString("command"));
                    stage.setTimeoutSeconds(rs.getInt("timeout_seconds"));
                    stage.setEnabled(rs.getBoolean("is_enabled"));
                    stage.setParallelGroup(rs.getString("parallel_group"));
                    stage.setCreatedAt(rs.getTimestamp("created_at"));
                    stages.add(stage);
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to query stages: {}", e.getMessage());
            throw new DatabaseException("Failed to query stages", e);
        }

        // Attach dependencies
        for (Stage s : stages) {
            s.setDependsOnStageIds(findStageDependencies(s.getId()));
        }

        return stages;
    }

    public void saveStage(Stage stage) {
        String sql = "INSERT INTO pipeline_stages (id, pipeline_id, name, stage_type, stage_order, command, timeout_seconds, is_enabled, parallel_group, created_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE name=VALUES(name), stage_type=VALUES(stage_type), stage_order=VALUES(stage_order), " +
                "command=VALUES(command), timeout_seconds=VALUES(timeout_seconds), is_enabled=VALUES(is_enabled), parallel_group=VALUES(parallel_group)";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, stage.getId());
            ps.setString(2, stage.getPipelineId());
            ps.setString(3, stage.getName());
            ps.setString(4, stage.getStageType().name());
            ps.setInt(5, stage.getStageOrder());
            ps.setString(6, stage.getCommand());
            ps.setInt(7, stage.getTimeoutSeconds());
            ps.setBoolean(8, stage.isEnabled());
            ps.setString(9, stage.getParallelGroup());
            ps.setTimestamp(10, stage.getCreatedAt() != null ? stage.getCreatedAt() : new Timestamp(System.currentTimeMillis()));
            ps.executeUpdate();

            // Save dependencies
            deleteStageDependencies(stage.getId());
            if (stage.getDependsOnStageIds() != null) {
                for (String depId : stage.getDependsOnStageIds()) {
                    saveStageDependency(stage.getId(), depId);
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to save stage: {}", e.getMessage());
            throw new DatabaseException("Failed to save stage", e);
        }
    }

    public void deleteStage(String stageId) {
        String sql = "DELETE FROM pipeline_stages WHERE id = ?";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, stageId);
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.error("Failed to delete stage: {}", e.getMessage());
            throw new DatabaseException("Failed to delete stage", e);
        }
    }

    public List<String> findStageDependencies(String stageId) {
        String sql = "SELECT depends_on_stage_id FROM stage_dependencies WHERE stage_id = ?";
        List<String> deps = new ArrayList<>();
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, stageId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    deps.add(rs.getString("depends_on_stage_id"));
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to query stage dependencies: {}", e.getMessage());
        }
        return deps;
    }

    public void saveStageDependency(String stageId, String dependsOnStageId) {
        String sql = "INSERT INTO stage_dependencies (id, stage_id, depends_on_stage_id) VALUES (?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE stage_id=VALUES(stage_id)";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, UUID.randomUUID().toString());
            ps.setString(2, stageId);
            ps.setString(3, dependsOnStageId);
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.error("Failed to save stage dependency: {}", e.getMessage());
        }
    }

    public void deleteStageDependencies(String stageId) {
        String sql = "DELETE FROM stage_dependencies WHERE stage_id = ?";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, stageId);
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.error("Failed to delete stage dependencies: {}", e.getMessage());
        }
    }

    private Pipeline mapPipeline(ResultSet rs) throws SQLException {
        Pipeline p = new Pipeline();
        p.setId(rs.getString("id"));
        p.setProjectId(rs.getString("project_id"));
        p.setName(rs.getString("name"));
        p.setDescription(rs.getString("description"));
        p.setExecutionMode(rs.getString("execution_mode"));
        p.setActive(rs.getBoolean("is_active"));
        p.setCreatedAt(rs.getTimestamp("created_at"));
        p.setUpdatedAt(rs.getTimestamp("updated_at"));
        p.setProjectName(rs.getString("project_name"));
        p.setExecutionCount(rs.getInt("execution_count"));
        p.setLastExecutionStatus(rs.getString("latest_status"));
        return p;
    }
}
