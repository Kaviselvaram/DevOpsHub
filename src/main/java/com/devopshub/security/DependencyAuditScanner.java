package com.devopshub.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Dependency audit scanner that inspects package manifests (package.json, pom.xml)
 * for insecure configurations, plaintext HTTP repository endpoints, wildcards, and known CVE patterns.
 */
public class DependencyAuditScanner implements SecurityScanner {
    private static final Logger logger = LoggerFactory.getLogger(DependencyAuditScanner.class);
    private static final String VERSION = "1.0.0";

    @Override
    public SecurityScanResult scan(File workspaceDir, String executionId, String stageExecutionId) {
        if (workspaceDir == null || !workspaceDir.exists() || !workspaceDir.isDirectory()) {
            return SecurityScanResult.failure("DependencyAuditScanner", VERSION, "Workspace directory invalid: " + workspaceDir);
        }

        List<SecurityFinding> findings = new ArrayList<>();

        File packageJson = new File(workspaceDir, "package.json");
        if (packageJson.exists()) {
            auditPackageJson(packageJson, executionId, stageExecutionId, findings);
        }

        File pomXml = new File(workspaceDir, "pom.xml");
        if (pomXml.exists()) {
            auditPomXml(pomXml, executionId, stageExecutionId, findings);
        }

        logger.info("DependencyAuditScanner finished. Detected {} dependency security findings.", findings.size());
        return SecurityScanResult.success("DevOpsHub DependencyAuditScanner", VERSION, findings);
    }

    private void auditPackageJson(File packageJson, String executionId, String stageExecutionId, List<SecurityFinding> findings) {
        try (BufferedReader reader = new BufferedReader(new FileReader(packageJson))) {
            String line;
            int lineNumber = 0;
            boolean inDeps = false;

            Pattern insecureHttp = Pattern.compile("\"(http://[^\"]+)\"");
            Pattern wildcardVersion = Pattern.compile("\"([^\"]+)\"\\s*:\\s*\"([*]|latest)\"");

            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.contains("\"dependencies\"") || line.contains("\"devDependencies\"")) {
                    inDeps = true;
                    continue;
                }
                if (inDeps && line.contains("}")) {
                    inDeps = false;
                }

                if (inDeps) {
                    Matcher mWild = wildcardVersion.matcher(line);
                    if (mWild.find()) {
                        String depName = mWild.group(1);
                        findings.add(new SecurityFinding(
                                executionId, stageExecutionId, "DEP-001",
                                SecuritySeverity.MEDIUM, SecurityCategory.DEPENDENCY_VULNERABILITY,
                                "Unpinned Dependency Version (" + depName + ")",
                                "package.json", lineNumber,
                                "Package '" + depName + "' uses unpinned version '" + mWild.group(2) + "', posing a supply-chain risk.",
                                "Pin the dependency to a specific stable semantic version."
                        ));
                    }

                    Matcher mHttp = insecureHttp.matcher(line);
                    if (mHttp.find()) {
                        findings.add(new SecurityFinding(
                                executionId, stageExecutionId, "DEP-002",
                                SecuritySeverity.HIGH, SecurityCategory.INSECURE_CONFIGURATION,
                                "Insecure HTTP Package Repository / URL",
                                "package.json", lineNumber,
                                "Dependency fetched over unencrypted HTTP: " + mHttp.group(1),
                                "Use HTTPS repository endpoints to prevent Man-in-the-Middle tampering."
                        ));
                    }
                }
            }
        } catch (IOException e) {
            logger.warn("Could not read package.json for dependency audit: {}", e.getMessage());
        }
    }

    private void auditPomXml(File pomXml, String executionId, String stageExecutionId, List<SecurityFinding> findings) {
        try (BufferedReader reader = new BufferedReader(new FileReader(pomXml))) {
            String line;
            int lineNumber = 0;
            Pattern insecureRepo = Pattern.compile("<url>\\s*(http://[^<]+)\\s*</url>");
            Pattern vulnerableLog4j = Pattern.compile("<artifactId>log4j-core</artifactId>");
            Pattern vulnerableVersion = Pattern.compile("<version>2\\.(?:[0-9]|1[0-6])(?:\\.[0-9]+)?</version>");

            boolean lastWasLog4j = false;

            while ((line = reader.readLine()) != null) {
                lineNumber++;

                Matcher mHttp = insecureRepo.matcher(line);
                if (mHttp.find()) {
                    findings.add(new SecurityFinding(
                            executionId, stageExecutionId, "DEP-003",
                            SecuritySeverity.HIGH, SecurityCategory.INSECURE_CONFIGURATION,
                            "Insecure HTTP Maven Repository",
                            "pom.xml", lineNumber,
                            "Repository configured with unencrypted HTTP: " + mHttp.group(1),
                            "Update repository URL to HTTPS."
                    ));
                }

                if (vulnerableLog4j.matcher(line).find()) {
                    lastWasLog4j = true;
                } else if (lastWasLog4j) {
                    if (vulnerableVersion.matcher(line).find()) {
                        findings.add(new SecurityFinding(
                                executionId, stageExecutionId, "CVE-2021-44228",
                                SecuritySeverity.CRITICAL, SecurityCategory.DEPENDENCY_VULNERABILITY,
                                "Vulnerable Log4j Core (Log4Shell CVE-2021-44228)",
                                "pom.xml", lineNumber,
                                "Detected vulnerable Log4j Core version subject to remote code execution.",
                                "Upgrade log4j-core to 2.17.1 or higher immediately."
                        ));
                        lastWasLog4j = false;
                    } else if (line.contains("</dependency>")) {
                        lastWasLog4j = false;
                    }
                }
            }
        } catch (IOException e) {
            logger.warn("Could not read pom.xml for dependency audit: {}", e.getMessage());
        }
    }
}
