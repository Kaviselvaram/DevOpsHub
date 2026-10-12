# DevOpsHub Local Development Guide

## 1. IDE Setup (VS Code & IntelliJ IDEA)

### Visual Studio Code
1. Install the **Extension Pack for Java** (Microsoft) and **Maven for Java**.
2. Open the project root (`javacicd`).
3. VS Code will automatically detect `pom.xml` and download dependencies.
4. Open the Maven sidebar and run `Plugins` -> `javafx` -> `javafx:run`.
5. Alternatively, launch tests directly from the Testing panel (`Cmd+Shift+P` -> `Java: Run Tests`).

### IntelliJ IDEA
1. Open the project directory (`File -> Open -> javacicd`).
2. Select `pom.xml` as a Maven Project.
3. Set Project SDK to Java 21+.
4. Create a Run/Debug configuration for `com.devopshub.app.Launcher` or execute Maven goal `javafx:run`.

---

## 2. Common Maven Tasks

| Task | Command |
|---|---|
| Compile source files | `mvn compile` |
| Run all unit and integration tests | `mvn test` |
| Package application JAR | `mvn package` |
| Launch JavaFX application | `mvn javafx:run` |
| Clean build artifacts | `mvn clean` |
| Run isolated test class | `mvn test -Dtest=FailureRuleEngineTest` |

---

## 3. Database Reset for Development
To reset the development database to a pristine seed state:
```bash
mysql -u root -proot devopshub < database/migrations/V1__initial_schema.sql
mysql -u root -proot devopshub < database/migrations/V2__sample_seed_data.sql
```
Or simply restart the Docker container:
```bash
docker restart devopshub-mysql
```
