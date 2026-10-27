package com.devopshub.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Centralized application configuration.
 * Loads configuration from .env file, environment variables, or defaults.
 */
public class AppConfig {
    private static final Logger logger = LoggerFactory.getLogger(AppConfig.class);
    private static final AppConfig INSTANCE = new AppConfig();

    private final Map<String, String> configMap = new HashMap<>();

    private AppConfig() {
        loadDefaults();
        loadDotEnv();
        loadSystemEnvironment();
        loadSystemProperties();
    }

    public static AppConfig getInstance() {
        return INSTANCE;
    }

    private void loadDefaults() {
        configMap.put("DB_HOST", "localhost");
        configMap.put("DB_PORT", "3306");
        configMap.put("DB_NAME", "devopshub");
        configMap.put("DB_USER", "root");
        configMap.put("DB_PASSWORD", "root");
        configMap.put("DB_POOL_SIZE", "10");
        configMap.put("APP_NAME", "DevOpsHub");
        configMap.put("APP_ENV", "development");
        configMap.put("APP_LOG_LEVEL", "INFO");
        configMap.put("EXECUTION_MAX_CONCURRENT_PIPELINES", "4");
        configMap.put("EXECUTION_DEFAULT_TIMEOUT_SECONDS", "300");
        configMap.put("EXECUTION_WORKSPACE_DIR", "./workspaces");
        configMap.put("GIT_TIMEOUT_SECONDS", "300");
        configMap.put("ALLOW_REMOTE_REPOS_AS_TRUSTED", "false");
        configMap.put("ALLOW_HOST_EXECUTION_FOR_TRUSTED", "true");
    }

    private void loadDotEnv() {
        File envFile = new File(".env");
        if (!envFile.exists()) {
            logger.info("No .env file found in working directory, using defaults & system environment.");
            return;
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(envFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                int eqIdx = line.indexOf('=');
                if (eqIdx > 0) {
                    String key = line.substring(0, eqIdx).trim();
                    String val = line.substring(eqIdx + 1).trim();
                    if (val.startsWith("\"") && val.endsWith("\"") && val.length() >= 2) {
                        val = val.substring(1, val.length() - 1);
                    }
                    configMap.put(key, val);
                }
            }
            logger.info("Loaded configuration from .env file.");
        } catch (IOException e) {
            logger.warn("Could not read .env file: {}", e.getMessage());
        }
    }

    private void loadSystemEnvironment() {
        for (Map.Entry<String, String> entry : System.getenv().entrySet()) {
            configMap.put(entry.getKey(), entry.getValue());
        }
    }

    private void loadSystemProperties() {
        for (String key : System.getProperties().stringPropertyNames()) {
            configMap.put(key, System.getProperty(key));
        }
    }

    public String get(String key) {
        return configMap.get(key);
    }

    public String get(String key, String defaultValue) {
        return configMap.getOrDefault(key, defaultValue);
    }

    public int getInt(String key, int defaultValue) {
        String val = configMap.get(key);
        if (val == null) return defaultValue;
        try {
            return Integer.parseInt(val);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public String getDbHost() { return get("DB_HOST", "localhost"); }
    public int getDbPort() { return getInt("DB_PORT", 3306); }
    public String getDbName() { return get("DB_NAME", "devopshub"); }
    public String getDbUser() { return get("DB_USER", "root"); }
    public String getDbPassword() { return get("DB_PASSWORD", "root"); }
    public int getDbPoolSize() { return getInt("DB_POOL_SIZE", 10); }

    public String getJdbcUrl() {
        return String.format("jdbc:mysql://%s:%d/%s?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC",
                getDbHost(), getDbPort(), getDbName());
    }

    public int getMaxConcurrentPipelines() {
        return getInt("EXECUTION_MAX_CONCURRENT_PIPELINES", 4);
    }

    public int getDefaultTimeoutSeconds() {
        return getInt("EXECUTION_DEFAULT_TIMEOUT_SECONDS", 300);
    }

    public String getWorkspaceDir() {
        return get("EXECUTION_WORKSPACE_DIR", "./workspaces");
    }

    public int getGitTimeoutSeconds() {
        return getInt("GIT_TIMEOUT_SECONDS", 300);
    }

    public void set(String key, String value) {
        configMap.put(key, value);
    }

    public void setMaxConcurrentPipelines(int count) {
        set("EXECUTION_MAX_CONCURRENT_PIPELINES", String.valueOf(count));
    }

    public void setDefaultTimeoutSeconds(int seconds) {
        set("EXECUTION_DEFAULT_TIMEOUT_SECONDS", String.valueOf(seconds));
    }

    public void setGitTimeoutSeconds(int seconds) {
        set("GIT_TIMEOUT_SECONDS", String.valueOf(seconds));
    }
}
