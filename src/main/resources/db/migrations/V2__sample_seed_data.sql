-- DevOpsHub Migration V2: Seed Data Matching Presentation Specifications
-- Projects, Pipelines, Stages, Historical Executions, Logs, and Diagnoses

-- 1. Seed Projects (Slide 9: Student Portal, AI Platform, E-Commerce, DevOpsHub)
INSERT INTO projects (id, name, description, repository_url, default_branch, project_type, status, created_at)
VALUES 
('proj-001', 'Student Portal', 'University academic portal for student course registrations and grades', 'https://github.com/vit/student-portal.git', 'main', 'Java', 'ACTIVE', '2026-10-01 10:00:00'),
('proj-002', 'AI Platform', 'Model training pipeline orchestration and inference microservice', 'https://github.com/vit/ai-platform.git', 'main', 'Python/Java', 'ACTIVE', '2026-10-02 11:30:00'),
('proj-003', 'E-Commerce', 'Full-stack retail checkout and inventory payment processing platform', 'https://github.com/vit/ecommerce-store.git', 'main', 'Java', 'ACTIVE', '2026-10-03 09:15:00'),
('proj-004', 'DevOpsHub', 'Intelligent CI/CD Management Platform control plane', 'https://github.com/vit/devopshub.git', 'main', 'Java', 'ACTIVE', '2026-10-04 14:00:00')
ON DUPLICATE KEY UPDATE name=VALUES(name);

-- 2. Seed Pipelines
INSERT INTO pipelines (id, project_id, name, description, execution_mode, is_active, created_at)
VALUES
('pipe-001', 'proj-001', 'StudentPortal-Release', 'Production build and integration test suite', 'SEQUENTIAL', TRUE, '2026-10-01 10:15:00'),
('pipe-002', 'proj-002', 'AIPlatform-CI', 'Model validation, unit tests, and packaging', 'SEQUENTIAL', TRUE, '2026-10-02 11:45:00'),
('pipe-003', 'proj-003', 'ECommerce-Delivery', 'Payment gateway verification and release pipeline', 'PARALLEL_FORK', TRUE, '2026-10-03 09:30:00'),
('pipe-004', 'proj-004', 'DevOpsHub-Core', 'DevOpsHub native desktop build, test, and verification pipeline', 'PARALLEL_FORK', TRUE, '2026-10-04 14:10:00')
ON DUPLICATE KEY UPDATE name=VALUES(name);

-- 3. Seed Pipeline Stages (Checkout, Build, Test, Security, Package, Deploy)
-- Pipeline 3 (E-Commerce) Stages:
INSERT INTO pipeline_stages (id, pipeline_id, name, stage_type, stage_order, command, timeout_seconds, is_enabled, parallel_group)
VALUES
('stage-ec-1', 'pipe-003', 'Checkout Source', 'CHECKOUT', 1, 'echo "Checking out git ref refs/heads/main..."', 120, TRUE, NULL),
('stage-ec-2', 'pipe-003', 'Compile Application', 'BUILD', 2, 'echo "Compiling Java modules with Maven..."', 300, TRUE, NULL),
('stage-ec-3', 'pipe-003', 'Unit & Integration Tests', 'TEST', 3, 'echo "Running LoginServiceTest and PaymentGatewayTest..." && exit 1', 300, TRUE, 'FORK_TEST'),
('stage-ec-4', 'pipe-003', 'SAST Security Scan', 'SECURITY', 4, 'echo "Scanning codebase for vulnerabilities..."', 300, TRUE, 'FORK_TEST'),
('stage-ec-5', 'pipe-003', 'Package JAR/Artifact', 'PACKAGE', 5, 'echo "Building production package archive..."', 180, TRUE, NULL),
('stage-ec-6', 'pipe-003', 'Deploy Staging', 'DEPLOY', 6, 'echo "Deploying to staging environment..."', 240, TRUE, NULL)
ON DUPLICATE KEY UPDATE name=VALUES(name);

-- Stage Dependencies for E-Commerce:
-- Stage 2 depends on 1. Stages 3 and 4 depend on 2 (Fork). Stage 5 depends on 3 and 4 (Sync/Join). Stage 6 depends on 5.
INSERT INTO stage_dependencies (id, stage_id, depends_on_stage_id)
VALUES
('dep-ec-1', 'stage-ec-2', 'stage-ec-1'),
('dep-ec-2', 'stage-ec-3', 'stage-ec-2'),
('dep-ec-3', 'stage-ec-4', 'stage-ec-2'),
('dep-ec-4', 'stage-ec-5', 'stage-ec-3'),
('dep-ec-5', 'stage-ec-5', 'stage-ec-4'),
('dep-ec-6', 'stage-ec-6', 'stage-ec-5')
ON DUPLICATE KEY UPDATE stage_id=VALUES(stage_id);

-- 4. Seed Historical Executions
-- Build #184 for E-Commerce (Slide 8: FAILED / 42.8S)
INSERT INTO executions (id, pipeline_id, project_id, execution_number, status, trigger_type, started_at, finished_at, duration_seconds, created_at)
VALUES
('exec-184', 'pipe-003', 'proj-003', 184, 'FAILED', 'MANUAL', '2026-10-06 10:41:26', '2026-10-06 10:42:09', 42.8, '2026-10-06 10:41:26'),
('exec-183', 'pipe-001', 'proj-001', 183, 'SUCCESS', 'WEBHOOK', '2026-10-06 09:12:00', '2026-10-06 09:12:38', 38.0, '2026-10-06 09:12:00'),
('exec-182', 'pipe-002', 'proj-002', 182, 'SUCCESS', 'SCHEDULED', '2026-10-06 08:00:00', '2026-10-06 08:00:45', 45.0, '2026-10-06 08:00:00')
ON DUPLICATE KEY UPDATE status=VALUES(status);

