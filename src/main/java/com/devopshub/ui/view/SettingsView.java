package com.devopshub.ui.view;

import com.devopshub.config.AppConfig;
import com.devopshub.config.DatabaseManager;
import com.devopshub.ui.component.SvgIcon;
import com.devopshub.ui.component.ToastNotification;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;

/**
 * Settings View:
 * Database connectivity testing with millisecond ping latency,
 * execution pool tuning with runtime persistence, and environment diagnostics.
 */
public class SettingsView extends VBox {

    private final MainLayoutView mainLayout;

    private Label pingResultLabel;
    private TextField maxPoolField;
    private TextField timeoutField;

    public SettingsView(MainLayoutView mainLayout) {
        this.mainLayout = mainLayout;

        this.setSpacing(20);
        this.setPadding(new Insets(24));
        this.setStyle("-fx-background-color: transparent;");

        buildDatabaseSettings();
        buildExecutionSettings();
        buildWebhookAndSchedulerSettings();
        buildSystemInfo();
    }

    private void buildDatabaseSettings() {
        VBox card = new VBox(14);
        card.getStyleClass().add("card");

        HBox header = new HBox(10);
        header.setAlignment(Pos.CENTER_LEFT);
        SvgIcon dbIcon = new SvgIcon(SvgIcon.IconName.DATABASE, 18, Color.web("#0284C7"));
        Label title = new Label("Database Connectivity Settings");
        title.getStyleClass().add("card-header");
        header.getChildren().addAll(dbIcon, title);

        AppConfig config = AppConfig.getInstance();
        boolean isH2 = DatabaseManager.getInstance().isH2Fallback();
        boolean isMem = DatabaseManager.getInstance().isH2InMemory();

        GridPane grid = new GridPane();
        grid.setHgap(16);
        grid.setVgap(10);

        TextField hostField = new TextField(config.getDbHost());
        hostField.setEditable(false);

        TextField portField = new TextField(String.valueOf(config.getDbPort()));
        portField.setEditable(false);

        TextField dbField = new TextField(config.getDbName());
        dbField.setEditable(false);

        TextField userField = new TextField(config.getDbUser());
        userField.setEditable(false);

        String engineText = isH2 ?
                (isMem ? "H2 In-Memory Fallback Mode (Ephemeral)" : "H2 Embedded Disk Fallback Mode (./data/devopshub)") :
                "Primary MySQL 8.0 Server (Port 3306 - Persistent)";
        Label modeLabel = new Label(engineText);
        modeLabel.setStyle(String.format("-fx-font-weight: bold; -fx-text-fill: %s;",
                isH2 ? (isMem ? "#D97706" : "#0284C7") : "#059669"));

        grid.add(new Label("Database Engine:"), 0, 0);
        grid.add(modeLabel, 1, 0);
        grid.add(new Label("Host:"), 0, 1);
        grid.add(hostField, 1, 1);
        grid.add(new Label("Port:"), 2, 1);
        grid.add(portField, 3, 1);
        grid.add(new Label("Database Name:"), 0, 2);
        grid.add(dbField, 1, 2);
        grid.add(new Label("User:"), 2, 2);
        grid.add(userField, 3, 2);

        HBox btnBox = new HBox(12);
        btnBox.setAlignment(Pos.CENTER_LEFT);

        Button testConnBtn = new Button("Test JDBC Connection");
        testConnBtn.getStyleClass().add("btn-secondary");
        testConnBtn.setGraphic(new SvgIcon(SvgIcon.IconName.CHECK_CIRCLE, 12, Color.web("#059669")));
        testConnBtn.setOnAction(e -> {
            long latency = DatabaseManager.getInstance().pingLatencyMillis();
            if (latency >= 0) {
                pingResultLabel.setText(String.format("✓ Connected (Ping: %d ms)", latency));
                pingResultLabel.setStyle("-fx-text-fill: #059669; -fx-font-weight: bold;");
                mainLayout.showToast(String.format("Database connected! Latency: %d ms", latency), ToastNotification.ToastType.SUCCESS);
            } else {
                pingResultLabel.setText("✗ Connection Failed");
                pingResultLabel.setStyle("-fx-text-fill: #E11D48; -fx-font-weight: bold;");
                mainLayout.showToast("Database connection test failed. Check MySQL status on port 3306.", ToastNotification.ToastType.ERROR);
            }
        });

        Button runMigrationsBtn = new Button("Re-apply Migrations");
        runMigrationsBtn.getStyleClass().add("btn-secondary");
        runMigrationsBtn.setGraphic(new SvgIcon(SvgIcon.IconName.REFRESH, 12, Color.web("#0284C7")));
        runMigrationsBtn.setOnAction(e -> {
            try {
                DatabaseManager.getInstance().applyMigrations();
                mainLayout.showToast("Database schema migrations applied successfully!", ToastNotification.ToastType.SUCCESS);
            } catch (Exception ex) {
                mainLayout.showToast("Migration error: " + ex.getMessage(), ToastNotification.ToastType.ERROR);
            }
        });

        pingResultLabel = new Label();
        pingResultLabel.setStyle("-fx-font-size: 12px;");

        btnBox.getChildren().addAll(testConnBtn, runMigrationsBtn, pingResultLabel);
        card.getChildren().addAll(header, grid, btnBox);
        this.getChildren().add(card);
    }

