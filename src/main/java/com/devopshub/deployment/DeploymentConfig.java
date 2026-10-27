package com.devopshub.deployment;

import java.io.File;

/**
 * Configuration payload specifying destination parameters for an artifact deployment.
 */
public class DeploymentConfig {
    private final DeploymentTargetType targetType;
    private final DeploymentEnvironment environment;
    private final File targetDirectory;
    private final String remoteHost;
    private final int remotePort;
    private final String remoteUser;
    private final String remoteDestinationPath;
    private final String sshKeyPath;

    public DeploymentConfig(DeploymentTargetType targetType, DeploymentEnvironment environment, File targetDirectory) {
        this(targetType, environment, targetDirectory, null, 22, null, null, null);
    }

    public DeploymentConfig(DeploymentTargetType targetType,
                            DeploymentEnvironment environment,
                            File targetDirectory,
                            String remoteHost,
                            int remotePort,
                            String remoteUser,
                            String remoteDestinationPath,
                            String sshKeyPath) {
        this.targetType = targetType != null ? targetType : DeploymentTargetType.LOCAL_STAGING;
        this.environment = environment != null ? environment : DeploymentEnvironment.TEST;
        this.targetDirectory = targetDirectory;
        this.remoteHost = remoteHost;
        this.remotePort = remotePort > 0 ? remotePort : 22;
        this.remoteUser = remoteUser;
        this.remoteDestinationPath = remoteDestinationPath;
        this.sshKeyPath = sshKeyPath;
    }

    public static DeploymentConfig localStaging(File dir, DeploymentEnvironment env) {
        return new DeploymentConfig(DeploymentTargetType.LOCAL_STAGING, env, dir);
    }

    public static DeploymentConfig sshRemote(String host, int port, String user, String destPath, String keyPath, DeploymentEnvironment env) {
        return new DeploymentConfig(DeploymentTargetType.SSH_REMOTE, env, null, host, port, user, destPath, keyPath);
    }

    public DeploymentTargetType getTargetType() { return targetType; }
    public DeploymentEnvironment getEnvironment() { return environment; }
    public File getTargetDirectory() { return targetDirectory; }
    public String getRemoteHost() { return remoteHost; }
    public int getRemotePort() { return remotePort; }
    public String getRemoteUser() { return remoteUser; }
    public String getRemoteDestinationPath() { return remoteDestinationPath; }
    public String getSshKeyPath() { return sshKeyPath; }
}
