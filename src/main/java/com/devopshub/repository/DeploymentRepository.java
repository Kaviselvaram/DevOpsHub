package com.devopshub.repository;

import com.devopshub.config.DatabaseManager;
import com.devopshub.deployment.DeploymentEnvironment;
import com.devopshub.deployment.DeploymentRecord;
import com.devopshub.deployment.DeploymentTargetType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for persisting and auditing deployment operations and checksums.
 */
public class DeploymentRepository {
    private static final Logger logger = LoggerFactory.getLogger(DeploymentRepository.class);
    private final DatabaseManager dbManager;

    public DeploymentRepository() {
        this(DatabaseManager.getInstance());
    }

    public DeploymentRepository(DatabaseManager dbManager) {
        this.dbManager = dbManager;
    }

    public void save(DeploymentRecord record) {
        if (record.getId() == null) {
            record.setId(UUID.randomUUID().toString());
        }
        String sql = "INSERT INTO deployment_records (id, execution_id, pipeline_id, target_type, environment, status, " +
                     "source_artifact, target_destination, sha256_checksum, duration_seconds, deployed_at, details) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, record.getId());
            stmt.setString(2, record.getExecutionId());
            stmt.setString(3, record.getPipelineId());
            stmt.setString(4, record.getTargetType() != null ? record.getTargetType().name() : DeploymentTargetType.LOCAL_STAGING.name());
            stmt.setString(5, record.getEnvironment() != null ? record.getEnvironment().name() : DeploymentEnvironment.TEST.name());
            stmt.setString(6, record.getStatus() != null ? record.getStatus() : "SUCCESS");
            stmt.setString(7, record.getSourceArtifact());
            stmt.setString(8, record.getTargetDestination());
            stmt.setString(9, record.getSha256Checksum());
            stmt.setDouble(10, record.getDurationSeconds());
            stmt.setTimestamp(11, record.getDeployedAt() != null ? record.getDeployedAt() : new Timestamp(System.currentTimeMillis()));
            stmt.setString(12, record.getDetails());
            stmt.executeUpdate();
            logger.info("Saved deployment record {} for execution {}", record.getId(), record.getExecutionId());
        } catch (SQLException e) {
            logger.error("Failed to save deployment record: {}", e.getMessage());
        }
    }

    public Optional<DeploymentRecord> findById(String id) {
        String sql = "SELECT * FROM deployment_records WHERE id = ?";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSet(rs));
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to find deployment record {}: {}", id, e.getMessage());
        }
        return Optional.empty();
    }

    public List<DeploymentRecord> findByExecutionId(String executionId) {
        List<DeploymentRecord> list = new ArrayList<>();
        String sql = "SELECT * FROM deployment_records WHERE execution_id = ? ORDER BY deployed_at DESC";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, executionId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    list.add(mapResultSet(rs));
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to find deployment records for execution {}: {}", executionId, e.getMessage());
        }
        return list;
    }

    public List<DeploymentRecord> findAll() {
        List<DeploymentRecord> list = new ArrayList<>();
        String sql = "SELECT * FROM deployment_records ORDER BY deployed_at DESC LIMIT 100";
        try (Connection conn = dbManager.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                list.add(mapResultSet(rs));
            }
        } catch (SQLException e) {
            logger.error("Failed to query deployment records: {}", e.getMessage());
        }
        return list;
    }

    private DeploymentRecord mapResultSet(ResultSet rs) throws SQLException {
        DeploymentRecord r = new DeploymentRecord();
        r.setId(rs.getString("id"));
        r.setExecutionId(rs.getString("execution_id"));
        r.setPipelineId(rs.getString("pipeline_id"));
        try {
            r.setTargetType(DeploymentTargetType.valueOf(rs.getString("target_type")));
        } catch (Exception e) {
            r.setTargetType(DeploymentTargetType.LOCAL_STAGING);
        }
        try {
            r.setEnvironment(DeploymentEnvironment.valueOf(rs.getString("environment")));
        } catch (Exception e) {
            r.setEnvironment(DeploymentEnvironment.TEST);
        }
        r.setStatus(rs.getString("status"));
        r.setSourceArtifact(rs.getString("source_artifact"));
        r.setTargetDestination(rs.getString("target_destination"));
        r.setSha256Checksum(rs.getString("sha256_checksum"));
        r.setDurationSeconds(rs.getDouble("duration_seconds"));
        r.setDeployedAt(rs.getTimestamp("deployed_at"));
        r.setDetails(rs.getString("details"));
        return r;
    }
}
