package com.devopshub.repository;

import com.devopshub.config.DatabaseManager;
import com.devopshub.execution.event.ExecutionEvent;
import com.devopshub.execution.event.ExecutionEventType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Persistence repository for fine-grained execution lifecycle events.
 */
public class ExecutionEventRepository {
    private static final Logger logger = LoggerFactory.getLogger(ExecutionEventRepository.class);
    private final DatabaseManager dbManager;
    private static volatile boolean tableEnsured = false;

    public ExecutionEventRepository() {
        this(DatabaseManager.getInstance());
    }

    public ExecutionEventRepository(DatabaseManager dbManager) {
        this.dbManager = dbManager;
        ensureTable();
    }

    private Connection getConnection() throws SQLException {
        if (dbManager != null) {
            try {
                return dbManager.getConnection();
            } catch (Exception ignored) {}
        }
        return DatabaseManager.getInstance().getConnection();
    }

    private synchronized void ensureTable() {
        if (tableEnsured) return;
        String sql = "CREATE TABLE IF NOT EXISTS execution_events (" +
                "id VARCHAR(100) PRIMARY KEY, " +
                "execution_id VARCHAR(100) NOT NULL, " +
                "sequence_number BIGINT NOT NULL, " +
                "pipeline_id VARCHAR(100), " +
                "stage_execution_id VARCHAR(100), " +
                "stage_name VARCHAR(100), " +
                "stage_order INT DEFAULT 0, " +
                "event_type VARCHAR(50) NOT NULL, " +
                "status_transition VARCHAR(100), " +
                "message TEXT NOT NULL, " +
                "duration_seconds DOUBLE DEFAULT 0.0, " +
                "exit_code INT, " +
                "is_authoritative BOOLEAN DEFAULT TRUE, " +
                "timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP)";
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
            tableEnsured = true;
        } catch (SQLException e) {
            logger.warn("Could not ensure execution_events table: {}", e.getMessage());
        }
    }

    public void save(ExecutionEvent event) {
        if (event == null) return;
        ensureTable();
        String sql = "INSERT INTO execution_events (id, execution_id, sequence_number, pipeline_id, " +
                "stage_execution_id, stage_name, stage_order, event_type, status_transition, message, " +
                "duration_seconds, exit_code, is_authoritative, timestamp) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, event.getId());
            ps.setString(2, event.getExecutionId());
            ps.setLong(3, event.getSequenceNumber());
            ps.setString(4, event.getPipelineId());
            ps.setString(5, event.getStageExecutionId());
            ps.setString(6, event.getStageName());
            ps.setInt(7, event.getStageOrder());
            ps.setString(8, event.getEventType().name());
            ps.setString(9, event.getStatusTransition());
            ps.setString(10, event.getMessage());
            if (event.getDurationSeconds() != null) {
                ps.setDouble(11, event.getDurationSeconds());
            } else {
                ps.setNull(11, Types.DOUBLE);
            }
            if (event.getExitCode() != null) {
                ps.setInt(12, event.getExitCode());
            } else {
                ps.setNull(12, Types.INTEGER);
            }
            ps.setBoolean(13, event.isAuthoritative());
            ps.setTimestamp(14, event.getTimestamp() != null ? event.getTimestamp() : new Timestamp(System.currentTimeMillis()));
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.error("Failed to save execution event #{}: {}", event.getSequenceNumber(), e.getMessage());
        }
    }

    public List<ExecutionEvent> findByExecutionId(String executionId) {
        ensureTable();
        String sql = "SELECT * FROM execution_events WHERE execution_id = ? ORDER BY sequence_number ASC";
        List<ExecutionEvent> list = new ArrayList<>();
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, executionId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ExecutionEvent ev = new ExecutionEvent();
                    ev.setId(rs.getString("id"));
                    ev.setExecutionId(rs.getString("execution_id"));
                    ev.setSequenceNumber(rs.getLong("sequence_number"));
                    ev.setPipelineId(rs.getString("pipeline_id"));
                    ev.setStageExecutionId(rs.getString("stage_execution_id"));
                    ev.setStageName(rs.getString("stage_name"));
                    ev.setStageOrder(rs.getInt("stage_order"));
                    ev.setEventType(ExecutionEventType.valueOf(rs.getString("event_type")));
                    ev.setStatusTransition(rs.getString("status_transition"));
                    ev.setMessage(rs.getString("message"));
                    double dur = rs.getDouble("duration_seconds");
                    ev.setDurationSeconds(rs.wasNull() ? null : dur);
                    int exit = rs.getInt("exit_code");
                    ev.setExitCode(rs.wasNull() ? null : exit);
                    ev.setAuthoritative(rs.getBoolean("is_authoritative"));
                    ev.setTimestamp(rs.getTimestamp("timestamp"));
                    list.add(ev);
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to query execution events for {}: {}", executionId, e.getMessage());
        }
        return list;
    }
}
