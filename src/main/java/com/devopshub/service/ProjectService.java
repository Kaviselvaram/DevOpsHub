package com.devopshub.service;

import com.devopshub.exception.DevOpsHubException;
import com.devopshub.model.Project;
import com.devopshub.repository.ProjectRepository;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class ProjectService {
    private final ProjectRepository projectRepository;

    public ProjectService() {
        this.projectRepository = new ProjectRepository();
    }

    public ProjectService(ProjectRepository projectRepository) {
        this.projectRepository = projectRepository;
    }

    public List<Project> getAllProjects() {
        return projectRepository.findAll();
    }

    public Optional<Project> getProjectById(String id) {
        if (id == null || id.trim().isEmpty()) {
            return Optional.empty();
        }
        return projectRepository.findById(id);
    }

    public List<Project> searchProjects(String query) {
        if (query == null || query.trim().isEmpty()) {
            return getAllProjects();
        }
        return projectRepository.search(query.trim());
    }

    public Project createProject(String name, String description, String repositoryUrl, String defaultBranch, String projectType) {
        validateProjectInput(name, repositoryUrl);

        Project project = new Project();
        project.setId(UUID.randomUUID().toString());
        project.setName(name.trim());
        project.setDescription(description != null ? description.trim() : "");
        project.setRepositoryUrl(repositoryUrl.trim());
        project.setDefaultBranch(defaultBranch != null && !defaultBranch.trim().isEmpty() ? defaultBranch.trim() : "main");
        project.setProjectType(projectType != null && !projectType.trim().isEmpty() ? projectType.trim() : "Java");
        project.setStatus("ACTIVE");
        project.setCreatedAt(new Timestamp(System.currentTimeMillis()));
        project.setUpdatedAt(new Timestamp(System.currentTimeMillis()));

        projectRepository.save(project);
        return project;
    }

    public Project updateProject(String id, String name, String description, String repositoryUrl, String defaultBranch, String projectType, String status) {
        if (id == null || id.trim().isEmpty()) {
            throw new DevOpsHubException("Project ID is required for update.");
        }
        validateProjectInput(name, repositoryUrl);

        Project project = projectRepository.findById(id)
                .orElseThrow(() -> new DevOpsHubException("Project not found with id: " + id));

        project.setName(name.trim());
        project.setDescription(description != null ? description.trim() : "");
        project.setRepositoryUrl(repositoryUrl.trim());
        project.setDefaultBranch(defaultBranch != null && !defaultBranch.trim().isEmpty() ? defaultBranch.trim() : "main");
        project.setProjectType(projectType != null && !projectType.trim().isEmpty() ? projectType.trim() : "Java");
        project.setStatus(status != null ? status : "ACTIVE");
        project.setUpdatedAt(new Timestamp(System.currentTimeMillis()));

        projectRepository.update(project);
        return project;
    }

    public void deleteProject(String id) {
        if (id == null || id.trim().isEmpty()) {
            throw new DevOpsHubException("Project ID is required for deletion.");
        }
        projectRepository.delete(id);
    }

    private void validateProjectInput(String name, String repositoryUrl) {
        if (name == null || name.trim().isEmpty()) {
            throw new DevOpsHubException("Project name is required.");
        }
        if (name.trim().length() > 100) {
            throw new DevOpsHubException("Project name cannot exceed 100 characters.");
        }
        if (repositoryUrl == null || repositoryUrl.trim().isEmpty()) {
            throw new DevOpsHubException("Repository URL is required.");
        }
    }
}
