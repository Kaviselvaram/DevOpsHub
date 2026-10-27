-- DevOpsHub Migration V3: Seed Reference Projects Matching Visual Specification Screen 2
-- "Choose a project to work": Promotion, Store, Gold store, HiLike, InStore

INSERT INTO projects (id, name, description, repository_url, default_branch, project_type, status, created_at)
VALUES
('proj-ref-1', 'Promotion', 'Mobile ad campaign optimization and discount marketing engine', 'https://github.com/vit/promotion-campaign.git', 'main', 'Java/Spring', 'ACTIVE', '2026-10-10 14:00:00'),
('proj-ref-2', 'Store', 'Cross-platform customer mobile storefront and product catalogue', 'https://github.com/vit/retail-storefront.git', 'main', 'Java', 'ACTIVE', '2026-10-09 11:20:00'),
('proj-ref-3', 'Gold store', 'High-value jewelry eCommerce marketplace with secure payments', 'https://github.com/vit/gold-store.git', 'main', 'Java', 'ACTIVE', '2026-10-04 09:30:00'),
('proj-ref-4', 'HiLike', 'Real-time social engagement and audience voting microservice', 'https://github.com/vit/hilike-social.git', 'main', 'Java', 'ACTIVE', '2026-10-02 16:45:00'),
('proj-ref-5', 'InStore', 'Point-of-sale hardware integration and branch inventory hub', 'https://github.com/vit/instore-pos.git', 'main', 'Java', 'ACTIVE', '2026-09-25 10:10:00')
ON DUPLICATE KEY UPDATE name=VALUES(name);

-- Seed Pipelines for Reference Projects
INSERT INTO pipelines (id, project_id, name, description, execution_mode, is_active, created_at)
VALUES
('pipe-ref-1', 'proj-ref-1', 'Promotion-Release-CI', 'Ad creative validation and automated deployment', 'SEQUENTIAL', TRUE, '2026-10-10 14:15:00'),
('pipe-ref-2', 'proj-ref-2', 'Storefront-Main-Build', 'Mobile app backend packaging and regression tests', 'SEQUENTIAL', TRUE, '2026-10-09 11:35:00'),
('pipe-ref-3', 'proj-ref-3', 'GoldStore-Payment-Deploy', 'PCI-compliant checkout pipeline with Sonar analysis', 'PARALLEL_FORK', TRUE, '2026-10-04 09:45:00')
ON DUPLICATE KEY UPDATE name=VALUES(name);

-- Seed Reference Screen 1 Pipeline Stages for Promotion Pipeline
INSERT INTO pipeline_stages (id, pipeline_id, name, stage_type, stage_order, command, timeout_seconds, is_enabled, parallel_group)
VALUES
('stage-ref-1', 'pipe-ref-1', 'Make layout', 'CHECKOUT', 1, 'echo "Validating layout designs and assets..."', 120, TRUE, NULL),
('stage-ref-2', 'pipe-ref-1', 'Prepare images', 'BUILD', 2, 'echo "Optimizing campaign images and banners..."', 180, TRUE, NULL),
('stage-ref-3', 'pipe-ref-1', 'Make a promotion plan', 'TEST', 3, 'echo "Executing promotional strategy rules..."', 240, TRUE, NULL),
('stage-ref-4', 'pipe-ref-1', 'Advertising', 'SECURITY', 4, 'echo "Targeted audience compliance scan..."', 180, TRUE, NULL),
('stage-ref-5', 'pipe-ref-1', 'Coding', 'PACKAGE', 5, 'echo "Compiling Java campaign discount service..."', 300, TRUE, NULL),
('stage-ref-6', 'pipe-ref-1', 'Payment', 'DEPLOY', 6, 'echo "Deploying checkout voucher microservice..."', 240, TRUE, NULL)
ON DUPLICATE KEY UPDATE name=VALUES(name);
