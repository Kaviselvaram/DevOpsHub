package com.devopshub.repository;

import com.devopshub.config.DatabaseManager;
import com.devopshub.security.SecurityCategory;
import com.devopshub.security.SecurityFinding;
import com.devopshub.security.SecuritySeverity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Repository for persisting and querying security scan findings.
 */
public class SecurityFindingRepository {
    private static final Logger logger = LoggerFactory.getLogger(SecurityFindingRepository.class);
    private final DatabaseManager dbManager;

    public SecurityFindingRepository() {
        this(DatabaseManager.getInstance());
    }

    public SecurityFindingRepository(DatabaseManager dbManager) {
        this.dbManager = dbManager;
    }

    public void save(SecurityFinding finding) {
        if (finding.getId() == null) {
            finding.setId(UUID.randomUUID().toString());
        }
        String sql = "INSERT INTO security_findings (id, execution_id, stage_execution_id, rule_id, severity, " +
                     "category, title, file_path, line_number, description, remediation, created_at) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, finding.getId());
            stmt.setString(2, finding.getExecutionId());
            stmt.setString(3, finding.getStageExecutionId());
            stmt.setString(4, finding.getRuleId());
            stmt.setString(5, finding.getSeverity() != null ? finding.getSeverity().name() : SecuritySeverity.MEDIUM.name());
            stmt.setString(6, finding.getCategory() != null ? finding.getCategory().name() : SecurityCategory.STATIC_ANALYSIS.name());
            stmt.setString(7, finding.getTitle());
            stmt.setString(8, finding.getFilePath());
            if (finding.getLineNumber() != null) {
                stmt.setInt(9, finding.getLineNumber());
            } else {
                stmt.setNull(9, Types.INTEGER);
            }
            stmt.setString(10, finding.getDescription());
            stmt.setString(11, finding.getRemediation());
            stmt.setTimestamp(12, finding.getCreatedAt() != null ? finding.getCreatedAt() : new Timestamp(System.currentTimeMillis()));

            stmt.executeUpdate();
            logger.debug("Saved security finding {} ({}) for execution {}", finding.getId(), finding.getRuleId(), finding.getExecutionId());
        } catch (SQLException e) {
            logger.error("Failed to save security finding {}: {}", finding.getId(), e.getMessage());
        }
    }

    public void saveAll(List<SecurityFinding> findings) {
        if (findings == null || findings.isEmpty()) return;
        String sql = "INSERT INTO security_findings (id, execution_id, stage_execution_id, rule_id, severity, " +
                     "category, title, file_path, line_number, description, remediation, created_at) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            conn.setAutoCommit(false);
            for (SecurityFinding f : findings) {
                if (f.getId() == null) f.setId(UUID.randomUUID().toString());
                stmt.setString(1, f.getId());
                stmt.setString(2, f.getExecutionId());
                stmt.setString(3, f.getStageExecutionId());
                stmt.setString(4, f.getRuleId());
                stmt.setString(5, f.getSeverity() != null ? f.getSeverity().name() : SecuritySeverity.MEDIUM.name());
                stmt.setString(6, f.getCategory() != null ? f.getCategory().name() : SecurityCategory.STATIC_ANALYSIS.name());
                stmt.setString(7, f.getTitle());
                stmt.setString(8, f.getFilePath());
                if (f.getLineNumber() != null) {
                    stmt.setInt(9, f.getLineNumber());
                } else {
                    stmt.setNull(9, Types.INTEGER);
                }
                stmt.setString(10, f.getDescription());
                stmt.setString(11, f.getRemediation());
                stmt.setTimestamp(12, f.getCreatedAt() != null ? f.getCreatedAt() : new Timestamp(System.currentTimeMillis()));
                stmt.addBatch();
            }
            stmt.executeBatch();
            conn.commit();
            logger.info("Persisted {} security findings in batch", findings.size());
        } catch (SQLException e) {
            logger.error("Failed to batch save security findings: {}", e.getMessage());
        }
    }

    public List<SecurityFinding> findByExecutionId(String executionId) {
        String sql = "SELECT * FROM security_findings WHERE execution_id = ? ORDER BY created_at ASC";
        List<SecurityFinding> list = new ArrayList<>();
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, executionId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    list.add(mapResultSet(rs));
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to query security findings for execution {}: {}", executionId, e.getMessage());
        }
        return list;
    }

    public List<SecurityFinding> findByStageExecutionId(String stageExecutionId) {
        String sql = "SELECT * FROM security_findings WHERE stage_execution_id = ? ORDER BY created_at ASC";
        List<SecurityFinding> list = new ArrayList<>();
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, stageExecutionId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    list.add(mapResultSet(rs));
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to query security findings for stage {}: {}", stageExecutionId, e.getMessage());
        }
        return list;
    }

    public long countByExecutionIdAndSeverity(String executionId, SecuritySeverity severity) {
        String sql = "SELECT COUNT(*) FROM security_findings WHERE execution_id = ? AND severity = ?";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, executionId);
            stmt.setString(2, severity.name());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return rs.getLong(1);
            }
        } catch (SQLException e) {
            logger.error("Failed to count findings by severity: {}", e.getMessage());
        }
        return 0;
    }

    private SecurityFinding mapResultSet(ResultSet rs) throws SQLException {
        SecurityFinding finding = new SecurityFinding();
        finding.setId(rs.getString("id"));
        finding.setExecutionId(rs.getString("execution_id"));
        finding.setStageExecutionId(rs.getString("stage_execution_id"));
        finding.setRuleId(rs.getString("rule_id"));
        finding.setSeverity(SecuritySeverity.valueOf(rs.getString("severity")));
        finding.setCategory(SecurityCategory.valueOf(rs.getString("category")));
        finding.setTitle(rs.getString("title"));
        finding.setFilePath(rs.getString("file_path"));
        int lineNum = rs.getInt("line_number");
        if (!rs.wasNull()) {
            finding.setLineNumber(lineNum);
        }
        finding.setDescription(rs.getString("description"));
        finding.setRemediation(rs.getString("remediation"));
        finding.setCreatedAt(rs.getTimestamp("created_at"));
        return finding;
    }
}
