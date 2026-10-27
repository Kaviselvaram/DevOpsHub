-- ============================================================================
-- DevOpsHub Database Inspection Script (Read-Only)
-- Purpose: Inspect database identity, schema, tables, keys, indexes,
--          accurate row counts, and recent application records.
-- Engine: MySQL 8.0+
-- Database: devopshub
-- Note: Contains only SELECT statements; does not modify any data or schema.
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 1. DATABASE & CONNECTION IDENTITY
-- ----------------------------------------------------------------------------
SELECT 
    DATABASE() AS current_database,
    CURRENT_USER() AS connected_user,
    VERSION() AS mysql_version,
    @@hostname AS host_name,
    @@port AS server_port,
    NOW() AS inspection_time;

-- ----------------------------------------------------------------------------
-- 2. ALL TABLES & STORAGE ENGINES IN DEVOPSHUB
-- ----------------------------------------------------------------------------
SELECT 
    table_name,
    table_type,
    engine,
    table_rows AS estimated_row_count,
    ROUND(((data_length + index_length) / 1024), 2) AS total_size_kb,
    create_time,
    update_time,
    table_collation
FROM information_schema.tables 
WHERE table_schema = DATABASE()
ORDER BY table_name ASC;

-- ----------------------------------------------------------------------------
-- 3. SCHEMA COLUMNS AND DATA TYPES
-- ----------------------------------------------------------------------------
SELECT 
    table_name,
    ordinal_position,
    column_name,
    column_type,
    is_nullable,
    column_key,
    column_default,
    extra
FROM information_schema.columns 
WHERE table_schema = DATABASE()
ORDER BY table_name ASC, ordinal_position ASC;

-- ----------------------------------------------------------------------------
-- 4. PRIMARY KEYS AND FOREIGN KEYS
-- ----------------------------------------------------------------------------
SELECT 
    kcu.table_name,
    kcu.constraint_name,
    tc.constraint_type,
    kcu.column_name,
    kcu.referenced_table_name,
    kcu.referenced_column_name
FROM information_schema.key_column_usage kcu
JOIN information_schema.table_constraints tc 
  ON kcu.constraint_name = tc.constraint_name 
 AND kcu.table_schema = tc.table_schema 
 AND kcu.table_name = tc.table_name
WHERE kcu.table_schema = DATABASE()
ORDER BY kcu.table_name ASC, tc.constraint_type DESC, kcu.ordinal_position ASC;

-- ----------------------------------------------------------------------------
-- 5. INDEXES ACROSS ALL TABLES
-- ----------------------------------------------------------------------------
SELECT 
    table_name,
    index_name,
    non_unique,
    seq_in_index,
    column_name,
    cardinality,
    index_type
FROM information_schema.statistics 
WHERE table_schema = DATABASE()
ORDER BY table_name ASC, index_name ASC, seq_in_index ASC;

-- ----------------------------------------------------------------------------
-- 6. EXACT TABLE ROW COUNTS (ALL 13 APPLICATION TABLES)
-- ----------------------------------------------------------------------------
SELECT 'projects' AS table_name, COUNT(*) AS row_count FROM projects
UNION ALL SELECT 'pipelines', COUNT(*) FROM pipelines
UNION ALL SELECT 'pipeline_stages', COUNT(*) FROM pipeline_stages
UNION ALL SELECT 'stage_dependencies', COUNT(*) FROM stage_dependencies
UNION ALL SELECT 'pipeline_schedules', COUNT(*) FROM pipeline_schedules
UNION ALL SELECT 'executions', COUNT(*) FROM executions
UNION ALL SELECT 'stage_executions', COUNT(*) FROM stage_executions
UNION ALL SELECT 'execution_logs', COUNT(*) FROM execution_logs
UNION ALL SELECT 'execution_events', COUNT(*) FROM execution_events
UNION ALL SELECT 'failure_diagnoses', COUNT(*) FROM failure_diagnoses
UNION ALL SELECT 'security_findings', COUNT(*) FROM security_findings
UNION ALL SELECT 'deployment_records', COUNT(*) FROM deployment_records
UNION ALL SELECT 'schema_version', COUNT(*) FROM schema_version
ORDER BY row_count DESC;

-- ----------------------------------------------------------------------------
-- 7. RECENT APPLICATION RECORDS
-- ----------------------------------------------------------------------------

-- Applied Schema Migrations
SELECT version, applied_at 
FROM schema_version 
ORDER BY applied_at ASC;

-- Recent Projects (Latest 10)
SELECT id, name, project_type, status, repository_url, default_branch, created_at, updated_at
FROM projects 
ORDER BY updated_at DESC, created_at DESC 
LIMIT 10;

-- Recent Pipelines (Latest 10)
SELECT id, project_id, name, execution_mode, is_active, created_at, updated_at 
FROM pipelines 
ORDER BY updated_at DESC, created_at DESC 
LIMIT 10;

-- Pipeline Stages (Sample of 20)
SELECT id, pipeline_id, name, stage_type, stage_order, timeout_seconds, is_enabled, parallel_group, created_at 
FROM pipeline_stages 
ORDER BY pipeline_id ASC, stage_order ASC 
LIMIT 20;

-- Recent Executions (Latest 10)
SELECT id, pipeline_id, project_id, execution_number, status, trigger_type, started_at, finished_at, duration_seconds, commit_hash, artifact_path, created_at 
FROM executions 
ORDER BY created_at DESC 
LIMIT 10;

-- Recent Stage Executions (Latest 15)
SELECT id, execution_id, stage_id, stage_name, stage_type, status, exit_code, duration_seconds, started_at, finished_at, error_message 
FROM stage_executions 
ORDER BY started_at DESC, id DESC 
LIMIT 15;

-- Recent Execution Logs (Latest 20)
SELECT id, execution_id, stage_name, log_level, stream, message, timestamp 
FROM execution_logs 
ORDER BY timestamp DESC, id DESC 
LIMIT 20;

-- Recent Real-time Execution Events (Latest 20)
SELECT id, execution_id, sequence_number, event_type, status_transition, stage_name, duration_seconds, exit_code, is_authoritative, timestamp 
FROM execution_events 
ORDER BY sequence_number DESC, timestamp DESC 
LIMIT 20;

-- Recent Failure Diagnoses (Latest 10)
SELECT id, execution_id, failed_stage, category, title, rule_id, rule_version, analyzed_at 
FROM failure_diagnoses 
ORDER BY analyzed_at DESC 
LIMIT 10;

-- Recent Deployment Records (Latest 10)
SELECT id, execution_id, pipeline_id, target_type, environment, status, source_artifact, target_destination, sha256_checksum, duration_seconds, deployed_at 
FROM deployment_records 
ORDER BY deployed_at DESC 
LIMIT 10;

-- Pipeline Schedules (if configured)
SELECT id, pipeline_id, interval_seconds, is_enabled, execution_count, last_run_at, next_run_at 
FROM pipeline_schedules 
LIMIT 10;