    private void buildExecutionSettings() {
        VBox card = new VBox(14);
        card.getStyleClass().add("card");

        HBox header = new HBox(10);
        header.setAlignment(Pos.CENTER_LEFT);
        SvgIcon cpuIcon = new SvgIcon(SvgIcon.IconName.CPU, 18, Color.web("#0D9488"));
        Label title = new Label("Execution Engine & Concurrency Settings");
        title.getStyleClass().add("card-header");
        header.getChildren().addAll(cpuIcon, title);

        AppConfig config = AppConfig.getInstance();

        GridPane grid = new GridPane();
        grid.setHgap(16);
        grid.setVgap(10);

        maxPoolField = new TextField(String.valueOf(config.getMaxConcurrentPipelines()));
        timeoutField = new TextField(String.valueOf(config.getDefaultTimeoutSeconds()));

        TextField wsField = new TextField(config.getWorkspaceDir());
        wsField.setEditable(false);

        grid.add(new Label("Max Concurrent Pipelines:"), 0, 0);
        grid.add(maxPoolField, 1, 0);
        grid.add(new Label("Default Stage Timeout (s):"), 2, 0);
        grid.add(timeoutField, 3, 0);
        grid.add(new Label("Workspace Directory:"), 0, 1);
        grid.add(wsField, 1, 1);

        HBox actionBox = new HBox(12);
        actionBox.setAlignment(Pos.CENTER_LEFT);

        Button saveSettingsBtn = new Button("Save Execution Settings");
        saveSettingsBtn.getStyleClass().add("btn-primary");
        saveSettingsBtn.setOnAction(e -> {
            try {
                int pool = Integer.parseInt(maxPoolField.getText().trim());
                int timeout = Integer.parseInt(timeoutField.getText().trim());
                config.setMaxConcurrentPipelines(pool);
                config.setDefaultTimeoutSeconds(timeout);
                mainLayout.showToast("Execution settings saved successfully!", ToastNotification.ToastType.SUCCESS);
            } catch (NumberFormatException ex) {
                mainLayout.showToast("Invalid number format for pool size or timeout.", ToastNotification.ToastType.ERROR);
            }
        });

        actionBox.getChildren().add(saveSettingsBtn);
        card.getChildren().addAll(header, grid, actionBox);
        this.getChildren().add(card);
    }

