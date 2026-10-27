package com.devopshub.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SecretMasker Log Redaction Unit Tests")
class SecretMaskerTest {

    @Test
    @DisplayName("Should redact basic auth passwords in HTTP/HTTPS URLs")
    void testMaskUrlCredentials() {
        String log = "Fetching git repository from https://user_deploy:ghp_SuperSecretToken12345@github.com/myorg/myrepo.git now";
        String masked = SecretMasker.mask(log);

        assertFalse(masked.contains("ghp_SuperSecretToken12345"), "Sensitive token must not appear in masked string");
        assertTrue(masked.contains("https://user_deploy:***@github.com/myorg/myrepo.git"));
    }

    @Test
    @DisplayName("Should redact password and secret key-value pairs")
    void testMaskKeyValueTokens() {
        String log = "Config loaded: password=SuperSecretPassword99; token: ghp_abc12345; auth_token = 'very_secret_token'";
        String masked = SecretMasker.mask(log);

        assertFalse(masked.contains("SuperSecretPassword99"));
        assertFalse(masked.contains("ghp_abc12345"));
        assertFalse(masked.contains("very_secret_token"));
        assertTrue(masked.contains("password=***"));
        assertTrue(masked.contains("token: ***"));
        assertTrue(masked.contains("auth_token = '***'"));
    }

    @Test
    @DisplayName("Should redact full RSA/EC private keys")
    void testMaskPrivateKey() {
        String log = "Deployment Key:\n-----BEGIN RSA PRIVATE KEY-----\nMIIEowIBAAKCAQEA0Yw/xyz123...\n-----END RSA PRIVATE KEY-----\nReady for SSH deploy.";
        String masked = SecretMasker.mask(log);

        assertFalse(masked.contains("MIIEowIBAAKCAQEA0Yw/xyz123"));
        assertTrue(masked.contains("[REDACTED PRIVATE KEY]"));
        assertTrue(masked.contains("Ready for SSH deploy."));
    }

    @Test
    @DisplayName("Should handle null and empty strings safely")
    void testNullAndEmpty() {
        assertNull(SecretMasker.mask(null));
        assertEquals("", SecretMasker.mask(""));
        assertEquals("Standard harmless output", SecretMasker.mask("Standard harmless output"));
    }
}
