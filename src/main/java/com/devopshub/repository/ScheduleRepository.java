package com.devopshub.repository;

import com.devopshub.config.DatabaseManager;
import com.devopshub.scheduler.PipelineSchedulerService.ScheduledJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for durable persistence of pipeline schedules in MySQL / H2 databases.
 */
public class ScheduleRepository {
    private static final Logger logger = LoggerFactory.getLogger(ScheduleRepository.class);
    private final DatabaseManager dbManager;

    public ScheduleRepository() {
        this(DatabaseManager.getInstance());
    }

    public ScheduleRepository(DatabaseManager dbManager) {
        this.dbManager = dbManager;
    }

    public void save(ScheduledJob job) {
        String sql = "INSERT INTO pipeline_schedules (id, pipeline_id, interval_seconds, is_enabled, execution_count, last_execution_id, last_run_at, next_run_at, created_at, updated_at) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP) " +
                     "ON DUPLICATE KEY UPDATE interval_seconds = VALUES(interval_seconds), is_enabled = VALUES(is_enabled), " +
                     "execution_count = VALUES(execution_count), last_execution_id = VALUES(last_execution_id), " +
                     "last_run_at = VALUES(last_run_at), next_run_at = VALUES(next_run_at), updated_at = CURRENT_TIMESTAMP";

        // For H2 mode compatibility if ON DUPLICATE KEY is not supported in same manner:
        if (dbManager.isH2Fallback()) {
            delete(job.getPipelineId());
            sql = "INSERT INTO pipeline_schedules (id, pipeline_id, interval_seconds, is_enabled, execution_count, last_execution_id, last_run_at, next_run_at, created_at, updated_at) " +
                  "VALUES (?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)";
        }

        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, UUID.randomUUID().toString());
            stmt.setString(2, job.getPipelineId());
            stmt.setLong(3, job.getIntervalSeconds());
            stmt.setBoolean(4, job.isEnabled());
            stmt.setInt(5, job.getExecutionCount());
            stmt.setString(6, job.getLastExecutionId());
            stmt.setTimestamp(7, job.getLastRunAt());
            stmt.setTimestamp(8, job.getNextRunAt());
            stmt.executeUpdate();
            logger.info("Saved schedule for pipeline {} (interval: {}s)", job.getPipelineId(), job.getIntervalSeconds());
        } catch (SQLException e) {
            logger.error("Failed to persist pipeline schedule for {}: {}", job.getPipelineId(), e.getMessage());
        }
    }

    public void update(ScheduledJob job) {
        String sql = "UPDATE pipeline_schedules SET interval_seconds = ?, is_enabled = ?, execution_count = ?, " +
                     "last_execution_id = ?, last_run_at = ?, next_run_at = ?, updated_at = CURRENT_TIMESTAMP " +
                     "WHERE pipeline_id = ?";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, job.getIntervalSeconds());
            stmt.setBoolean(2, job.isEnabled());
            stmt.setInt(3, job.getExecutionCount());
            stmt.setString(4, job.getLastExecutionId());
            stmt.setTimestamp(5, job.getLastRunAt());
            stmt.setTimestamp(6, job.getNextRunAt());
            stmt.setString(7, job.getPipelineId());
            stmt.executeUpdate();
        } catch (SQLException e) {
            logger.error("Failed to update pipeline schedule for {}: {}", job.getPipelineId(), e.getMessage());
        }
    }

    public void delete(String pipelineId) {
        String sql = "DELETE FROM pipeline_schedules WHERE pipeline_id = ?";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, pipelineId);
            stmt.executeUpdate();
            logger.info("Deleted schedule for pipeline {}", pipelineId);
        } catch (SQLException e) {
            logger.error("Failed to delete pipeline schedule for {}: {}", pipelineId, e.getMessage());
        }
    }

    public Optional<ScheduledJob> findByPipelineId(String pipelineId) {
        String sql = "SELECT s.*, p.name as pipeline_name FROM pipeline_schedules s " +
                     "LEFT JOIN pipelines p ON s.pipeline_id = p.id WHERE s.pipeline_id = ?";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, pipelineId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSetToJob(rs));
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to find schedule for pipeline {}: {}", pipelineId, e.getMessage());
        }
        return Optional.empty();
    }

    public List<ScheduledJob> findAll() {
        List<ScheduledJob> list = new ArrayList<>();
        String sql = "SELECT s.*, p.name as pipeline_name FROM pipeline_schedules s " +
                     "LEFT JOIN pipelines p ON s.pipeline_id = p.id";
        try (Connection conn = dbManager.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                list.add(mapResultSetToJob(rs));
            }
        } catch (SQLException e) {
            logger.error("Failed to query all schedules: {}", e.getMessage());
        }
        return list;
    }

    private ScheduledJob mapResultSetToJob(ResultSet rs) throws SQLException {
        String pipeId = rs.getString("pipeline_id");
        String pipeName = rs.getString("pipeline_name");
        if (pipeName == null) pipeName = "Pipeline-" + pipeId;
        long interval = rs.getLong("interval_seconds");
        boolean enabled = rs.getBoolean("is_enabled");

        ScheduledJob job = new ScheduledJob(pipeId, pipeName, interval, enabled);
        job.setExecutionCount(rs.getInt("execution_count"));
        job.setLastExecutionId(rs.getString("last_execution_id"));
        job.setLastRunAt(rs.getTimestamp("last_run_at"));
        job.setNextRunAt(rs.getTimestamp("next_run_at"));
        return job;
    }
}
