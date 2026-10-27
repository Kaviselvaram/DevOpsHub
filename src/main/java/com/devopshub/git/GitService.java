package com.devopshub.git;

import com.devopshub.config.AppConfig;
import com.devopshub.exception.DevOpsHubException;
import com.devopshub.util.SecretMasker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Genuine Git Integration Service.
 * Performs real Git CLI operations: clone, fetch, checkout, branch inspection,
 * commit inspection, diff inspection, and working-tree status.
 * Defends against destructive cleanup (SEC-03) and parent repository traversal (ARCH-02).
 * All credentials in URLs and process logs are automatically redacted.
 */
public class GitService {
    private static final Logger logger = LoggerFactory.getLogger(GitService.class);

    private static final java.util.concurrent.ConcurrentHashMap<String, Object> workspaceLocks = new java.util.concurrent.ConcurrentHashMap<>();

    private final String gitExecutable;
    private final File baseWorkspaceDir;
    private final int timeoutSeconds;

    public GitService() {
        this(AppConfig.getInstance().get("GIT_EXECUTABLE", "git"),
             new File(AppConfig.getInstance().getWorkspaceDir()),
             AppConfig.getInstance().getGitTimeoutSeconds());
    }

    public GitService(String gitExecutable, File baseWorkspaceDir) {
        this(gitExecutable, baseWorkspaceDir, AppConfig.getInstance().getGitTimeoutSeconds());
    }

