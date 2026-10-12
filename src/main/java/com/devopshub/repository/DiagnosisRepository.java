package com.devopshub.repository;

import com.devopshub.config.DatabaseManager;
import com.devopshub.exception.DatabaseException;
import com.devopshub.model.FailureCategory;
import com.devopshub.model.FailureDiagnosis;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.*;

public class DiagnosisRepository {
    private static final Logger logger = LoggerFactory.getLogger(DiagnosisRepository.class);
    private final DatabaseManager dbManager;

    public DiagnosisRepository() {
        this.dbManager = DatabaseManager.getInstance();
    }

    public DiagnosisRepository(DatabaseManager dbManager) {
        this.dbManager = dbManager;
    }

    public void save(FailureDiagnosis d) {
        String sql = "INSERT INTO failure_diagnoses (id, execution_id, stage_execution_id, failed_stage, category, title, likely_cause, evidence_excerpt, suggested_action, rule_id, rule_version, analyzed_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE title=VALUES(title), likely_cause=VALUES(likely_cause), evidence_excerpt=VALUES(evidence_excerpt), suggested_action=VALUES(suggested_action)";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, d.getId());
            ps.setString(2, d.getExecutionId());
            ps.setString(3, d.getStageExecutionId());
            ps.setString(4, d.getFailedStage());
            ps.setString(5, d.getCategory().name());
            ps.setString(6, d.getTitle());
            ps.setString(7, d.getLikelyCause());
            ps.setString(8, d.getEvidenceExcerpt());
            ps.setString(9, d.getSuggestedAction());
            ps.setString(10, d.getRuleId());
            ps.setString(11, d.getRuleVersion());
            ps.setTimestamp(12, d.getAnalyzedAt() != null ? d.getAnalyzedAt() : new Timestamp(System.currentTimeMillis()));
            ps.executeUpdate();
            logger.info("Saved failure diagnosis for execution: {}", d.getExecutionId());
        } catch (SQLException e) {
            logger.error("Failed to save diagnosis: {}", e.getMessage());
            throw new DatabaseException("Failed to save diagnosis", e);
        }
    }

    public Optional<FailureDiagnosis> findByExecutionId(String executionId) {
        String sql = "SELECT * FROM failure_diagnoses WHERE execution_id = ? ORDER BY analyzed_at DESC LIMIT 1";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, executionId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapDiagnosis(rs));
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to query diagnosis by execution id: {}", e.getMessage());
        }
        return Optional.empty();
    }

    public List<FailureDiagnosis> findAll() {
        String sql = "SELECT * FROM failure_diagnoses ORDER BY analyzed_at DESC";
        List<FailureDiagnosis> list = new ArrayList<>();
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapDiagnosis(rs));
            }
        } catch (SQLException e) {
            logger.error("Failed to query diagnoses: {}", e.getMessage());
        }
        return list;
    }

    public Map<String, Integer> countByCategory() {
        String sql = "SELECT category, COUNT(*) as cnt FROM failure_diagnoses GROUP BY category";
        Map<String, Integer> map = new HashMap<>();
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                map.put(rs.getString("category"), rs.getInt("cnt"));
            }
        } catch (SQLException e) {
            logger.error("Failed to count diagnoses by category: {}", e.getMessage());
        }
        return map;
    }

    private FailureDiagnosis mapDiagnosis(ResultSet rs) throws SQLException {
        FailureDiagnosis d = new FailureDiagnosis();
        d.setId(rs.getString("id"));
        d.setExecutionId(rs.getString("execution_id"));
        d.setStageExecutionId(rs.getString("stage_execution_id"));
        d.setFailedStage(rs.getString("failed_stage"));
        d.setCategory(FailureCategory.fromString(rs.getString("category")));
        d.setTitle(rs.getString("title"));
        d.setLikelyCause(rs.getString("likely_cause"));
        d.setEvidenceExcerpt(rs.getString("evidence_excerpt"));
        d.setSuggestedAction(rs.getString("suggested_action"));
        d.setRuleId(rs.getString("rule_id"));
        d.setRuleVersion(rs.getString("rule_version"));
        d.setAnalyzedAt(rs.getTimestamp("analyzed_at"));
        return d;
    }
}
