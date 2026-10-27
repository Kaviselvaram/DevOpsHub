package com.devopshub.webhook;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SEC-02: Webhook HMAC-SHA256 Cryptographic Verification & Replay Protection Tests")
class WebhookHmacVerificationTest {

    private static final String SECRET_KEY = "my-super-secret-key-12345";
    private static final String ROTATION_KEY = "my-rotated-secret-key-67890";

    @Test
    @DisplayName("Valid HMAC-SHA256 signature over raw payload must verify successfully")
    void testValidHmacSignatureVerification() {
        byte[] payload = "{\"pipelineId\": \"pipe-123\", \"ref\": \"refs/heads/main\"}".getBytes(StandardCharsets.UTF_8);

        String hexDigest = WebhookServerService.calculateHmacSha256(payload, SECRET_KEY);
        assertNotNull(hexDigest);
        assertEquals(64, hexDigest.length(), "HMAC-SHA256 hex digest must be 64 characters");

        // Verify with sha256= prefix (GitHub standard)
        assertTrue(WebhookServerService.verifyHmacSha256(payload, "sha256=" + hexDigest, SECRET_KEY));

        // Verify without prefix
        assertTrue(WebhookServerService.verifyHmacSha256(payload, hexDigest, SECRET_KEY));
    }

    @Test
    @DisplayName("Altered payload bytes must fail HMAC verification against original signature")
    void testAlteredPayloadFailsVerification() {
        byte[] original = "{\"action\": \"push\", \"repo\": \"my-app\"}".getBytes(StandardCharsets.UTF_8);
        byte[] altered = "{\"action\": \"push\", \"repo\": \"malicious-app\"}".getBytes(StandardCharsets.UTF_8);

        String signature = WebhookServerService.calculateHmacSha256(original, SECRET_KEY);

        assertFalse(WebhookServerService.verifyHmacSha256(altered, signature, SECRET_KEY),
                "Altered payload must not verify with signature generated for original payload");
    }

    @Test
    @DisplayName("Invalid signature string must be rejected")
    void testInvalidSignatureRejection() {
        byte[] payload = "{\"test\": \"payload\"}".getBytes(StandardCharsets.UTF_8);
        String invalidSignature = "sha256=0000000000000000000000000000000000000000000000000000000000000000";

        assertFalse(WebhookServerService.verifyHmacSha256(payload, invalidSignature, SECRET_KEY));
    }

    @Test
    @DisplayName("Missing, null, or empty secret must fail closed")
    void testMissingSecretFailsClosed() {
        byte[] payload = "{\"test\": \"data\"}".getBytes(StandardCharsets.UTF_8);

        assertFalse(WebhookServerService.verifyHmacSha256(payload, "sha256=abcdef", null));
        assertFalse(WebhookServerService.verifyHmacSha256(payload, "sha256=abcdef", ""));
        assertFalse(WebhookServerService.verifyHmacSha256(null, "sha256=abcdef", SECRET_KEY));
        assertFalse(WebhookServerService.verifyHmacSha256(payload, null, SECRET_KEY));
    }

    @Test
    @DisplayName("Secret rotation allows verification against secondary key")
    void testSecretRotationSecondaryKeyVerification() {
        byte[] payload = "{\"pipelineId\": \"pipe-rotate\"}".getBytes(StandardCharsets.UTF_8);

        String signatureWithRotationKey = WebhookServerService.calculateHmacSha256(payload, ROTATION_KEY);

        // Verification against old key fails
        assertFalse(WebhookServerService.verifyHmacSha256(payload, signatureWithRotationKey, SECRET_KEY));

        // Verification against rotated secondary key succeeds
        assertTrue(WebhookServerService.verifyHmacSha256(payload, signatureWithRotationKey, ROTATION_KEY));
    }

    @Test
    @DisplayName("HMAC verification must be case-insensitive for SHA256= prefix and hex characters")
    void testCaseInsensitiveHmacVerification() {
        byte[] payload = "{\"test\": \"case-insensitivity\"}".getBytes(StandardCharsets.UTF_8);
        String hexDigest = WebhookServerService.calculateHmacSha256(payload, SECRET_KEY);

        // Test with uppercase prefix SHA256=
        assertTrue(WebhookServerService.verifyHmacSha256(payload, "SHA256=" + hexDigest, SECRET_KEY),
                "Uppercase SHA256= prefix must be accepted");

        // Test with uppercase hex characters
        String upperHex = hexDigest.toUpperCase(java.util.Locale.ROOT);
        assertTrue(WebhookServerService.verifyHmacSha256(payload, "sha256=" + upperHex, SECRET_KEY),
                "Uppercase hex digest characters must verify correctly against computed signature");
    }
}
