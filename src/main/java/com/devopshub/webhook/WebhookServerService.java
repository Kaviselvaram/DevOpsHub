package com.devopshub.webhook;

import com.devopshub.config.AppConfig;
import com.devopshub.execution.ExecutionEngine;
import com.devopshub.model.Execution;
import com.devopshub.model.Pipeline;
import com.devopshub.model.TriggerType;
import com.devopshub.service.PipelineService;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.Executors;

/**
 * Genuine HTTP Webhook Server with Cryptographic HMAC-SHA256 Signature Verification,
 * Constant-Time Comparison, Key Rotation, and Replay Protection (SEC-02).
 */
public class WebhookServerService {
    private static final Logger logger = LoggerFactory.getLogger(WebhookServerService.class);
    private static WebhookServerService instance;

    public static final long REPLAY_WINDOW_MILLIS = 300_000L; // 5 minutes

    private HttpServer server;
    private int port;
    private boolean isRunning = false;
    private String configuredSecretToken = null;
    private String rotationSecretKey = null;
    private ExecutionEngine executionEngine;
    private PipelineService pipelineService;

    // Bounded delivery ID and signature cache with timestamp tracking for replay protection
    private final Map<String, Long> processedDeliveryCache = Collections.synchronizedMap(new LinkedHashMap<>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Long> eldest) {
            return size() > 2000;
        }
    });

    private WebhookServerService() {
        this.port = AppConfig.getInstance().getInt("WEBHOOK_PORT", 8089);
        this.configuredSecretToken = AppConfig.getInstance().get("WEBHOOK_SECRET_KEY",
                AppConfig.getInstance().get("WEBHOOK_SECRET_TOKEN", null));
        this.rotationSecretKey = AppConfig.getInstance().get("WEBHOOK_ROTATION_SECRET_KEY", null);
        this.executionEngine = ExecutionEngine.getInstance();
        this.pipelineService = new PipelineService();
    }

    public static synchronized WebhookServerService getInstance() {
        if (instance == null) {
            instance = new WebhookServerService();
        }
        return instance;
    }

    public synchronized void setDependencies(ExecutionEngine engine, PipelineService service) {
        if (engine != null) this.executionEngine = engine;
        if (service != null) this.pipelineService = service;
    }

    public synchronized void startServer() throws IOException {
        startServer(this.port);
    }

    public synchronized void startServer(int targetPort) throws IOException {
        if (isRunning) return;

        try {
            server = HttpServer.create(new InetSocketAddress(targetPort), 0);
            server.createContext("/api/webhook/trigger", new WebhookHandler());
            server.setExecutor(Executors.newFixedThreadPool(4));
            server.start();
            this.port = server.getAddress().getPort();
            isRunning = true;
            logger.info("Secure Webhook HTTP Server listening on http://localhost:{}/api/webhook/trigger (HMAC-SHA256 enabled)", port);
        } catch (IOException e) {
            logger.error("Failed to start webhook HTTP server on port {}: {}", targetPort, e.getMessage());
            throw e;
        }
    }

    public synchronized void stopServer() {
        if (server != null && isRunning) {
            server.stop(1);
            isRunning = false;
            logger.info("Webhook HTTP Server stopped.");
        }
    }

    public boolean isRunning() { return isRunning; }
    public int getPort() { return (server != null && isRunning) ? server.getAddress().getPort() : port; }
    public String getConfiguredSecretToken() { return configuredSecretToken; }
    public void setConfiguredSecretToken(String token) { this.configuredSecretToken = token; }
    public String getRotationSecretKey() { return rotationSecretKey; }
    public void setRotationSecretKey(String key) { this.rotationSecretKey = key; }

    /**
     * Computes the HMAC-SHA256 hex digest over raw bytes using the specified secret.
     */
    public static String calculateHmacSha256(byte[] data, String secret) {
        if (data == null || secret == null) {
            throw new IllegalArgumentException("Payload and secret must not be null for HMAC computation");
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKeySpec);
            byte[] hash = mac.doFinal(data);
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString().toLowerCase(Locale.ROOT);
        } catch (Exception e) {
            throw new RuntimeException("HMAC-SHA256 calculation failed", e);
        }
    }

    /**
     * Verifies an incoming HMAC-SHA256 signature against the raw request bytes using constant-time equality.
     */
    public static boolean verifyHmacSha256(byte[] data, String signatureHeader, String secret) {
        if (data == null || signatureHeader == null || secret == null || secret.trim().isEmpty()) {
            return false;
        }
        String sig = signatureHeader.trim();
        if (sig.regionMatches(true, 0, "sha256=", 0, 7)) {
            sig = sig.substring(7).trim();
        }
        sig = sig.toLowerCase(Locale.ROOT);
        try {
            String expectedHex = calculateHmacSha256(data, secret).toLowerCase(Locale.ROOT);
            return MessageDigest.isEqual(
                    expectedHex.getBytes(StandardCharsets.UTF_8),
                    sig.getBytes(StandardCharsets.UTF_8)
            );
        } catch (Exception e) {
            logger.error("HMAC signature verification failed: {}", e.getMessage());
            return false;
        }
    }

    private class WebhookHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "{\"error\": \"Method Not Allowed. Use POST.\"}");
                return;
            }

            try {
                // Read exact raw request bytes for HMAC calculation with 1MB maximum body limit
                InputStream is = exchange.getRequestBody();
                int maxBytes = 1024 * 1024; // 1MB
                byte[] rawBytes = is.readNBytes(maxBytes + 1);
                if (rawBytes.length > maxBytes) {
                    sendResponse(exchange, 413, "{\"error\": \"Payload Too Large: Webhook body exceeds 1MB limit.\"}");
                    return;
                }
                String body = new String(rawBytes, StandardCharsets.UTF_8).trim();

                String pipelineId = null;
                String token = null;

                // Check query parameters
                String query = exchange.getRequestURI().getQuery();
                Map<String, String> qParams = parseQueryParams(query);
                if (qParams.containsKey("pipelineId")) pipelineId = qParams.get("pipelineId");
                if (qParams.containsKey("projectId")) {
                    String projId = qParams.get("projectId");
                    List<Pipeline> pipes = pipelineService.getPipelinesByProject(projId);
                    if (!pipes.isEmpty()) {
                        pipelineId = pipes.get(0).getId();
                    }
                }
                if (qParams.containsKey("token")) token = qParams.get("token");

                // Parse JSON body if present
                if (!body.isEmpty() && body.startsWith("{")) {
                    try {
                        JsonObject json = JsonParser.parseString(body).getAsJsonObject();
                        if (pipelineId == null && json.has("pipelineId")) pipelineId = json.get("pipelineId").getAsString();
                        if (pipelineId == null && json.has("projectId")) {
                            List<Pipeline> pipes = pipelineService.getPipelinesByProject(json.get("projectId").getAsString());
                            if (!pipes.isEmpty()) pipelineId = pipes.get(0).getId();
                        }
                        if (token == null && json.has("token")) token = json.get("token").getAsString();
                    } catch (Exception ignored) {}
                }

                if (pipelineId == null || pipelineId.trim().isEmpty()) {
                    sendResponse(exchange, 400, "{\"error\": \"Missing required field 'pipelineId' or 'projectId'.\"}");
                    return;
                }

                // Check secret configuration: fail closed if no secret is configured
                if (configuredSecretToken == null || configuredSecretToken.trim().isEmpty()) {
                    logger.error("Rejecting webhook trigger: No WEBHOOK_SECRET_KEY is configured.");
                    sendResponse(exchange, 503, "{\"error\": \"Webhook service unavailable: No WEBHOOK_SECRET_KEY configured.\"}");
                    return;
                }

                // 1. Replay timestamp validation (if timestamp header is present)
                String timestampHeader = exchange.getRequestHeaders().getFirst("X-DevOpsHub-Timestamp");
                if (timestampHeader == null) {
                    timestampHeader = exchange.getRequestHeaders().getFirst("X-Hub-Timestamp");
                }
                if (timestampHeader != null && !timestampHeader.trim().isEmpty()) {
                    long ts;
                    String tsTrim = timestampHeader.trim();
                    try {
                        ts = Long.parseLong(tsTrim);
                        if (tsTrim.length() <= 10) {
                            ts = ts * 1000L; // convert epoch seconds to millis
                        }
                    } catch (NumberFormatException nfe) {
                        try {
                            ts = java.time.Instant.parse(tsTrim).toEpochMilli();
                        } catch (Exception ex) {
                            sendResponse(exchange, 400, "{\"error\": \"Bad Request: Malformed timestamp header.\"}");
                            return;
                        }
                    }
                    long now = System.currentTimeMillis();
                    if (Math.abs(now - ts) > REPLAY_WINDOW_MILLIS) {
                        logger.warn("Webhook rejected: Timestamp expired (diff: {}ms, max allowed: {}ms)", Math.abs(now - ts), REPLAY_WINDOW_MILLIS);
                        sendResponse(exchange, 401, "{\"error\": \"Unauthorized: Webhook timestamp expired or outside replay window.\"}");
                        return;
                    }
                }

                // 2. Cryptographic Signature & Token Verification
                String sigHeader = exchange.getRequestHeaders().getFirst("X-Hub-Signature-256");
                if (sigHeader == null) {
                    sigHeader = exchange.getRequestHeaders().getFirst("X-Signature-256");
                }

                boolean authorized = false;

                if (sigHeader != null && !sigHeader.trim().isEmpty()) {
                    // Primary secret HMAC verification
                    authorized = verifyHmacSha256(rawBytes, sigHeader, configuredSecretToken);
                    // Rotation secret HMAC verification
                    if (!authorized && rotationSecretKey != null && !rotationSecretKey.trim().isEmpty()) {
                        authorized = verifyHmacSha256(rawBytes, sigHeader, rotationSecretKey);
                    }
                } else if (token != null && !token.trim().isEmpty()) {
                    // Constant-time token verification
                    authorized = MessageDigest.isEqual(
                            configuredSecretToken.getBytes(StandardCharsets.UTF_8),
                            token.getBytes(StandardCharsets.UTF_8)
                    );
                    if (!authorized && rotationSecretKey != null && !rotationSecretKey.trim().isEmpty()) {
                        authorized = MessageDigest.isEqual(
                                rotationSecretKey.getBytes(StandardCharsets.UTF_8),
                                token.getBytes(StandardCharsets.UTF_8)
                        );
                    }
                }

                if (!authorized) {
                    logger.warn("Unauthorized webhook request for pipelineId '{}'. Signature or token invalid.", pipelineId);
                    sendResponse(exchange, 401, "{\"error\": \"Unauthorized. Invalid signature or secret token.\"}");
                    return;
                }

                // 3. Replay protection: deduplication by delivery ID or signature token
                String deliveryId = exchange.getRequestHeaders().getFirst("X-GitHub-Delivery");
                if (deliveryId == null) {
                    deliveryId = exchange.getRequestHeaders().getFirst("X-Delivery-ID");
                }
                String deduplicationKey = deliveryId;
                if (deduplicationKey == null || deduplicationKey.trim().isEmpty()) {
                    if (sigHeader != null && !sigHeader.trim().isEmpty()) {
                        deduplicationKey = "sig:" + sigHeader.trim().toLowerCase(Locale.ROOT);
                    }
                }

                if (deduplicationKey != null && !deduplicationKey.trim().isEmpty()) {
                    long now = System.currentTimeMillis();
                    synchronized (processedDeliveryCache) {
                        // Purge expired entries older than 2x window
                        processedDeliveryCache.entrySet().removeIf(entry -> (now - entry.getValue()) > (REPLAY_WINDOW_MILLIS * 2));
                        if (processedDeliveryCache.containsKey(deduplicationKey)) {
                            logger.warn("Webhook replay rejected: delivery/signature token '{}' was already processed.", deduplicationKey);
                            sendResponse(exchange, 409, "{\"error\": \"Conflict: Webhook delivery has already been processed.\"}");
                            return;
                        }
                        processedDeliveryCache.put(deduplicationKey, now);
                    }
                }

                Optional<Pipeline> pipeOpt = pipelineService.getPipelineById(pipelineId.trim());
                if (pipeOpt.isEmpty()) {
                    sendResponse(exchange, 404, "{\"error\": \"Pipeline not found with id: " + pipelineId + "\"}");
                    return;
                }

                Pipeline pipeline = pipeOpt.get();
                logger.info("Triggering execution for pipeline '{}' via incoming authenticated Webhook...", pipeline.getName());

                Execution execution = executionEngine.startExecution(pipeline, TriggerType.WEBHOOK, null, null);

                JsonObject res = new JsonObject();
                res.addProperty("status", "QUEUED");
                res.addProperty("executionId", execution.getId());
                res.addProperty("executionNumber", execution.getExecutionNumber());
                res.addProperty("pipelineId", pipeline.getId());
                res.addProperty("pipelineName", pipeline.getName());
                res.addProperty("triggerType", "WEBHOOK");

                sendResponse(exchange, 200, res.toString());

            } catch (Exception e) {
                logger.error("Error processing webhook: {}", e.getMessage(), e);
                sendResponse(exchange, 500, "{\"error\": \"Internal server error: " + e.getMessage() + "\"}");
            }
        }

        private void sendResponse(HttpExchange exchange, int statusCode, String responseJson) throws IOException {
            byte[] bytes = responseJson.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(statusCode, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }

        private Map<String, String> parseQueryParams(String query) {
            Map<String, String> map = new HashMap<>();
            if (query == null || query.isEmpty()) return map;
            for (String param : query.split("&")) {
                String[] pair = param.split("=");
                if (pair.length > 1) {
                    map.put(pair[0], pair[1]);
                } else if (pair.length == 1) {
                    map.put(pair[0], "");
                }
            }
            return map;
        }
    }
}