-- Stage executions for Build #184
INSERT INTO stage_executions (id, execution_id, stage_id, stage_name, stage_type, status, exit_code, started_at, finished_at, duration_seconds, error_message)
VALUES
('se-184-1', 'exec-184', 'stage-ec-1', 'Checkout Source', 'CHECKOUT', 'SUCCESS', 0, '2026-10-06 10:41:26', '2026-10-06 10:41:31', 5.0, NULL),
('se-184-2', 'exec-184', 'stage-ec-2', 'Compile Application', 'BUILD', 'SUCCESS', 0, '2026-10-06 10:41:31', '2026-10-06 10:41:50', 19.0, NULL),
('se-184-3', 'exec-184', 'stage-ec-3', 'Unit & Integration Tests', 'TEST', 'FAILED', 1, '2026-10-06 10:41:50', '2026-10-06 10:42:09', 18.8, 'AssertionError: expected HTTP 401 received HTTP 500 at LoginService'),
('se-184-4', 'exec-184', 'stage-ec-4', 'SAST Security Scan', 'SECURITY', 'SUCCESS', 0, '2026-10-06 10:41:50', '2026-10-06 10:42:05', 15.0, NULL),
('se-184-5', 'exec-184', 'stage-ec-5', 'Package JAR/Artifact', 'PACKAGE', 'SKIPPED', 0, NULL, NULL, 0.0, 'Prerequisite stage Unit & Integration Tests failed'),
('se-184-6', 'exec-184', 'stage-ec-6', 'Deploy Staging', 'DEPLOY', 'SKIPPED', 0, NULL, NULL, 0.0, 'Prerequisite stage Package JAR/Artifact skipped')
ON DUPLICATE KEY UPDATE status=VALUES(status);

-- 5. Seed Execution Logs for Build #184 (Slide 8 exact match)
INSERT INTO execution_logs (execution_id, stage_execution_id, stage_name, log_level, stream, message, timestamp)
VALUES
('exec-184', 'se-184-1', 'Checkout Source', 'INFO', 'STDOUT', '[10:41:26.012] Fetching origin/main from git repository...', '2026-10-06 10:41:26'),
('exec-184', 'se-184-1', 'Checkout Source', 'INFO', 'STDOUT', '[10:41:31.000] HEAD is now at 8f3c21a Merge pull request #42', '2026-10-06 10:41:31'),
('exec-184', 'se-184-2', 'Compile Application', 'INFO', 'STDOUT', '[10:41:31.100] Starting compilation with javac 21...', '2026-10-06 10:41:31'),
('exec-184', 'se-184-2', 'Compile Application', 'INFO', 'STDOUT', '[10:41:50.000] Build successful: 124 source files compiled', '2026-10-06 10:41:50'),
('exec-184', 'se-184-3', 'Unit & Integration Tests', 'INFO', 'STDOUT', '[10:41:50.100] Running test suite...', '2026-10-06 10:41:50'),
('exec-184', 'se-184-3', 'Unit & Integration Tests', 'INFO', 'STDOUT', '[10:42:08.114] TEST LoginServiceTest', '2026-10-06 10:42:08'),
('exec-184', 'se-184-3', 'Unit & Integration Tests', 'ERROR', 'STDERR', '[10:42:09.021] expected HTTP 401', '2026-10-06 10:42:09'),
('exec-184', 'se-184-3', 'Unit & Integration Tests', 'ERROR', 'STDERR', '[10:42:09.023] received HTTP 500', '2026-10-06 10:42:09'),
('exec-184', 'se-184-3', 'Unit & Integration Tests', 'ERROR', 'STDERR', '[10:42:09.026] exception at LoginService', '2026-10-06 10:42:09'),
('exec-184', 'se-184-3', 'Unit & Integration Tests', 'ERROR', 'STDERR', '[10:42:09.028] Tests run: 48, Failures: 1, Errors: 0, Skipped: 0', '2026-10-06 10:42:09')
ON DUPLICATE KEY UPDATE message=VALUES(message);

-- 6. Seed Failure Diagnosis for Build #184 (Slide 8 exact match)
INSERT INTO failure_diagnoses (id, execution_id, stage_execution_id, failed_stage, category, title, likely_cause, evidence_excerpt, suggested_action, rule_id, rule_version, analyzed_at)
VALUES
('diag-184', 'exec-184', 'se-184-3', 'Unit & Integration Tests', 'ASSERTION_FAILURE', 'HTTP Status Code Mismatch in Authentication Test', 'Likely exception-handling issue in authentication service handler', '[10:42:09.021] expected HTTP 401\n[10:42:09.023] received HTTP 500\n[10:42:09.026] exception at LoginService', 'Inspect LoginService error handling before rerun. Verify unauthorized requests return HTTP 401 rather than unhandled 500 exceptions.', 'RULE_HTTP_ASSERTION_01', '1.0', '2026-10-06 10:42:10')
ON DUPLICATE KEY UPDATE title=VALUES(title);
