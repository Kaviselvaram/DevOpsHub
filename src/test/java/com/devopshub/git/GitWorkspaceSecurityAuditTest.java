package com.devopshub.git;

import com.devopshub.exception.DevOpsHubException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SEC-03 & ARCH-02: Git Workspace Confinement, Cleanup Safety & Attribution Isolation Tests")
class GitWorkspaceSecurityAuditTest {

    @Test
    @DisplayName("Pre-existing directory must NOT be deleted when git clone fails (SEC-03)")
    void testPreExistingDirectoryPreservedOnCloneFailure(@TempDir File tempWorkspaceRoot) throws IOException {
        GitService gitService = new GitService("git", tempWorkspaceRoot);

        // Pre-create a user directory with pre-existing work/files
        File preExistingDir = new File(tempWorkspaceRoot, "my-existing-project");
        preExistingDir.mkdirs();
        File sentinelFile = new File(preExistingDir, "important_user_file.txt");
        Files.writeString(sentinelFile.toPath(), "DO NOT DELETE THIS WORK");

        assertTrue(preExistingDir.exists());
        assertTrue(sentinelFile.exists());

        // Attempt to clone with an invalid repository URL
        GitService.GitExecutionResult result = gitService.cloneOrFetch(
                "https://invalid.example.com/non-existent-repo.git",
                "main",
                preExistingDir
        );

        assertNotEquals(0, result.getExitCode(), "Clone of non-existent repo should fail");
        assertTrue(preExistingDir.exists(), "Pre-existing directory must NOT be deleted on clone failure");
        assertTrue(sentinelFile.exists(), "Pre-existing user files must NOT be wiped");
        assertEquals("DO NOT DELETE THIS WORK", Files.readString(sentinelFile.toPath()));
    }

    @Test
    @DisplayName("Destination directory outside base workspace root must be rejected (SEC-03)")
    void testDestinationOutsideWorkspaceRootRejected(@TempDir File tempWorkspaceRoot, @TempDir File outsideDir) {
        GitService gitService = new GitService("git", tempWorkspaceRoot);

        File escapeDir = new File(outsideDir, "escape-attempt");

        assertThrows(DevOpsHubException.class, () -> gitService.validateWorkspaceConfinement(escapeDir),
                "Destination outside authorized workspace root must be rejected");

        assertThrows(DevOpsHubException.class, () ->
                gitService.cloneOrFetch("https://github.com/test/repo.git", "main", escapeDir));
    }

    @Test
    @DisplayName("Subdirectory without immediate .git child must return UNKNOWN and NOT resolve parent repo (ARCH-02)")
    void testDirectoryWithoutImmediateGitReturnsUnknown(@TempDir File tempWorkspaceRoot) {
        GitService gitService = new GitService("git", tempWorkspaceRoot);

        File subDirWithoutGit = new File(tempWorkspaceRoot, "plain-subfolder");
        subDirWithoutGit.mkdirs();

        // Must not execute rev-parse on parent or return DevOpsHub commit hash
        String commitHash = gitService.getCurrentCommitHash(subDirWithoutGit);
        assertEquals("UNKNOWN", commitHash, "Directory without immediate .git must return UNKNOWN");

        String branch = gitService.getCurrentBranch(subDirWithoutGit);
        assertEquals("UNKNOWN", branch, "Directory without immediate .git must return UNKNOWN branch");

        String message = gitService.getLatestCommitMessage(subDirWithoutGit);
        assertEquals("", message, "Directory without immediate .git must return empty message");
    }

    @Test
    @DisplayName("Safe cleanup must delete symlinks without following into target external folders (SEC-03)")
    void testSafeCleanupDoesNotFollowSymlinks(@TempDir File tempWorkspaceRoot, @TempDir File externalDir) throws IOException {
        GitService gitService = new GitService("git", tempWorkspaceRoot);

        // Create an external folder with confidential data outside workspace
        File secretFile = new File(externalDir, "confidential_keys.pem");
        Files.writeString(secretFile.toPath(), "EXTERNAL PRIVATE KEY");

        // Inside workspace, create a directory and a symlink pointing to externalDir
        File managedFolder = new File(tempWorkspaceRoot, "managed-folder");
        managedFolder.mkdirs();
        Path symlinkPath = Path.of(managedFolder.getAbsolutePath(), "symlink-to-external");

        try {
            Files.createSymbolicLink(symlinkPath, externalDir.toPath());
        } catch (UnsupportedOperationException | IOException e) {
            // If OS/filesystem does not support symlinks in test environment, skip symlink assertion
            return;
        }

        assertTrue(Files.isSymbolicLink(symlinkPath));

        // Perform safe cleanup of managedFolder
        gitService.safeCleanupDirectory(managedFolder);

        // Managed folder and symlink should be gone
        assertFalse(managedFolder.exists(), "Managed folder should be cleaned up");

        // External directory and secret file must remain completely untouched!
        assertTrue(externalDir.exists(), "External directory must NOT be deleted");
        assertTrue(secretFile.exists(), "Confidential file outside workspace must NOT be deleted");
        assertEquals("EXTERNAL PRIVATE KEY", Files.readString(secretFile.toPath()));
    }

    @Test
    @DisplayName("Git worktree where .git is a file must resolve authorized repository root (Item 5)")
    void testWorktreeGitFileResolvesAuthorizedRoot(@TempDir File tempWorkspaceRoot) throws IOException {
        GitService gitService = new GitService("git", tempWorkspaceRoot);

        File worktreeDir = new File(tempWorkspaceRoot, "my-worktree");
        worktreeDir.mkdirs();

        // Simulate Git worktree where .git is a file pointing to gitdir
        File gitFile = new File(worktreeDir, ".git");
        Files.writeString(gitFile.toPath(), "gitdir: /path/to/main/repo/.git/worktrees/my-worktree\n");

        assertTrue(gitFile.exists() && gitFile.isFile(), ".git must exist as a file in worktrees");

        File root = gitService.resolveAuthorizedRepositoryRoot(worktreeDir);
        assertNotNull(root, "Worktree directory with .git file must be resolved as authorized root");
        assertEquals(worktreeDir.getCanonicalPath(), root.getCanonicalPath(), "Worktree root should be worktreeDir");
    }

    @Test
    @DisplayName("Destination directory with matching prefix but sibling name must be rejected (Item 4)")
    void testStrictWorkspacePrefixRejectsSiblingDirectories(@TempDir File tempBase) {
        GitService gitService = new GitService("git", tempBase);

        // e.g. base is /path/workspaces, sibling is /path/workspaces_evil
        File siblingDir = new File(tempBase.getParentFile(), tempBase.getName() + "_malicious_sibling");

        assertThrows(DevOpsHubException.class, () -> gitService.validateWorkspaceConfinement(siblingDir),
                "Sibling directory sharing string prefix must be rejected by workspace confinement");
    }
}
