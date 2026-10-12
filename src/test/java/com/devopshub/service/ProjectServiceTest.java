package com.devopshub.service;

import com.devopshub.exception.DevOpsHubException;
import com.devopshub.model.Project;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ProjectServiceTest {

    private ProjectService projectService;

    @BeforeEach
    void setUp() {
        projectService = new ProjectService();
    }

    @Test
    @DisplayName("Create project with valid input succeeds")
    void testCreateProjectSuccess() {
        String uniqueName = "Test Project " + System.currentTimeMillis();
        Project p = projectService.createProject(uniqueName, "Description", "https://github.com/vit/test.git", "main", "Java");

        assertNotNull(p.getId());
        assertEquals(uniqueName, p.getName());
        assertEquals("ACTIVE", p.getStatus());

        Project fetched = projectService.getProjectById(p.getId()).orElseThrow();
        assertEquals(uniqueName, fetched.getName());
    }

    @Test
    @DisplayName("Create project with empty name throws DevOpsHubException")
    void testCreateProjectEmptyNameThrows() {
        assertThrows(DevOpsHubException.class, () ->
                projectService.createProject("   ", "Desc", "https://github.com/vit/test.git", "main", "Java"));
    }

    @Test
    @DisplayName("Create project with empty repository URL throws DevOpsHubException")
    void testCreateProjectEmptyRepoThrows() {
        assertThrows(DevOpsHubException.class, () ->
                projectService.createProject("Valid Name", "Desc", "", "main", "Java"));
    }

    @Test
    @DisplayName("Search projects returns matching results")
    void testSearchProjects() {
        String uniqueName = "Searchable " + System.currentTimeMillis();
        projectService.createProject(uniqueName, "Special Search Token", "https://github.com/vit/search.git", "main", "Java");

        List<Project> results = projectService.searchProjects("Special Search Token");
        assertFalse(results.isEmpty());
        assertTrue(results.stream().anyMatch(p -> p.getName().equals(uniqueName)));
    }
}