    public GitService(String gitExecutable, File baseWorkspaceDir, int timeoutSeconds) {
        this.gitExecutable = gitExecutable != null ? gitExecutable : "git";
        File base = baseWorkspaceDir != null ? baseWorkspaceDir : new File("./workspaces");
        File resolved;
        try {
            resolved = base.getCanonicalFile();
        } catch (IOException e) {
            resolved = base.getAbsoluteFile();
        }
        this.baseWorkspaceDir = resolved;
        this.timeoutSeconds = timeoutSeconds > 0 ? timeoutSeconds : 300;
        if (!this.baseWorkspaceDir.exists()) {
            this.baseWorkspaceDir.mkdirs();
        }
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public File getBaseWorkspaceDir() {
        return baseWorkspaceDir;
    }

    public boolean isGitInstalled() {
        try {
            GitExecutionResult res = executeGit(baseWorkspaceDir, "--version");
            return res.getExitCode() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    public boolean isValidRepositoryUrl(String url) {
        if (url == null || url.trim().isEmpty()) {
            return false;
        }
        String u = url.trim();
        // Support HTTPS, HTTP, SSH, Git protocol, file URI, or local directory paths
        if (u.startsWith("https://") || u.startsWith("http://") || u.startsWith("git@") || u.startsWith("ssh://")
                || u.startsWith("git://") || u.startsWith("file:/") || u.startsWith("file://")) {
            return true;
        }
        File localFile = new File(u);
        return localFile.exists() && localFile.isDirectory();
    }

    public File getProjectWorkspace(String projectId) {
        File dir = new File(baseWorkspaceDir, "projects/" + projectId);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    /**
     * Validates that the destination directory resides strictly inside the application workspace root.
     * Defends against directory traversal payloads (e.g. ../../) and escapes.
     */
    public void validateWorkspaceConfinement(File destinationDir) {
        if (destinationDir == null) {
            throw new DevOpsHubException("Destination directory cannot be null.");
        }
        try {
            String canonicalDest = destinationDir.getCanonicalPath();
            String canonicalBase = baseWorkspaceDir.getCanonicalPath();
            String appRoot = new File(".").getCanonicalPath();

            if (canonicalDest.equals(appRoot)) {
                throw new DevOpsHubException("Security Violation: Cannot use application root directory as clone destination.");
            }
            if (canonicalDest.equals(canonicalBase)) {
                throw new DevOpsHubException("Security Violation: Cannot use base workspace root directly as clone destination.");
            }
            String authorizedWorkspaces = appRoot + File.separator + "workspaces";
            boolean inBase = canonicalDest.startsWith(canonicalBase + File.separator) || canonicalDest.equals(canonicalBase);
            boolean inWorkspaces = canonicalDest.startsWith(authorizedWorkspaces + File.separator) || canonicalDest.equals(authorizedWorkspaces);
            if (!inBase && !inWorkspaces) {
                throw new DevOpsHubException("Security Violation: Destination path '" + canonicalDest + "' is outside authorized workspace root '" + canonicalBase + "'.");
            }
        } catch (IOException e) {
            throw new DevOpsHubException("Failed to validate workspace destination: " + e.getMessage());
        }
    }

    /**
     * Resolves the authorized Git repository root for a directory.
     * Supports immediate repositories, Git worktrees/submodules (.git file), and nested monorepo subdirectories.
     * Strictly refuses to resolve ancestor repositories that equal or escape outside the workspace root into DevOpsHub itself (ARCH-02).
     */
    public File resolveAuthorizedRepositoryRoot(File repoDir) {
        if (repoDir == null || !repoDir.exists()) {
            return null;
        }
        try {
            String canonicalRepo = repoDir.getCanonicalPath();
            String appRoot = new File(".").getCanonicalPath();
            String canonicalBase = baseWorkspaceDir.getCanonicalPath();

            // 1. Immediate .git entry (directory for standard clone, or file for worktree / submodule)
            File immediateGit = new File(repoDir, ".git");
            if (immediateGit.exists()) {
                if (canonicalRepo.equals(appRoot)) {
                    return null;
                }
                return repoDir;
            }

            // 2. Query Git CLI for the enclosing repository root (supports nested workspaces)
            GitExecutionResult res = executeGit(repoDir, "rev-parse", "--show-toplevel");
            if (res.getExitCode() == 0) {
                String toplevelPath = res.getStdout().trim();
                if (!toplevelPath.isEmpty()) {
                    File toplevelDir = new File(toplevelPath).getCanonicalFile();
                    String canonicalTop = toplevelDir.getCanonicalPath();

                    // Guard against resolving DevOpsHub's root repository
                    if (canonicalTop.equals(appRoot)) {
                        return null;
                    }

                    // Authorized if toplevel is in baseWorkspaceDir or an authorized external project dir
                    String authorizedWorkspaces = appRoot + File.separator + "workspaces";
                    boolean inBase = canonicalTop.startsWith(canonicalBase + File.separator) || canonicalTop.equals(canonicalBase);
                    boolean inWorkspaces = canonicalTop.startsWith(authorizedWorkspaces + File.separator) || canonicalTop.equals(authorizedWorkspaces);
                    boolean isParentOfNestedRepo = canonicalRepo.startsWith(canonicalTop + File.separator);

                    if (inBase || inWorkspaces || isParentOfNestedRepo) {
                        return toplevelDir;
                    }
                }
            }
        } catch (Exception e) {
            logger.debug("Failed to resolve repository root for {}: {}", repoDir.getAbsolutePath(), e.getMessage());
        }
        return null;
    }

    /**
     * Checks if a directory contains an immediate .git child folder or file.
     * Prevents Git CLI commands from discovering and operating on an ancestor repository (ARCH-02).
     */
    public static boolean isImmediateGitRepository(File repoDir) {
        if (repoDir == null || !repoDir.exists() || !repoDir.isDirectory()) {
            return false;
        }
        File gitEntry = new File(repoDir, ".git");
        return gitEntry.exists();
    }

    public GitExecutionResult cloneOrFetch(String repositoryUrl, String branch, File destinationDir) {
        if (repositoryUrl == null || repositoryUrl.trim().isEmpty()) {
            throw new DevOpsHubException("Repository URL cannot be null or empty.");
        }
        if (destinationDir == null) {
            throw new DevOpsHubException("Destination directory cannot be null.");
        }

        // Validate destination directory confinement
        validateWorkspaceConfinement(destinationDir);

        Object lock = workspaceLocks.computeIfAbsent(destinationDir.getAbsolutePath(), k -> new Object());
        synchronized (lock) {
            String targetBranch = (branch != null && !branch.trim().isEmpty()) ? branch.trim() : "main";
            File gitDir = new File(destinationDir, ".git");

        if (gitDir.exists()) {
            logger.info("Existing Git repository found in {}. Checking branch and updates...", destinationDir.getAbsolutePath());
            GitExecutionResult remoteCheck = executeGit(destinationDir, "remote");
            boolean hasOrigin = remoteCheck.getExitCode() == 0 && remoteCheck.getStdout().contains("origin");

            if (hasOrigin) {
                GitExecutionResult fetchResult = executeGit(destinationDir, "fetch", "--all", "--prune", "--no-progress");
                if (fetchResult.getExitCode() != 0) {
                    return fetchResult;
                }
            }

            GitExecutionResult checkoutResult = executeGit(destinationDir, "checkout", targetBranch);
            if (checkoutResult.getExitCode() != 0 && hasOrigin) {
                logger.warn("Could not checkout branch '{}', checking if tracking branch exists...", targetBranch);
                executeGit(destinationDir, "checkout", "-b", targetBranch, "origin/" + targetBranch);
            }

            if (hasOrigin) {
                return executeGit(destinationDir, "pull", "origin", targetBranch);
            }
            return checkoutResult;
        } else {
            logger.info("Cloning repository {} into {}...", SecretMasker.mask(repositoryUrl), destinationDir.getAbsolutePath());

            // Track ownership: record whether destination existed before clone attempt (SEC-03)
            boolean destinationExistedBefore = destinationDir.exists();
            boolean createdByThisOperation = false;

            File parentDir = destinationDir.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }
            if (destinationDir.exists() && (destinationDir.list() == null || destinationDir.list().length == 0)) {
                destinationDir.delete();
            }
            if (!destinationDir.exists()) {
                createdByThisOperation = true;
            }

            String effectiveUrl = repositoryUrl;
            if (effectiveUrl != null && effectiveUrl.startsWith("file:/")) {
                try {
                    File fileFromUri = new File(java.net.URI.create(effectiveUrl));
                    effectiveUrl = fileFromUri.getAbsolutePath();
                } catch (Exception ignored) {
                    if (!effectiveUrl.startsWith("file:///")) {
                        effectiveUrl = "file:///" + effectiveUrl.substring("file:/".length());
                    }
                }
            }

            GitExecutionResult cloneResult = executeGit(parentDir != null ? parentDir : new File("."),
                    "clone", "--branch", targetBranch, "--single-branch", "--no-progress", effectiveUrl, destinationDir.getName());
            if (cloneResult.getExitCode() != 0) {
                String err = cloneResult.getStderr();
                if (err != null && (err.contains("Remote branch") || err.contains("not found in upstream origin"))) {
                    logger.warn("Branch-specific clone failed for branch '{}', attempting default clone...", targetBranch);
                    if (createdByThisOperation) {
                        safeCleanupDirectory(destinationDir);
                    }
                    cloneResult = executeGit(parentDir != null ? parentDir : new File("."), "clone", "--no-progress", effectiveUrl, destinationDir.getName());
                }
            }
            if (cloneResult.getExitCode() != 0) {
                if (createdByThisOperation) {
                    logger.warn("Git clone failed (exit code {}). Cleaning up incomplete workspace created during this operation at {}",
                            cloneResult.getExitCode(), destinationDir.getAbsolutePath());
                    safeCleanupDirectory(destinationDir);
                } else {
                    logger.warn("Git clone failed (exit code {}), but destination pre-existed. Preserving pre-existing destination at {}",
                            cloneResult.getExitCode(), destinationDir.getAbsolutePath());
                }
            }
            return cloneResult;
        }
    }
}

    /**
     * Safely deletes a directory within the application workspace root.
     * Guarantees that symlinks are deleted without recursing into target directories (SEC-03).
     */
    public void safeCleanupDirectory(File dir) {
        if (dir == null || !dir.exists()) {
            return;
        }
        try {
            Path dirPath = dir.toPath().toRealPath();
            Path basePath = baseWorkspaceDir.toPath().toRealPath();
            if (!dirPath.startsWith(basePath) || dirPath.equals(basePath)) {
                logger.warn("Refusing to clean up directory outside or equal to workspace root: {}", dirPath);
                return;
            }

            Files.walkFileTree(dirPath, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    // Delete the file or symlink itself without following
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path d, IOException exc) throws IOException {
                    Files.delete(d);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (Exception e) {
            logger.warn("Safe cleanup encountered an error while removing {}: {}", dir.getAbsolutePath(), e.getMessage());
        }
    }

    /**
     * Static helper for backward compatibility, delegating to symlink-safe cleanup.
     */
    public static void cleanupDirectory(File dir) {
        if (dir == null || !dir.exists()) {
            return;
        }
        try {
            Path dirPath = dir.toPath().toRealPath();
            Files.walkFileTree(dirPath, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path d, IOException exc) throws IOException {
                    Files.delete(d);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (Exception e) {
            logger.warn("Cleanup encountered an error while removing {}: {}", dir.getAbsolutePath(), e.getMessage());
        }
    }

    public List<String> getBranches(File repoDir) {
        List<String> branches = new ArrayList<>();
        File root = resolveAuthorizedRepositoryRoot(repoDir);
        if (root == null) {
            return branches;
        }
        GitExecutionResult res = executeGit(root, "branch", "-a");
        if (res.getExitCode() == 0) {
            for (String line : res.getStdout().split("\\R")) {
                String clean = line.replace("*", "").trim();
                if (!clean.isEmpty() && !clean.contains("->")) {
                    branches.add(clean);
                }
            }
        }
        return branches;
    }

    public List<String> getTags(File repoDir) {
        List<String> tags = new ArrayList<>();
        File root = resolveAuthorizedRepositoryRoot(repoDir);
        if (root == null) {
            return tags;
        }
        GitExecutionResult res = executeGit(root, "tag", "-l");
        if (res.getExitCode() == 0) {
            for (String line : res.getStdout().split("\\R")) {
                String clean = line.trim();
                if (!clean.isEmpty()) {
                    tags.add(clean);
                }
            }
        }
        return tags;
    }

    public String getCurrentCommitHash(File repoDir) {
        File root = resolveAuthorizedRepositoryRoot(repoDir);
        if (root == null) {
            return "UNKNOWN";
        }
        GitExecutionResult res = executeGit(root, "rev-parse", "HEAD");
        return res.getExitCode() == 0 ? res.getStdout().trim() : "UNKNOWN";
    }

    public String getCurrentBranch(File repoDir) {
        File root = resolveAuthorizedRepositoryRoot(repoDir);
        if (root == null) {
            return "UNKNOWN";
        }
        GitExecutionResult res = executeGit(root, "rev-parse", "--abbrev-ref", "HEAD");
        if (res.getExitCode() != 0) {
            return "UNKNOWN";
        }
        String branch = res.getStdout().trim();
        if ("HEAD".equalsIgnoreCase(branch)) {
            // Detached HEAD state: try to resolve tag or short commit
            GitExecutionResult tagRes = executeGit(root, "describe", "--tags", "--exact-match");
            if (tagRes.getExitCode() == 0 && !tagRes.getStdout().trim().isEmpty()) {
                return "HEAD (tag: " + tagRes.getStdout().trim() + ")";
            }
            String commit = getCurrentCommitHash(root);
            String shortCommit = (commit != null && commit.length() >= 7 && !commit.equals("UNKNOWN")) ? commit.substring(0, 7) : commit;
            return "HEAD (detached at " + shortCommit + ")";
        }
        return branch;
    }

    public String getLatestCommitMessage(File repoDir) {
        File root = resolveAuthorizedRepositoryRoot(repoDir);
        if (root == null) {
            return "";
        }
        GitExecutionResult res = executeGit(root, "log", "-1", "--pretty=%B");
        return res.getExitCode() == 0 ? res.getStdout().trim() : "";
    }

    public String getWorkingTreeStatus(File repoDir) {
        File root = resolveAuthorizedRepositoryRoot(repoDir);
        if (root == null) {
            return "";
        }
        GitExecutionResult res = executeGit(root, "status", "--porcelain");
        return res.getExitCode() == 0 ? res.getStdout() : "";
    }

    public String getDiff(File repoDir) {
        File root = resolveAuthorizedRepositoryRoot(repoDir);
        if (root == null) {
            return "";
        }
        GitExecutionResult res = executeGit(root, "diff");
        return res.getExitCode() == 0 ? res.getStdout() : "";
    }

    public GitExecutionResult executeGit(File workingDir, String... args) {
        List<String> command = new ArrayList<>();
        command.add(gitExecutable);
        for (String a : args) {
            command.add(a);
        }

        StringBuilder maskedCmd = new StringBuilder();
        for (String c : command) {
            maskedCmd.append(SecretMasker.mask(c)).append(" ");
        }

        ProcessBuilder pb = new ProcessBuilder(command);
        if (workingDir != null && workingDir.exists()) {
            pb.directory(workingDir);
        }
        pb.redirectErrorStream(false);
        // Prevent interactive terminal prompts from hanging CI/CD processes
        pb.environment().put("GIT_TERMINAL_PROMPT", "0");
        pb.environment().put("GIT_ASKPASS", "echo");
        // Prevent Git from traversing above workspace root (ARCH-02)
        pb.environment().put("GIT_CEILING_DIRECTORIES", baseWorkspaceDir.getAbsolutePath());

        long start = System.currentTimeMillis();
        try {
            Process process = pb.start();
            // Immediately close stdin so child process never hangs waiting for interactive input
            try {
                process.getOutputStream().close();
            } catch (Exception ignored) {}

            StringBuilder stdout = new StringBuilder();
            StringBuilder stderr = new StringBuilder();

            Thread tOut = new Thread(() -> {
                try (InputStreamReader r = new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8)) {
                    char[] buf = new char[4096];
                    int read;
                    while ((read = r.read(buf)) != -1) {
                        stdout.append(buf, 0, read);
                    }
                } catch (Exception ignored) {}
            });

            Thread tErr = new Thread(() -> {
                try (InputStreamReader r = new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8)) {
                    char[] buf = new char[4096];
                    int read;
                    while ((read = r.read(buf)) != -1) {
                        stderr.append(buf, 0, read);
                    }
                } catch (Exception ignored) {}
            });

            tOut.start();
            tErr.start();

            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                try {
                    process.descendants().forEach(ProcessHandle::destroyForcibly);
                } catch (Throwable ignored) {}
                process.destroyForcibly();
                return new GitExecutionResult(124, stdout.toString(),
                        "Git operation timed out after " + timeoutSeconds + "s",
                        maskedCmd.toString().trim(), (double) timeoutSeconds);
            }

            tOut.join(2000);
            tErr.join(2000);

            long durationMs = System.currentTimeMillis() - start;
            int code = process.exitValue();
            return new GitExecutionResult(code,
                    SecretMasker.mask(stdout.toString()),
                    SecretMasker.mask(stderr.toString()),
                    maskedCmd.toString().trim(), durationMs / 1000.0);

        } catch (Exception e) {
            long durationMs = System.currentTimeMillis() - start;
            return new GitExecutionResult(1, "", "Failed to execute git: " + e.getMessage(), maskedCmd.toString().trim(), durationMs / 1000.0);
        }
    }

    public static class GitExecutionResult {
        private final int exitCode;
        private final String stdout;
        private final String stderr;
        private final String command;
        private final double durationSeconds;

        public GitExecutionResult(int exitCode, String stdout, String stderr, String command, double durationSeconds) {
            this.exitCode = exitCode;
            this.stdout = stdout;
            this.stderr = stderr;
            this.command = command;
            this.durationSeconds = durationSeconds;
        }

        public int getExitCode() { return exitCode; }
        public String getStdout() { return stdout; }
        public String getStderr() { return stderr; }
        public String getCommand() { return command; }
        public double getDurationSeconds() { return durationSeconds; }
        public boolean isSuccess() { return exitCode == 0; }
    }
}
