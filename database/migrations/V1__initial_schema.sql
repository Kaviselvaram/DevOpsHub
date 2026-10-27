-- DevOpsHub Migration V1: Initial Normalized Schema
-- Author: DevOpsHub Engineering Team

-- 1. Projects Table
CREATE TABLE IF NOT EXISTS projects (
    id VARCHAR(100) PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    description TEXT,
    repository_url VARCHAR(255) NOT NULL,
    default_branch VARCHAR(50) DEFAULT 'main',
    project_type VARCHAR(50) DEFAULT 'Java',
    status VARCHAR(20) DEFAULT 'ACTIVE',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_project_status (status),
    INDEX idx_project_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 2. Pipelines Table
CREATE TABLE IF NOT EXISTS pipelines (
    id VARCHAR(100) PRIMARY KEY,
    project_id VARCHAR(100) NOT NULL,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    execution_mode VARCHAR(20) DEFAULT 'SEQUENTIAL',
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE,
    INDEX idx_pipeline_project (project_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 3. Pipeline Stages Table
CREATE TABLE IF NOT EXISTS pipeline_stages (
    id VARCHAR(100) PRIMARY KEY,
    pipeline_id VARCHAR(100) NOT NULL,
    name VARCHAR(100) NOT NULL,
    stage_type VARCHAR(50) NOT NULL,
    stage_order INT NOT NULL,
    command TEXT NOT NULL,
    timeout_seconds INT DEFAULT 300,
    is_enabled BOOLEAN DEFAULT TRUE,
    parallel_group VARCHAR(50) DEFAULT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (pipeline_id) REFERENCES pipelines(id) ON DELETE CASCADE,
    INDEX idx_stage_order (pipeline_id, stage_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 4. Stage Dependencies Table
CREATE TABLE IF NOT EXISTS stage_dependencies (
    id VARCHAR(100) PRIMARY KEY,
    stage_id VARCHAR(100) NOT NULL,
    depends_on_stage_id VARCHAR(100) NOT NULL,
    FOREIGN KEY (stage_id) REFERENCES pipeline_stages(id) ON DELETE CASCADE,
    FOREIGN KEY (depends_on_stage_id) REFERENCES pipeline_stages(id) ON DELETE CASCADE,
    UNIQUE KEY uk_stage_dep (stage_id, depends_on_stage_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 5. Executions Table
CREATE TABLE IF NOT EXISTS executions (
    id VARCHAR(100) PRIMARY KEY,
    pipeline_id VARCHAR(100) NOT NULL,
    project_id VARCHAR(100) NOT NULL,
    execution_number INT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'QUEUED',
    trigger_type VARCHAR(50) DEFAULT 'MANUAL',
    started_at TIMESTAMP NULL DEFAULT NULL,
    finished_at TIMESTAMP NULL DEFAULT NULL,
    duration_seconds DOUBLE DEFAULT 0.0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (pipeline_id) REFERENCES pipelines(id) ON DELETE CASCADE,
    FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE,
    INDEX idx_execution_status (status),
    INDEX idx_execution_created (created_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 6. Stage Executions Table
CREATE TABLE IF NOT EXISTS stage_executions (
    id VARCHAR(100) PRIMARY KEY,
    execution_id VARCHAR(100) NOT NULL,
    stage_id VARCHAR(100) DEFAULT NULL,
    stage_name VARCHAR(100) NOT NULL,
    stage_type VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'QUEUED',
    exit_code INT DEFAULT 0,
    started_at TIMESTAMP NULL DEFAULT NULL,
    finished_at TIMESTAMP NULL DEFAULT NULL,
    duration_seconds DOUBLE DEFAULT 0.0,
    error_message TEXT,
    FOREIGN KEY (execution_id) REFERENCES executions(id) ON DELETE CASCADE,
    FOREIGN KEY (stage_id) REFERENCES pipeline_stages(id) ON DELETE SET NULL,
    INDEX idx_stage_exec_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 7. Execution Logs Table
CREATE TABLE IF NOT EXISTS execution_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    execution_id VARCHAR(100) NOT NULL,
    stage_execution_id VARCHAR(100) DEFAULT NULL,
    stage_name VARCHAR(100),
    log_level VARCHAR(10) DEFAULT 'INFO',
    stream VARCHAR(10) DEFAULT 'STDOUT',
    message TEXT NOT NULL,
    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (execution_id) REFERENCES executions(id) ON DELETE CASCADE,
    INDEX idx_logs_exec (execution_id, timestamp)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 8. Failure Diagnoses Table
CREATE TABLE IF NOT EXISTS failure_diagnoses (
    id VARCHAR(100) PRIMARY KEY,
    execution_id VARCHAR(100) NOT NULL,
    stage_execution_id VARCHAR(100) DEFAULT NULL,
    failed_stage VARCHAR(100) NOT NULL,
    category VARCHAR(50) NOT NULL,
    title VARCHAR(255) NOT NULL,
    likely_cause TEXT NOT NULL,
    evidence_excerpt TEXT NOT NULL,
    suggested_action TEXT NOT NULL,
    rule_id VARCHAR(50) NOT NULL,
    rule_version VARCHAR(20) DEFAULT '1.0',
    analyzed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (execution_id) REFERENCES executions(id) ON DELETE CASCADE,
    INDEX idx_diagnosis_category (category)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
