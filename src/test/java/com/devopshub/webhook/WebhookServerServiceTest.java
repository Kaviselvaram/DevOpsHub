package com.devopshub.webhook;

import com.devopshub.execution.ExecutionEngine;
import com.devopshub.model.Pipeline;
import com.devopshub.model.Stage;
import com.devopshub.model.StageType;
import com.devopshub.repository.PipelineRepository;
import com.devopshub.service.PipelineService;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("WebhookServerService Real HTTP Webhook Trigger Tests")
class WebhookServerServiceTest {

    private static WebhookServerService webhookService;
    private static PipelineRepository pipelineRepository;
    private static Pipeline testPipeline;
    private static HttpClient httpClient;

    @BeforeAll
    static void setUpAll() throws IOException {
        pipelineRepository = new PipelineRepository();
        PipelineService pipelineService = new PipelineService();
        ExecutionEngine executionEngine = new ExecutionEngine();

        com.devopshub.repository.ProjectRepository projectRepo = new com.devopshub.repository.ProjectRepository();
        com.devopshub.model.Project project = new com.devopshub.model.Project(
                "proj-webhook-" + UUID.randomUUID(),
                "Webhook Test Project " + UUID.randomUUID(),
                "Testing webhooks",
                "https://github.com/test/repo.git",
                "main",
                "Java",
                "ACTIVE"
        );
        projectRepo.save(project);

        String pipeId = "webhook-test-pipe-" + UUID.randomUUID();
        testPipeline = new Pipeline(
                pipeId,
                project.getId(),
                "Webhook Triggerable Pipeline",
                "Testing real HTTP webhook triggers",
                "SEQUENTIAL",
                true
        );
        testPipeline.setStages(List.of(new Stage(UUID.randomUUID().toString(), pipeId, "Echo Stage", StageType.BUILD, 1, "echo 'Webhook triggered'", 60, true, null)));
        pipelineRepository.save(testPipeline);

        webhookService = WebhookServerService.getInstance();
        webhookService.setDependencies(executionEngine, pipelineService);
        webhookService.setConfiguredSecretToken("test-secret-token-xyz");

        if (!webhookService.isRunning()) {
            webhookService.startServer(0);
        }

        httpClient = HttpClient.newHttpClient();
    }

    @AfterAll
    static void tearDownAll() {
        if (webhookService != null) {
            webhookService.stopServer();
        }
    }

    @Test
    @DisplayName("Should successfully trigger pipeline execution via HTTP POST with valid secret token")
    void testValidWebhookTrigger() throws IOException, InterruptedException {
        int port = webhookService.getPort();
        String jsonBody = String.format("{\"pipelineId\": \"%s\", \"token\": \"test-secret-token-xyz\"}", testPipeline.getId());

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/webhook/trigger"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\"status\":\"QUEUED\""));
        assertTrue(response.body().contains(testPipeline.getId()));
        assertTrue(response.body().contains("\"triggerType\":\"WEBHOOK\""));
    }

    @Test
    @DisplayName("Should reject webhook request with invalid secret token (401 Unauthorized)")
    void testInvalidTokenRejection() throws IOException, InterruptedException {
        int port = webhookService.getPort();
        String jsonBody = String.format("{\"pipelineId\": \"%s\", \"token\": \"WRONG_TOKEN\"}", testPipeline.getId());

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/webhook/trigger"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(401, response.statusCode());
        assertTrue(response.body().contains("Unauthorized"));
    }

    @Test
    @DisplayName("Should reject webhook request without pipelineId (400 Bad Request)")
    void testMissingPipelineId() throws IOException, InterruptedException {
        int port = webhookService.getPort();
        String jsonBody = "{\"token\": \"test-secret-token-xyz\"}";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/webhook/trigger"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(400, response.statusCode());
        assertTrue(response.body().contains("Missing required field"));
    }

    @Test
    @DisplayName("Should reject GET request (405 Method Not Allowed)")
    void testMethodNotAllowed() throws IOException, InterruptedException {
        int port = webhookService.getPort();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/webhook/trigger?pipelineId=" + testPipeline.getId()))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(405, response.statusCode());
    }
}
