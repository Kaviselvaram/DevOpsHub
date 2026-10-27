-- DevOpsHub Schema Migration V4: Durable Schedules, Security Findings & Deployment Audit
-- Enables durable persistence for automated pipeline schedules, security scan findings, and deployment history.

CREATE TABLE IF NOT EXISTS pipeline_schedules (
    id VARCHAR(100) PRIMARY KEY,
    pipeline_id VARCHAR(100) NOT NULL UNIQUE,
    interval_seconds BIGINT NOT NULL,
    is_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    execution_count INT NOT NULL DEFAULT 0,
    last_execution_id VARCHAR(100) NULL,
    last_run_at TIMESTAMP NULL,
    next_run_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (pipeline_id) REFERENCES pipelines(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS security_findings (
    id VARCHAR(100) PRIMARY KEY,
    execution_id VARCHAR(100) NOT NULL,
    stage_execution_id VARCHAR(100) NULL,
    rule_id VARCHAR(100) NOT NULL,
    severity VARCHAR(20) NOT NULL,
    category VARCHAR(50) NOT NULL,
    title VARCHAR(255) NOT NULL,
    file_path VARCHAR(500) NULL,
    line_number INT NULL,
    description TEXT NULL,
    remediation TEXT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (execution_id) REFERENCES executions(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS deployment_records (
    id VARCHAR(100) PRIMARY KEY,
    execution_id VARCHAR(100) NOT NULL,
    pipeline_id VARCHAR(100) NOT NULL,
    target_type VARCHAR(50) NOT NULL,
    environment VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL,
    source_artifact VARCHAR(500) NOT NULL,
    target_destination VARCHAR(500) NOT NULL,
    sha256_checksum VARCHAR(64) NOT NULL,
    duration_seconds DOUBLE NOT NULL,
    deployed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    details TEXT NULL,
    FOREIGN KEY (execution_id) REFERENCES executions(id) ON DELETE CASCADE,
    FOREIGN KEY (pipeline_id) REFERENCES pipelines(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
