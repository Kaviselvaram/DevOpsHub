# DevOpsHub Configuration Guide

## 1. Overview
DevOpsHub uses a dual-layer configuration hierarchy:
1. Environment variables and `.env` file for runtime credentials and infrastructure endpoints.
2. Centralized application defaults in `com.devopshub.config.AppConfig`.

---

## 2. Environment Variables & Properties

| Key | Default Value | Description |
|---|---|---|
| `APP_NAME` | `DevOpsHub` | Display name of the application |
| `APP_ENV` | `development` | Environment mode (`development`, `test`, `production`) |
| `DB_URL` | `jdbc:mysql://localhost:3306/devopshub...` | JDBC URL for MySQL 8.0 connection |
| `DB_USER` | `root` | MySQL database username |
| `DB_PASSWORD` | `root` | MySQL database password |
| `DB_MAX_POOL_SIZE` | `10` | HikariCP maximum connection pool capacity |
| `DB_MIN_IDLE` | `2` | HikariCP minimum idle connections |
| `EXECUTION_MAX_CONCURRENT` | `4` | Maximum concurrent top-level pipeline executions |
| `EXECUTION_DEFAULT_TIMEOUT`| `300` | Default timeout in seconds for a pipeline stage |
| `LOG_LEVEL` | `INFO` | Console and application logging verbosity |

---

## 3. Sample Configuration File (`.env`)
```properties
# DevOpsHub Environment Configuration
APP_NAME=DevOpsHub
APP_ENV=development

# MySQL 8.0 Connection
DB_URL=jdbc:mysql://localhost:3306/devopshub?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC
DB_USER=root
DB_PASSWORD=root
DB_MAX_POOL_SIZE=10
DB_MIN_IDLE=2

# Concurrency & Execution
EXECUTION_MAX_CONCURRENT=4
EXECUTION_DEFAULT_TIMEOUT=300
```
*(Always ensure `.env` is listed in `.gitignore` to prevent credential leakage).*
