package com.devopshub.deployment;

import java.io.File;

/**
 * Interface contract for authentic deployment providers.
 */
public interface DeploymentProvider {

    /**
     * Identifies the deployment target type handled by this provider.
     */
    DeploymentTargetType getTargetType();

    /**
     * Determines whether the deployment provider target environment is configured, reachable, and operational.
     */
    boolean isAvailable();

    /**
     * Deploys an artifact according to the supplied configuration.
     *
     * @param sourceArtifact Local built artifact file
     * @param config Target destination and environment settings
     * @return DeploymentResult containing verified outcome, hash, and path
     */
    DeploymentService.DeploymentResult deploy(File sourceArtifact, DeploymentConfig config);

    /**
     * Indicates whether this provider supports rolling back to a previously deployed version.
     */
    boolean supportsRollback();

    /**
     * Executes a rollback operation reverting to the previously verified artifact.
     *
     * @param config Deployment configuration
     * @return Result of the rollback operation
     */
    DeploymentService.DeploymentResult rollback(DeploymentConfig config);
}
