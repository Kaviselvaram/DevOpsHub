package com.devopshub.service;

import com.devopshub.exception.DevOpsHubException;
import com.devopshub.model.Stage;
import com.devopshub.model.StageType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ProjectImportService Inspection & Safe Archive Extraction Tests")
class ProjectImportServiceTest {

    private ProjectImportService importService;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        importService = new ProjectImportService();
    }

    @Test
    @DisplayName("Should detect Maven Java project structure")
    void testDetectMavenProject() throws IOException {
        File mavenDir = tempDir.resolve("my-maven-app").toFile();
        mavenDir.mkdirs();
        new File(mavenDir, "pom.xml").createNewFile();

        ProjectImportService.DetectedProject detected = importService.inspectPath(mavenDir);
        assertEquals("Java", detected.getProjectType());
        assertEquals("Maven", detected.getBuildSystem());
        assertFalse(detected.isArchive());

        List<Stage> stages = importService.generateAuthenticStages(detected.getBuildSystem(), "test-pipe");
        assertEquals(4, stages.size());
        assertEquals("mvn compile -B", stages.get(0).getCommand());
        assertEquals("mvn test -B", stages.get(1).getCommand());
        assertEquals(StageType.DEPLOY, stages.get(3).getStageType());
    }

    @Test
    @DisplayName("Should detect NodeJS project structure")
    void testDetectNodeProject() throws IOException {
        File nodeDir = tempDir.resolve("my-node-app").toFile();
        nodeDir.mkdirs();
        new File(nodeDir, "package.json").createNewFile();

        ProjectImportService.DetectedProject detected = importService.inspectPath(nodeDir);
        assertEquals("NodeJS", detected.getProjectType());
        assertEquals("npm", detected.getBuildSystem());

        List<Stage> stages = importService.generateAuthenticStages(detected.getBuildSystem(), "test-pipe");
        assertTrue(stages.stream().anyMatch(s -> s.getCommand().contains("npm install")));
        assertTrue(stages.stream().anyMatch(s -> s.getCommand().contains("npm test")));
    }

    @Test
    @DisplayName("Should detect Python project structure")
    void testDetectPythonProject() throws IOException {
        File pyDir = tempDir.resolve("my-python-app").toFile();
        pyDir.mkdirs();
        new File(pyDir, "requirements.txt").createNewFile();

        ProjectImportService.DetectedProject detected = importService.inspectPath(pyDir);
        assertEquals("Python", detected.getProjectType());
        assertEquals("pip/pytest", detected.getBuildSystem());
    }

    @Test
    @DisplayName("Should safely extract legitimate ZIP archive")
    void testExtractSafeZip() throws IOException {
        File zipFile = tempDir.resolve("sample.zip").toFile();
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile))) {
            ZipEntry entry = new ZipEntry("src/index.js");
            zos.putNextEntry(entry);
            zos.write("console.log('DevOpsHub Genuine Import');".getBytes());
            zos.closeEntry();
        }

        File destDir = tempDir.resolve("extracted").toFile();
        File result = importService.extractArchiveSafely(zipFile, destDir);

        assertTrue(result.exists());
        File extractedFile = new File(destDir, "src/index.js");
        assertTrue(extractedFile.exists());
    }

    @Test
    @DisplayName("Should prevent Zip-Slip path traversal vulnerability and throw SecurityException")
    void testZipSlipVulnerabilityDefense() throws IOException {
        File maliciousZip = tempDir.resolve("malicious-zipslip.zip").toFile();
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(maliciousZip))) {
            // Malicious entry attempting to escape target directory
            ZipEntry maliciousEntry = new ZipEntry("../evil-escape.txt");
            zos.putNextEntry(maliciousEntry);
            zos.write("malicious payload".getBytes());
            zos.closeEntry();
        }

        File destDir = tempDir.resolve("safe-target").toFile();
        destDir.mkdirs();

        SecurityException ex = assertThrows(SecurityException.class, () -> {
            importService.extractArchiveSafely(maliciousZip, destDir);
        });

        assertTrue(ex.getMessage().contains("Zip Slip path traversal blocked"));
        assertFalse(new File(destDir.getParentFile(), "evil-escape.txt").exists(), "Malicious file must not be written outside target");
    }

    @Test
    @DisplayName("Should reject nonexistent path with clear exception")
    void testNonexistentPath() {
        File nonExistent = new File(tempDir.toFile(), "does-not-exist-dir");
        assertThrows(DevOpsHubException.class, () -> importService.inspectPath(nonExistent));
    }
}
