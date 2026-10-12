# DevOpsHub Database Setup & Migration Guide

## 1. Prerequisites
- MySQL 8.0+ server installed locally or accessible via Docker.
- Port `3306` available.

---

## 2. Docker Setup (Recommended)
You can launch an isolated MySQL 8.0 instance using Docker with the provided helper script:

```bash
./scripts/run-docker.sh
```

Or execute the Docker command directly:
```bash
docker run -d \
  --name devopshub-mysql \
  -e MYSQL_ROOT_PASSWORD=root \
  -e MYSQL_DATABASE=devopshub \
  -p 3306:3306 \
  mysql:8.0
```

Verify the container is healthy:
```bash
docker ps --filter "name=devopshub-mysql"
```

---

## 3. Local Native MySQL Setup
If running native MySQL:

1. Log into MySQL as root:
   ```bash
   mysql -u root -p
   ```
2. Create the database:
   ```sql
   CREATE DATABASE IF NOT EXISTS devopshub CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
   ```
3. (Optional) Create a dedicated user:
   ```sql
   CREATE USER IF NOT EXISTS 'devopshub'@'localhost' IDENTIFIED BY 'devopshub123';
   GRANT ALL PRIVILEGES ON devopshub.* TO 'devopshub'@'localhost';
   FLUSH PRIVILEGES;
   ```

---

## 4. Environment Configuration
Copy the template `.env.example` to `.env`:
```bash
cp .env.example .env
```

Ensure the configuration matches your MySQL credentials:
```properties
DB_URL=jdbc:mysql://localhost:3306/devopshub?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC
DB_USER=root
DB_PASSWORD=root
DB_MAX_POOL_SIZE=10
```

---

## 5. Automatic Migrations on Startup
DevOpsHub runs database migrations automatically upon application startup through `DatabaseConfig.runMigrations()`. It reads and executes:
- `database/migrations/V1__initial_schema.sql` (Creates 8 core tables with indexes and foreign keys)
- `database/migrations/V2__sample_seed_data.sql` (Inserts demonstration projects, pipelines, executions, and diagnostics)

To manually apply the schema via command line:
```bash
mysql -u root -p devopshub < database/migrations/V1__initial_schema.sql
mysql -u root -p devopshub < database/migrations/V2__sample_seed_data.sql
```

---

## 6. Verifying Schema Integrity
Verify that all tables exist and are populated:
```bash
mysql -u root -p -e "USE devopshub; SHOW TABLES;"
mysql -u root -p -e "USE devopshub; SELECT id, name, status FROM projects;"
```
Expected tables:
- `projects`
- `pipelines`
- `pipeline_stages`
- `stage_dependencies`
- `executions`
- `stage_executions`
- `execution_logs`
- `failure_diagnoses`
