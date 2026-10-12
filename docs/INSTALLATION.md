# DevOpsHub Installation Guide

## 1. System Requirements
- **Operating System:** macOS (Apple Silicon or Intel), Windows 10/11, or Linux (Ubuntu 20.04+).
- **JDK:** Java 21 LTS or newer (tested on Java 25 / OpenJDK 21).
- **Build Tool:** Apache Maven 3.8.0+.
- **Database:** MySQL 8.0+ (native or Docker).
- **Memory:** Minimum 4 GB RAM (8 GB recommended).
- **Storage:** Minimum 500 MB free space.

---

## 2. Step-by-Step Installation

### Step 1: Clone Repository
```bash
git clone https://github.com/your-org/javacicd.git
cd javacicd
```

### Step 2: Configure Environment
Copy `.env.example` to `.env`:
```bash
cp .env.example .env
```
Edit `.env` if your MySQL password or host differs from the defaults (`root` / `root`).

### Step 3: Launch MySQL Database
Using Docker:
```bash
./scripts/run-docker.sh
```
Or start your local MySQL service:
```bash
mysql.server start
```

### Step 4: Build Application with Maven
Compile and package the desktop client:
```bash
mvn clean package -DskipTests=false
```
Expected output:
```text
[INFO] BUILD SUCCESS
[INFO] Total time: ...
```

### Step 5: Launch DevOpsHub
Start the JavaFX application:
```bash
mvn javafx:run
```
Or run the packaged JAR:
```bash
java -jar target/devopshub-desktop-1.0.0.jar
```
DevOpsHub will automatically initialize the database schema and present the desktop interface.
