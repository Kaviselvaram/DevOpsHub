# DevOpsHub Deployment Guide

## 1. Overview
DevOpsHub is packaged as a standalone desktop application. For enterprise and academic distribution, it can be distributed as:
1. Executable Uber JAR with bundled dependencies.
2. Native OS installers (macOS `.dmg` / `.app`, Windows `.exe` / `.msi`, Linux `.deb` / `.rpm`) generated using `jpackage`.

---

## 2. Packaging the Executable JAR
Run Maven package:
```bash
mvn clean package -DskipTests=true
```
The resulting executable JAR is produced at:
`target/devopshub-desktop-1.0.0.jar`

Run on any workstation with Java 21+ installed:
```bash
java -jar target/devopshub-desktop-1.0.0.jar
```

---

## 3. Creating Native Bundles with `jpackage`
Java 21 provides the `jpackage` utility to produce native installers with a bundled, minimal Java runtime.

### macOS DMG Bundle Example:
```bash
jpackage \
  --input target \
  --name "DevOpsHub" \
  --main-jar devopshub-desktop-1.0.0.jar \
  --main-class com.devopshub.app.Launcher \
  --type dmg \
  --app-version 1.0.0 \
  --vendor "DevOpsHub Team"
```

### Windows MSI Bundle Example:
```bash
jpackage \
  --input target \
  --name "DevOpsHub" \
  --main-jar devopshub-desktop-1.0.0.jar \
  --main-class com.devopshub.app.Launcher \
  --type msi \
  --app-version 1.0.0 \
  --win-shortcut \
  --win-menu
```

---

## 4. Production Database Configuration
For multi-user team deployments:
1. Host a managed MySQL 8.0 instance (AWS RDS, Google Cloud SQL, or on-premise high-availability MySQL cluster).
2. Configure SSL connection strings in the client `.env`:
   ```properties
   DB_URL=jdbc:mysql://db.internal.devopshub.com:3306/devopshub?useSSL=true&requireSSL=true
   ```
3. Set appropriate database user privileges and connection pool limits (`DB_MAX_POOL_SIZE=20`).
