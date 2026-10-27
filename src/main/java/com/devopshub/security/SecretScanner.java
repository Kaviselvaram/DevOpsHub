package com.devopshub.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Genuine static secret scanner that inspects repository files for committed credentials,
 * private keys, API tokens, and sensitive strings.
 */
public class SecretScanner implements SecurityScanner {
    private static final Logger logger = LoggerFactory.getLogger(SecretScanner.class);
    private static final String VERSION = "1.0.0";

    private static final Set<String> IGNORED_DIRS = Set.of(
            ".git", "node_modules", "target", "build", ".next", "dist", "vendor", ".idea", ".vscode"
    );

    private static final Set<String> IGNORED_EXTENSIONS = Set.of(
            ".png", ".jpg", ".jpeg", ".gif", ".ico", ".svg", ".woff", ".woff2", ".ttf", ".eot",
            ".jar", ".war", ".zip", ".tar", ".gz", ".class", ".exe", ".dll", ".so", ".dylib"
    );

    private record Rule(
            String id,
            String title,
            Pattern pattern,
            SecuritySeverity severity,
            String remediation
    ) {}

    private final List<Rule> rules = List.of(
            new Rule("SEC-001", "AWS Access Key Leak",
                    Pattern.compile("\\b(AKIA|ASIA)[0-9A-Z]{16}\\b"),
                    SecuritySeverity.CRITICAL,
                    "Revoke this AWS key in AWS IAM immediately and configure AWS credentials via environment variables or IAM roles."),

            new Rule("SEC-002", "Unencrypted Private Key Committed",
                    Pattern.compile("-----BEGIN (?:(?:RSA|EC|DSA|OPENSSH|PGP|ENCRYPTED)\\s+)?(?:PRIVATE )?KEY-----"),
                    SecuritySeverity.CRITICAL,
                    "Remove the private key from version control, regenerate the keypair, and inject secrets at runtime."),

            new Rule("SEC-003", "GitHub Personal Access Token",
                    Pattern.compile("\\b(ghp_[a-zA-Z0-9]{36}|github_pat_[a-zA-Z0-9_]{50,})\\b"),
                    SecuritySeverity.CRITICAL,
                    "Revoke the compromised GitHub token at github.com/settings/tokens and use encrypted secrets."),

            new Rule("SEC-004", "Slack Webhook / Token",
                    Pattern.compile("(https://hooks\\.slack\\.com/services/T[0-9a-zA-Z_]+/B[0-9a-zA-Z_]+/[0-9a-zA-Z_]+|xox[baprs]-[0-9a-zA-Z]{10,48})"),
                    SecuritySeverity.HIGH,
                    "Revoke the Slack webhook or bot token and store it in environment variables."),

            new Rule("SEC-005", "Hardcoded Password or API Secret Assignment",
                    Pattern.compile("(?i)(password|secret|api_key|apikey|private_key|auth_token)\\s*[:=]\\s*[\"']([^\"'\\s]{8,})[\"']"),
                    SecuritySeverity.HIGH,
                    "Extract hardcoded credentials into an external configuration service or environment variable.")
    );

    @Override
    public SecurityScanResult scan(File workspaceDir, String executionId, String stageExecutionId) {
        if (workspaceDir == null || !workspaceDir.exists() || !workspaceDir.isDirectory()) {
            return SecurityScanResult.failure("SecretScanner", VERSION, "Workspace directory does not exist or is invalid: " + workspaceDir);
        }

        List<SecurityFinding> findings = new ArrayList<>();
        int filesScanned = 0;

        try (Stream<Path> paths = Files.walk(workspaceDir.toPath(), 10)) {
            Iterator<Path> it = paths.iterator();
            while (it.hasNext() && filesScanned < 1000) {
                Path path = it.next();
                File file = path.toFile();

                if (!file.isFile() || shouldIgnore(workspaceDir, file)) {
                    continue;
                }

                if (file.length() > 2 * 1024 * 1024) { // Skip files > 2MB
                    continue;
                }

                filesScanned++;
                scanFile(workspaceDir, file, executionId, stageExecutionId, findings);
            }
        } catch (IOException e) {
            logger.error("Error walking workspace for secret scan: {}", e.getMessage(), e);
            return SecurityScanResult.failure("SecretScanner", VERSION, "Filesystem error: " + e.getMessage());
        }

        logger.info("SecretScanner finished. Scanned {} files, detected {} potential leaks.", filesScanned, findings.size());
        return SecurityScanResult.success("DevOpsHub SecretScanner", VERSION, findings);
    }

    private void scanFile(File workspaceDir, File file, String executionId, String stageExecutionId, List<SecurityFinding> findings) {
        String relativePath = workspaceDir.toPath().relativize(file.toPath()).toString();

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            int lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.length() > 2000) continue; // Skip minified lines

                for (Rule rule : rules) {
                    Matcher m = rule.pattern.matcher(line);
                    if (m.find()) {
                        String matchedText = m.group();
                        // Ignore harmless placeholders
                        if (isPlaceholder(matchedText, line)) {
                            continue;
                        }

                        String masked = maskSecret(matchedText);
                        String description = String.format("Found pattern matching '%s' on line %d: %s",
                                rule.title, lineNumber, masked);

                        SecurityFinding finding = new SecurityFinding(
                                executionId, stageExecutionId, rule.id,
                                rule.severity, SecurityCategory.SECRET_LEAK,
                                rule.title, relativePath, lineNumber,
                                description, rule.remediation
                        );
                        findings.add(finding);
                    }
                }
            }
        } catch (IOException e) {
            logger.warn("Could not read file {} for secret scanning: {}", relativePath, e.getMessage());
        }
    }

    private boolean isPlaceholder(String match, String line) {
        String lower = match.toLowerCase();
        return lower.contains("your_api_key") || lower.contains("insert_token")
                || lower.contains("changeme") || lower.contains("<key>")
                || lower.contains("your_password_here") || lower.equals("password")
                || lower.equals("12345678");
    }

    private String maskSecret(String secret) {
        if (secret.length() <= 8) {
            return "***";
        }
        return secret.substring(0, 4) + "****" + secret.substring(secret.length() - 4);
    }

    private boolean shouldIgnore(File workspaceDir, File file) {
        String name = file.getName();
        for (String ext : IGNORED_EXTENSIONS) {
            if (name.toLowerCase().endsWith(ext)) return true;
        }

        Path rel = workspaceDir.toPath().relativize(file.toPath());
        for (Path part : rel) {
            if (IGNORED_DIRS.contains(part.toString())) {
                return true;
            }
        }
        return false;
    }
}
