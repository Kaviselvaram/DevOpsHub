package com.devopshub.git;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("GitService Real CLI & Controlled Repository Tests")
class GitServiceTest {

    private GitService gitService;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        gitService = new GitService("git", tempDir.toFile());
    }

    @Test
    @DisplayName("Should detect that git is installed on system PATH")
    void testIsGitInstalled() {
        assertTrue(gitService.isGitInstalled(), "Git executable must be installed and responding on system");
    }

    @Test
    @DisplayName("Should validate repository URLs correctly")
    void testIsValidRepositoryUrl() {
        assertTrue(gitService.isValidRepositoryUrl("https://github.com/torvalds/linux.git"));
        assertTrue(gitService.isValidRepositoryUrl("git@github.com:torvalds/linux.git"));
        assertTrue(gitService.isValidRepositoryUrl("ssh://git@github.com/torvalds/linux.git"));
        assertTrue(gitService.isValidRepositoryUrl(tempDir.toFile().getAbsolutePath()));

        assertFalse(gitService.isValidRepositoryUrl(null));
        assertFalse(gitService.isValidRepositoryUrl("   "));
        assertFalse(gitService.isValidRepositoryUrl("/non/existent/path/that/does/not/exist"));
    }

    @Test
    @DisplayName("Should perform real Git clone, commit inspection, and branch detection on local repository")
    void testRealLocalGitLifecycle() throws IOException, InterruptedException {
        // 1. Initialize a real local source repository
        File sourceRepoDir = tempDir.resolve("source-repo").toFile();
        sourceRepoDir.mkdirs();

        ProcessBuilder initPb = new ProcessBuilder("git", "init", "-b", "main");
        initPb.directory(sourceRepoDir);
        assertEquals(0, initPb.start().waitFor());

        // Configure git identity for test commits
        ProcessBuilder configName = new ProcessBuilder("git", "config", "user.name", "Test Runner");
        configName.directory(sourceRepoDir);
        configName.start().waitFor();

        ProcessBuilder configEmail = new ProcessBuilder("git", "config", "user.email", "runner@devopshub.local");
        configEmail.directory(sourceRepoDir);
        configEmail.start().waitFor();

        // Write a test source file
        File sampleFile = new File(sourceRepoDir, "app.txt");
        try (FileWriter fw = new FileWriter(sampleFile)) {
            fw.write("DevOpsHub End-To-End Git Integration Real Test");
        }

        // git add & commit
        ProcessBuilder addPb = new ProcessBuilder("git", "add", "app.txt");
        addPb.directory(sourceRepoDir);
        assertEquals(0, addPb.start().waitFor());

        ProcessBuilder commitPb = new ProcessBuilder("git", "commit", "-m", "Initial commit for DevOpsHub verification");
        commitPb.directory(sourceRepoDir);
        assertEquals(0, commitPb.start().waitFor());

        // 2. Test GitService clone of this real local repository
        File cloneDestDir = tempDir.resolve("cloned-repo").toFile();
        GitService.GitExecutionResult cloneRes = gitService.cloneOrFetch(sourceRepoDir.getAbsolutePath(), "main", cloneDestDir);

        assertTrue(cloneRes.isSuccess(), "Git clone should succeed: " + cloneRes.getStderr());
        assertTrue(new File(cloneDestDir, "app.txt").exists(), "Cloned file must exist on filesystem");

        // 3. Verify real commit hash and branch inspection
        String commitHash = gitService.getCurrentCommitHash(cloneDestDir);
        assertNotNull(commitHash);
        assertEquals(40, commitHash.length(), "Valid SHA-1 commit hash should be 40 hex characters");

        String branch = gitService.getCurrentBranch(cloneDestDir);
        assertEquals("main", branch);

        String commitMsg = gitService.getLatestCommitMessage(cloneDestDir);
        assertTrue(commitMsg.contains("Initial commit for DevOpsHub verification"));

        // 4. Verify working tree status (clean initially)
        String status = gitService.getWorkingTreeStatus(cloneDestDir);
        assertEquals("", status.trim(), "Working tree should be clean");

        // 5. Modify a file and verify diff/status inspection
        try (FileWriter fw = new FileWriter(new File(cloneDestDir, "app.txt"), true)) {
            fw.write("\nNew line added.");
        }
        String dirtyStatus = gitService.getWorkingTreeStatus(cloneDestDir);
        assertTrue(dirtyStatus.contains("M app.txt"), "Status must report modified file");

        String diff = gitService.getDiff(cloneDestDir);
        assertTrue(diff.contains("+New line added."));
    }

    @Test
    @DisplayName("Should report failure when cloning invalid or nonexistent repository")
    void testCloneInvalidRepo() {
        File cloneDestDir = tempDir.resolve("invalid-clone").toFile();
        GitService.GitExecutionResult res = gitService.cloneOrFetch("https://invalid.example.com/nonexistent-repo-999.git", "main", cloneDestDir);

        assertFalse(res.isSuccess(), "Clone of nonexistent repo must fail");
        assertNotEquals(0, res.getExitCode());
    }

    @Test
    @DisplayName("Should scrub credentials from git commands and logs")
    void testCredentialScrubbing() {
        File cloneDestDir = tempDir.resolve("creds-clone").toFile();
        String secretToken = "ghp_VerySecretToken1234567890ABCDEF";
        String sensitiveUrl = "https://oauth2:" + secretToken + "@github.com/nonexistent-org/nonexistent-repo.git";

        GitService.GitExecutionResult res = gitService.cloneOrFetch(sensitiveUrl, "main", cloneDestDir);

        assertFalse(res.isSuccess());
        assertFalse(res.getCommand().contains(secretToken), "Command string must NOT contain secret credentials");
        assertTrue(res.getCommand().contains("***"), "Command string must mask credentials with asterisks");
        assertFalse(res.getStderr().contains(secretToken), "Stderr must NOT contain raw credentials");
    }

    @Test
    @DisplayName("Should inspect branches and tags on repository")
    void testBranchAndTagInspection() throws IOException, InterruptedException {
        File sourceRepoDir = tempDir.resolve("tag-branch-repo").toFile();
        sourceRepoDir.mkdirs();

        ProcessBuilder initPb = new ProcessBuilder("git", "init", "-b", "main");
        initPb.directory(sourceRepoDir);
        assertEquals(0, initPb.start().waitFor());

        new ProcessBuilder("git", "config", "user.name", "Test Runner").directory(sourceRepoDir).start().waitFor();
        new ProcessBuilder("git", "config", "user.email", "runner@test.local").directory(sourceRepoDir).start().waitFor();

        File sample = new File(sourceRepoDir, "README.md");
        try (FileWriter fw = new FileWriter(sample)) {
            fw.write("# Tags and Branches Test");
        }
        new ProcessBuilder("git", "add", "README.md").directory(sourceRepoDir).start().waitFor();
        new ProcessBuilder("git", "commit", "-m", "Initial commit").directory(sourceRepoDir).start().waitFor();
        new ProcessBuilder("git", "tag", "v1.0.0").directory(sourceRepoDir).start().waitFor();
        new ProcessBuilder("git", "branch", "staging").directory(sourceRepoDir).start().waitFor();

        var branches = gitService.getBranches(sourceRepoDir);
        assertTrue(branches.contains("main") || branches.contains("staging"), "Should list branches");

        var tags = gitService.getTags(sourceRepoDir);
        assertTrue(tags.contains("v1.0.0"), "Should list tag v1.0.0");
    }
}