    private void buildWebhookAndSchedulerSettings() {
        VBox card = new VBox(14);
        card.getStyleClass().add("card");

        HBox header = new HBox(10);
        header.setAlignment(Pos.CENTER_LEFT);
        SvgIcon hookIcon = new SvgIcon(SvgIcon.IconName.TERMINAL, 18, Color.web("#8B5CF6"));
        Label title = new Label("HTTP Webhooks & Automated Schedulers");
        title.getStyleClass().add("card-header");
        header.getChildren().addAll(hookIcon, title);

        var webhook = com.devopshub.webhook.WebhookServerService.getInstance();
        var scheduler = com.devopshub.scheduler.PipelineSchedulerService.getInstance();

        GridPane grid = new GridPane();
        grid.setHgap(16);
        grid.setVgap(10);

        Label serverStatus = new Label(webhook.isRunning() ?
                "Active on http://localhost:" + webhook.getPort() + "/api/webhook/trigger" : "Server Inactive");
        serverStatus.setStyle(String.format("-fx-font-weight: bold; -fx-text-fill: %s;", webhook.isRunning() ? "#10B981" : "#F43F5E"));

        TextField tokenField = new TextField(webhook.getConfiguredSecretToken());
        tokenField.setPromptText("Webhook Secret Token");

        int activeJobsCount = scheduler.getScheduledJobs().size();
        Label schedulerStatus = new Label(activeJobsCount + " active scheduled jobs configured");
        schedulerStatus.setStyle("-fx-text-fill: #8A8D9E;");

        grid.add(new Label("Webhook Endpoint:"), 0, 0);
        grid.add(serverStatus, 1, 0);
        grid.add(new Label("Secret Token:"), 0, 1);
        grid.add(tokenField, 1, 1);
        grid.add(new Label("Scheduler Status:"), 0, 2);
        grid.add(schedulerStatus, 1, 2);

        HBox btnBox = new HBox(12);
        btnBox.setAlignment(Pos.CENTER_LEFT);

        Button toggleBtn = new Button(webhook.isRunning() ? "Stop Webhook Server" : "Start Webhook Server");
        toggleBtn.getStyleClass().add("btn-secondary");
        toggleBtn.setOnAction(e -> {
            try {
                if (webhook.isRunning()) {
                    webhook.stopServer();
                    mainLayout.showToast("Webhook server stopped.", ToastNotification.ToastType.INFO);
                } else {
                    webhook.startServer();
                    mainLayout.showToast("Webhook server started on port " + webhook.getPort(), ToastNotification.ToastType.SUCCESS);
                }
                serverStatus.setText(webhook.isRunning() ?
                        "Active on http://localhost:" + webhook.getPort() + "/api/webhook/trigger" : "Server Inactive");
                serverStatus.setStyle(String.format("-fx-font-weight: bold; -fx-text-fill: %s;", webhook.isRunning() ? "#10B981" : "#F43F5E"));
                toggleBtn.setText(webhook.isRunning() ? "Stop Webhook Server" : "Start Webhook Server");
            } catch (Exception ex) {
                mainLayout.showToast("Failed to toggle webhook server: " + ex.getMessage(), ToastNotification.ToastType.ERROR);
            }
        });

        Button saveTokenBtn = new Button("Save Webhook Secret");
        saveTokenBtn.getStyleClass().add("btn-primary");
        saveTokenBtn.setOnAction(e -> {
            String t = tokenField.getText().trim();
            webhook.setConfiguredSecretToken(t);
            mainLayout.showToast("Webhook secret token saved!", ToastNotification.ToastType.SUCCESS);
        });

        btnBox.getChildren().addAll(toggleBtn, saveTokenBtn);

        // Active Scheduled Jobs Section (UI-01)
        VBox schedulesContainer = new VBox(8);
        schedulesContainer.setStyle("-fx-padding: 14px 0 0 0;");

        Label schedulesHeader = new Label("Active Pipeline Schedules");
        schedulesHeader.setStyle("-fx-font-weight: bold; -fx-text-fill: #E2E8F0; -fx-font-size: 13px;");
        schedulesContainer.getChildren().add(schedulesHeader);

        var jobs = scheduler.getScheduledJobs();
        if (jobs.isEmpty()) {
            Label emptySchedules = new Label("No automated pipeline schedules configured. Open any pipeline in Pipelines view to configure schedules.");
            emptySchedules.setStyle("-fx-font-size: 11px; -fx-text-fill: #64748B;");
            schedulesContainer.getChildren().add(emptySchedules);
        } else {
            for (var job : jobs) {
                HBox jobRow = new HBox(12);
                jobRow.setAlignment(Pos.CENTER_LEFT);
                jobRow.setStyle("-fx-background-color: #12131F; -fx-padding: 8px 12px; -fx-background-radius: 6px; -fx-border-color: #26283C;");

                Label name = new Label(job.getPipelineName());
                name.setStyle("-fx-font-weight: bold; -fx-text-fill: #38BDF8;");

                Label interval = new Label("Runs every " + (job.getIntervalSeconds() >= 60 ? (job.getIntervalSeconds() / 60) + "m" : job.getIntervalSeconds() + "s"));
                interval.setStyle("-fx-text-fill: #94A3B8; -fx-font-size: 11px;");

                Region sp = new Region();
                HBox.setHgrow(sp, Priority.ALWAYS);

                Button unschBtn = new Button("Unschedule");
                unschBtn.getStyleClass().add("btn-secondary");
                unschBtn.setStyle("-fx-font-size: 10px; -fx-text-fill: #F43F5E; -fx-padding: 3px 8px;");
                unschBtn.setOnAction(ev -> {
                    scheduler.unschedulePipeline(job.getPipelineId());
                    mainLayout.showToast("Unscheduled " + job.getPipelineName(), ToastNotification.ToastType.INFO);
                    mainLayout.navigateToSettings();
                });

                jobRow.getChildren().addAll(name, interval, sp, unschBtn);
                schedulesContainer.getChildren().add(jobRow);
            }
        }

        Label desktopDisclaimer = new Label("Note: Automated schedules execute while DevOpsHub desktop application is active. When the desktop app exits, schedules pause until next launch.");
        desktopDisclaimer.setWrapText(true);
        desktopDisclaimer.setStyle("-fx-font-size: 11px; -fx-text-fill: #F59E0B;");
        schedulesContainer.getChildren().add(desktopDisclaimer);

        card.getChildren().addAll(header, grid, btnBox, new Separator(), schedulesContainer);
        this.getChildren().add(card);
    }

