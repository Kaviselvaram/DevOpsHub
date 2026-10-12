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

        Label modeLabel = new Label(isH2 ? "H2 In-Memory Fallback Mode" : "Primary MySQL 8.0 Server (Port 3306)");
        modeLabel.setStyle(String.format("-fx-font-weight: bold; -fx-text-fill: %s;", isH2 ? "#D97706" : "#059669"));

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

    private void buildSystemInfo() {
        VBox card = new VBox(10);
        card.getStyleClass().add("card");

        Label title = new Label("Environment & Platform Diagnostics");
        title.getStyleClass().add("card-header");

        Label javaVer = new Label("• Java Runtime: " + System.getProperty("java.version") + " (" + System.getProperty("java.vendor") + ")");
        Label osName = new Label("• Operating System: " + System.getProperty("os.name") + " (" + System.getProperty("os.arch") + ")");
        Label javafxVer = new Label("• JavaFX Version: 21.0.6 (OpenJFX native desktop)");
        Label academic = new Label("• Course Context: CSE1007 — Java Programming (DevOpsHub Intelligent CI/CD Platform)");

        javaVer.setStyle("-fx-text-fill: #475569; -fx-font-size: 12px;");
        osName.setStyle("-fx-text-fill: #475569; -fx-font-size: 12px;");
        javafxVer.setStyle("-fx-text-fill: #475569; -fx-font-size: 12px;");
        academic.setStyle("-fx-text-fill: #0284C7; -fx-font-weight: bold; -fx-font-size: 12px;");

        card.getChildren().addAll(title, javaVer, osName, javafxVer, academic);
        this.getChildren().add(card);
    }
}
