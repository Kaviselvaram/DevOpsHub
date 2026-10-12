package com.devopshub.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AppConfigTest {

    @Test
    @DisplayName("AppConfig loads default parameters correctly")
    void testDefaultConfiguration() {
        AppConfig config = AppConfig.getInstance();
        assertNotNull(config.getDbHost());
        assertTrue(config.getDbPort() > 0);
        assertNotNull(config.getDbName());
        assertNotNull(config.getDbUser());
        assertNotNull(config.getJdbcUrl());
        assertTrue(config.getJdbcUrl().startsWith("jdbc:mysql://"));
        assertTrue(config.getMaxConcurrentPipelines() > 0);
        assertTrue(config.getDefaultTimeoutSeconds() > 0);
        assertNotNull(config.getWorkspaceDir());
    }

    @Test
    @DisplayName("AppConfig handles default fallback when key is missing")
    void testMissingKeyFallback() {
        AppConfig config = AppConfig.getInstance();
        assertEquals("defaultVal", config.get("NON_EXISTENT_KEY", "defaultVal"));
        assertEquals(42, config.getInt("NON_EXISTENT_INT", 42));
    }
}