    private void buildSystemInfo() {
        VBox card = new VBox(10);
        card.getStyleClass().add("card");

        Label title = new Label("Environment & Platform Diagnostics");
        title.getStyleClass().add("card-header");

        Label javaVer = new Label("• Java Runtime: " + System.getProperty("java.version") + " (" + System.getProperty("java.vendor") + ")");
        Label osName = new Label("• Operating System: " + System.getProperty("os.name") + " (" + System.getProperty("os.arch") + ")");
        Label javafxVer = new Label("• JavaFX Version: 21.0.6 (OpenJFX native desktop)");
        Label academic = new Label("• Course Context: CSE1007 — Java Programming (DevOpsHub Intelligent CI/CD Platform)");

        javaVer.setStyle("-fx-text-fill: #8A8D9E; -fx-font-size: 12px;");
        osName.setStyle("-fx-text-fill: #8A8D9E; -fx-font-size: 12px;");
        javafxVer.setStyle("-fx-text-fill: #8A8D9E; -fx-font-size: 12px;");
        academic.setStyle("-fx-text-fill: #38BDF8; -fx-font-weight: bold; -fx-font-size: 12px;");

        card.getChildren().addAll(title, javaVer, osName, javafxVer, academic);
        this.getChildren().add(card);
    }
}
