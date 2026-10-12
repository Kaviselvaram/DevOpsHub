package com.devopshub.repository;

import com.devopshub.config.DatabaseManager;
import com.devopshub.exception.DatabaseException;
import com.devopshub.model.ExecutionLog;
import com.devopshub.model.LogLevel;
import com.devopshub.model.LogStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class LogRepository {
    private static final Logger logger = LoggerFactory.getLogger(LogRepository.class);
    private final DatabaseManager dbManager;

    public LogRepository() {
        this.dbManager = DatabaseManager.getInstance();
    }

    public LogRepository(DatabaseManager dbManager) {
        this.dbManager = dbManager;
    }

    public void save(ExecutionLog log) {
        String sql = "INSERT INTO execution_logs (execution_id, stage_execution_id, stage_name, log_level, stream, message, timestamp) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, log.getExecutionId());
            ps.setString(2, log.getStageExecutionId());
            ps.setString(3, log.getStageName());
            ps.setString(4, log.getLogLevel().name());
            ps.setString(5, log.getStream().name());
            ps.setString(6, log.getMessage());
            ps.setTimestamp(7, log.getTimestamp() != null ? log.getTimestamp() : new Timestamp(System.currentTimeMillis()));
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    log.setId(rs.getLong(1));
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to save execution log: {}", e.getMessage());
        }
    }

    public void saveBatch(List<ExecutionLog> logs) {
        if (logs == null || logs.isEmpty()) return;
        String sql = "INSERT INTO execution_logs (execution_id, stage_execution_id, stage_name, log_level, stream, message, timestamp) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            conn.setAutoCommit(false);
            for (ExecutionLog log : logs) {
                ps.setString(1, log.getExecutionId());
                ps.setString(2, log.getStageExecutionId());
                ps.setString(3, log.getStageName());
                ps.setString(4, log.getLogLevel().name());
                ps.setString(5, log.getStream().name());
                ps.setString(6, log.getMessage());
                ps.setTimestamp(7, log.getTimestamp() != null ? log.getTimestamp() : new Timestamp(System.currentTimeMillis()));
                ps.addBatch();
            }
            ps.executeBatch();
            conn.commit();
        } catch (SQLException e) {
            logger.error("Failed to batch save logs: {}", e.getMessage());
        }
    }

    public List<ExecutionLog> findByExecutionId(String executionId) {
        String sql = "SELECT * FROM execution_logs WHERE execution_id = ? ORDER BY id ASC LIMIT 5000";
        List<ExecutionLog> list = new ArrayList<>();
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, executionId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapLog(rs));
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to query logs: {}", e.getMessage());
            throw new DatabaseException("Failed to query logs", e);
        }
        return list;
    }

    public List<ExecutionLog> findByExecutionIdAndStage(String executionId, String stageName) {
        String sql = "SELECT * FROM execution_logs WHERE execution_id = ? AND stage_name = ? ORDER BY id ASC LIMIT 2000";
        List<ExecutionLog> list = new ArrayList<>();
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, executionId);
            ps.setString(2, stageName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapLog(rs));
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to query logs by stage: {}", e.getMessage());
        }
        return list;
    }

    public List<ExecutionLog> searchLogs(String executionId, String query) {
        String sql = "SELECT * FROM execution_logs WHERE execution_id = ? AND LOWER(message) LIKE ? ORDER BY id ASC LIMIT 500";
        List<ExecutionLog> list = new ArrayList<>();
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, executionId);
            ps.setString(2, "%" + query.toLowerCase() + "%");
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapLog(rs));
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to search logs: {}", e.getMessage());
        }
        return list;
    }

    private ExecutionLog mapLog(ResultSet rs) throws SQLException {
        ExecutionLog log = new ExecutionLog();
        log.setId(rs.getLong("id"));
        log.setExecutionId(rs.getString("execution_id"));
        log.setStageExecutionId(rs.getString("stage_execution_id"));
        log.setStageName(rs.getString("stage_name"));
        log.setLogLevel(LogLevel.fromString(rs.getString("log_level")));
        log.setStream(LogStream.fromString(rs.getString("stream")));
        log.setMessage(rs.getString("message"));
        log.setTimestamp(rs.getTimestamp("timestamp"));
        return log;
    }
}
