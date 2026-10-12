package com.devopshub.repository;

import com.devopshub.config.DatabaseManager;
import com.devopshub.exception.DatabaseException;
import com.devopshub.model.Project;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ProjectRepository {
    private static final Logger logger = LoggerFactory.getLogger(ProjectRepository.class);
    private final DatabaseManager dbManager;

    public ProjectRepository() {
        this.dbManager = DatabaseManager.getInstance();
    }

    public ProjectRepository(DatabaseManager dbManager) {
        this.dbManager = dbManager;
    }

    public List<Project> findAll() {
        String sql = "SELECT p.*, " +
                "(SELECT COUNT(*) FROM pipelines pl WHERE pl.project_id = p.id) as pipeline_count, " +
                "(SELECT e.status FROM executions e WHERE e.project_id = p.id ORDER BY e.created_at DESC LIMIT 1) as latest_status " +
                "FROM projects p ORDER BY p.created_at DESC";
        List<Project> list = new ArrayList<>();
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapResultSetWithAggregates(rs));
            }
        } catch (SQLException e) {
            logger.error("Failed to query projects: {}", e.getMessage());
            throw new DatabaseException("Failed to query projects", e);
        }
        return list;
    }

    public Optional<Project> findById(String id) {
        String sql = "SELECT p.*, " +
                "(SELECT COUNT(*) FROM pipelines pl WHERE pl.project_id = p.id) as pipeline_count, " +
                "(SELECT e.status FROM executions e WHERE e.project_id = p.id ORDER BY e.created_at DESC LIMIT 1) as latest_status " +
                "FROM projects p WHERE p.id = ?";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSetWithAggregates(rs));
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to query project by id: {}", e.getMessage());
            throw new DatabaseException("Failed to query project by id", e);
        }
        return Optional.empty();
    }

    public List<Project> search(String query) {
        String sql = "SELECT p.*, " +
                "(SELECT COUNT(*) FROM pipelines pl WHERE pl.project_id = p.id) as pipeline_count, " +
                "(SELECT e.status FROM executions e WHERE e.project_id = p.id ORDER BY e.created_at DESC LIMIT 1) as latest_status " +
                "FROM projects p WHERE LOWER(p.name) LIKE ? OR LOWER(p.description) LIKE ? OR LOWER(p.project_type) LIKE ? " +
                "ORDER BY p.created_at DESC";
        List<Project> list = new ArrayList<>();
        String likePattern = "%" + query.toLowerCase().trim() + "%";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, likePattern);
            ps.setString(2, likePattern);
            ps.setString(3, likePattern);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapResultSetWithAggregates(rs));
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to search projects: {}", e.getMessage());
            throw new DatabaseException("Failed to search projects", e);
        }
        return list;
    }

    public void save(Project project) {
        String sql = "INSERT INTO projects (id, name, description, repository_url, default_branch, project_type, status, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, project.getId());
            ps.setString(2, project.getName());
            ps.setString(3, project.getDescription());
            ps.setString(4, project.getRepositoryUrl());
            ps.setString(5, project.getDefaultBranch());
            ps.setString(6, project.getProjectType());
            ps.setString(7, project.getStatus());
            ps.setTimestamp(8, project.getCreatedAt() != null ? project.getCreatedAt() : new Timestamp(System.currentTimeMillis()));
            ps.setTimestamp(9, new Timestamp(System.currentTimeMillis()));
            ps.executeUpdate();
            logger.info("Saved project: {} ({})", project.getName(), project.getId());
        } catch (SQLException e) {
            logger.error("Failed to save project: {}", e.getMessage());
            throw new DatabaseException("Failed to save project: " + e.getMessage(), e);
        }
    }

    public void update(Project project) {
        String sql = "UPDATE projects SET name = ?, description = ?, repository_url = ?, default_branch = ?, project_type = ?, status = ?, updated_at = ? WHERE id = ?";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, project.getName());
            ps.setString(2, project.getDescription());
            ps.setString(3, project.getRepositoryUrl());
            ps.setString(4, project.getDefaultBranch());
            ps.setString(5, project.getProjectType());
            ps.setString(6, project.getStatus());
            ps.setTimestamp(7, new Timestamp(System.currentTimeMillis()));
            ps.setString(8, project.getId());
            ps.executeUpdate();
            logger.info("Updated project: {} ({})", project.getName(), project.getId());
        } catch (SQLException e) {
            logger.error("Failed to update project: {}", e.getMessage());
            throw new DatabaseException("Failed to update project", e);
        }
    }

    public void delete(String id) {
        String sql = "DELETE FROM projects WHERE id = ?";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            ps.executeUpdate();
            logger.info("Deleted project: {}", id);
        } catch (SQLException e) {
            logger.error("Failed to delete project: {}", e.getMessage());
            throw new DatabaseException("Failed to delete project", e);
        }
    }

    public int count() {
        String sql = "SELECT COUNT(*) FROM projects";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            logger.error("Failed to count projects: {}", e.getMessage());
            throw new DatabaseException("Failed to count projects", e);
        }
        return 0;
    }

    private Project mapResultSetWithAggregates(ResultSet rs) throws SQLException {
        Project p = new Project();
        p.setId(rs.getString("id"));
        p.setName(rs.getString("name"));
        p.setDescription(rs.getString("description"));
        p.setRepositoryUrl(rs.getString("repository_url"));
        p.setDefaultBranch(rs.getString("default_branch"));
        p.setProjectType(rs.getString("project_type"));
        p.setStatus(rs.getString("status"));
        p.setCreatedAt(rs.getTimestamp("created_at"));
        p.setUpdatedAt(rs.getTimestamp("updated_at"));
        p.setPipelineCount(rs.getInt("pipeline_count"));
        p.setLatestExecutionStatus(rs.getString("latest_status"));
        return p;
    }
}
