package com.devopshub.ui.view;

import com.devopshub.config.DatabaseManager;
import com.devopshub.execution.ExecutionEngine;
import com.devopshub.model.Execution;
import com.devopshub.model.Pipeline;
import com.devopshub.model.Project;
import com.devopshub.service.DashboardService;
import com.devopshub.service.PipelineService;
import com.devopshub.service.ProjectService;
import com.devopshub.ui.component.SvgIcon;
import com.devopshub.ui.component.ToastNotification;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

import java.util.HashMap;
import java.util.Map;

/**
 * Main Application Shell: Responsive Sidebar, Header, and Content Container.
 */
public class MainLayoutView extends BorderPane {

    private final Stage primaryStage;
    private final ProjectService projectService;
    private final PipelineService pipelineService;
    private final DashboardService dashboardService;
    private final ExecutionEngine executionEngine;

    private final StackPane contentStack;
    private final Label pageTitleLabel;
    private final Label pageSubtitleLabel;
    private final HBox headerActionsBox;

    private final Map<String, Button> navButtons = new HashMap<>();
    private String currentViewName = "dashboard";

    public MainLayoutView(Stage primaryStage) {
        this.primaryStage = primaryStage;
        this.projectService = new ProjectService();
        this.pipelineService = new PipelineService();
        this.dashboardService = new DashboardService();
        this.executionEngine = new ExecutionEngine();

        this.getStyleClass().add("root");

        // Center Content Stack (holds active view and floating toasts)
        this.contentStack = new StackPane();
        VBox.setVgrow(contentStack, Priority.ALWAYS);

        // Build Shell Components
        this.setLeft(createSidebar());

        VBox centerArea = new VBox();
        VBox.setVgrow(centerArea, Priority.ALWAYS);

        // Header
        HBox topHeader = new HBox(16);
        topHeader.getStyleClass().add("top-header");
        topHeader.setAlignment(Pos.CENTER_LEFT);

        VBox titleBox = new VBox(2);
        pageTitleLabel = new Label("Dashboard");
        pageTitleLabel.getStyleClass().add("header-title");
        pageSubtitleLabel = new Label("Operational truth at a glance");
        pageSubtitleLabel.getStyleClass().add("header-subtitle");
        titleBox.getChildren().addAll(pageTitleLabel, pageSubtitleLabel);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // Header DB Status indicator
        boolean isH2 = DatabaseManager.getInstance().isH2Fallback();
        HBox dbHeaderPill = new HBox(6);
        dbHeaderPill.setAlignment(Pos.CENTER_LEFT);
        dbHeaderPill.setStyle("-fx-background-color: #F1F5F9; -fx-padding: 4px 10px; -fx-background-radius: 12px; -fx-cursor: hand;");
        SvgIcon dbDot = new SvgIcon(SvgIcon.IconName.CHECK_CIRCLE, 12, isH2 ? Color.web("#D97706") : Color.web("#10B981"));
        Label dbStatusText = new Label(isH2 ? "H2 Database" : "MySQL Connected");
        dbStatusText.setStyle("-fx-font-size: 11px; -fx-font-weight: 600; -fx-text-fill: #334155;");
        dbHeaderPill.getChildren().addAll(dbDot, dbStatusText);
        dbHeaderPill.setOnMouseClicked(e -> {
            long ms = DatabaseManager.getInstance().pingLatencyMillis();
            showToast(String.format("Database active: %s (Ping: %d ms)", isH2 ? "H2" : "MySQL 8.0", ms), ToastNotification.ToastType.INFO);
        });

        headerActionsBox = new HBox(10);
        headerActionsBox.setAlignment(Pos.CENTER_RIGHT);

        topHeader.getChildren().addAll(titleBox, spacer, dbHeaderPill, headerActionsBox);
        centerArea.getChildren().addAll(topHeader, contentStack);

        this.setCenter(centerArea);

        // Initial View
        navigateToDashboard();
    }

    private Node createSidebar() {
        VBox sidebar = new VBox(8);
        sidebar.getStyleClass().add("sidebar");

        // Brand Logo Area
        HBox brandBox = new HBox(10);
        brandBox.setAlignment(Pos.CENTER_LEFT);
        brandBox.setPadding(new Insets(4, 8, 16, 8));

        SvgIcon brandIcon = new SvgIcon(SvgIcon.IconName.CPU, 22, Color.web("#0284C7"));
        VBox brandTextBox = new VBox(2);
        Label brandTitle = new Label("DEVOPSHUB");
        brandTitle.getStyleClass().add("sidebar-brand-title");
        Label brandSubtitle = new Label("INTELLIGENT CI/CD");
        brandSubtitle.getStyleClass().add("sidebar-brand-subtitle");
        brandTextBox.getChildren().addAll(brandTitle, brandSubtitle);

        brandBox.getChildren().addAll(brandIcon, brandTextBox);

        // Nav Section Label
        Label navTitle = new Label("PLATFORM");
        navTitle.getStyleClass().add("nav-section-title");

        // Nav Buttons
        Button dashBtn = createNavButton("Dashboard", SvgIcon.IconName.DASHBOARD, this::navigateToDashboard);
        Button projBtn = createNavButton("Projects", SvgIcon.IconName.PROJECTS, this::navigateToProjects);
        Button pipeBtn = createNavButton("Pipelines", SvgIcon.IconName.PIPELINES, this::navigateToPipelines);
        Button execBtn = createNavButton("Executions", SvgIcon.IconName.EXECUTIONS, this::navigateToExecutions);
        Button logBtn = createNavButton("Logs", SvgIcon.IconName.LOGS, this::navigateToLogs);
        Button analyticsBtn = createNavButton("Analytics", SvgIcon.IconName.ANALYTICS, this::navigateToAnalytics);
        Button settingsBtn = createNavButton("Settings", SvgIcon.IconName.SETTINGS, this::navigateToSettings);

        navButtons.put("dashboard", dashBtn);
        navButtons.put("projects", projBtn);
        navButtons.put("pipelines", pipeBtn);
        navButtons.put("executions", execBtn);
        navButtons.put("logs", logBtn);
        navButtons.put("analytics", analyticsBtn);
        navButtons.put("settings", settingsBtn);

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        // Database status indicator in sidebar footer
        HBox dbStatusBox = new HBox(8);
        dbStatusBox.setAlignment(Pos.CENTER_LEFT);
        dbStatusBox.setPadding(new Insets(12, 10, 8, 10));
        dbStatusBox.setStyle("-fx-background-color: #1E293B; -fx-background-radius: 6px;");

        boolean isH2 = DatabaseManager.getInstance().isH2Fallback();
        SvgIcon dbIcon = new SvgIcon(SvgIcon.IconName.DATABASE, 14, isH2 ? Color.web("#F59E0B") : Color.web("#10B981"));
        Label dbLabel = new Label(isH2 ? "H2 Memory Mode" : "MySQL Connected");
        dbLabel.setStyle(String.format("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: %s;",
                isH2 ? "#FBBF24" : "#34D399"));
        dbStatusBox.getChildren().addAll(dbIcon, dbLabel);

        sidebar.getChildren().addAll(
                brandBox, navTitle,
                dashBtn, projBtn, pipeBtn, execBtn, logBtn, analyticsBtn, settingsBtn,
                spacer, dbStatusBox
        );

        return sidebar;
    }

    private Button createNavButton(String text, SvgIcon.IconName icon, Runnable action) {
        Button btn = new Button(text);
        btn.getStyleClass().add("nav-button");
        btn.setMaxWidth(Double.MAX_VALUE);
        btn.setGraphic(new SvgIcon(icon, 16, Color.web("#94A3B8")));
        btn.setGraphicTextGap(10);
        btn.setOnAction(e -> action.run());
        return btn;
    }

    private void setActiveNav(String viewName) {
        this.currentViewName = viewName;
        for (Map.Entry<String, Button> entry : navButtons.entrySet()) {
            Button btn = entry.getValue();
            if (entry.getKey().equals(viewName)) {
                if (!btn.getStyleClass().contains("nav-button-active")) {
                    btn.getStyleClass().add("nav-button-active");
                }
            } else {
                btn.getStyleClass().remove("nav-button-active");
            }
        }
    }

    public void setView(String viewName, String title, String subtitle, Node viewNode, Node... actionButtons) {
        setActiveNav(viewName);
        pageTitleLabel.setText(title);
        pageSubtitleLabel.setText(subtitle);

        headerActionsBox.getChildren().clear();
        if (actionButtons != null) {
            headerActionsBox.getChildren().addAll(actionButtons);
        }

        ScrollPane scroll = new ScrollPane(viewNode);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background-color: transparent; -fx-background: transparent; -fx-padding: 0;");

        contentStack.getChildren().setAll(scroll);
    }

    public void showToast(String message, ToastNotification.ToastType type) {
        Platform.runLater(() -> ToastNotification.show(contentStack, message, type));
    }

    // Navigation Methods
    public void navigateToDashboard() {
        setView("dashboard", "Dashboard", "Operational truth at a glance",
                new DashboardView(this, dashboardService, executionEngine));
    }

    public void navigateToProjects() {
        Button newProjectBtn = new Button("New Project");
        newProjectBtn.getStyleClass().add("btn-primary");
        newProjectBtn.setGraphic(new SvgIcon(SvgIcon.IconName.PLUS, 14, Color.WHITE));

        ProjectsView projectsView = new ProjectsView(this, projectService);
        newProjectBtn.setOnAction(e -> projectsView.showCreateProjectModal());

        setView("projects", "Projects", "Manage repositories and service boundaries",
                projectsView, newProjectBtn);
    }

    public void navigateToPipelines() {
        Button newPipeBtn = new Button("New Pipeline");
        newPipeBtn.getStyleClass().add("btn-primary");
        newPipeBtn.setGraphic(new SvgIcon(SvgIcon.IconName.PLUS, 14, Color.WHITE));
        newPipeBtn.setOnAction(e -> navigateToPipelineBuilder(null));

        setView("pipelines", "Pipelines", "Configure delivery intent and execution topologies",
                new PipelinesView(this, pipelineService, executionEngine), newPipeBtn);
    }

    public void navigateToPipelineBuilder(Pipeline existingPipeline) {
        String title = existingPipeline == null ? "Create Pipeline" : "Configure Pipeline";
        setView("pipelines", title, "Visual stage builder and dependency configuration",
                new PipelineBuilderView(this, pipelineService, projectService, existingPipeline));
    }

    public void navigateToExecutions() {
        setView("executions", "Execution History", "Audit trail of pipeline runs, durations, and outcomes",
                new ExecutionHistoryView(this, dashboardService));
    }

    public void navigateToExecutionDetail(String executionId) {
        setView("executions", "Execution Details", "Real-time pipeline monitoring and rule-based failure diagnosis",
                new ExecutionDetailView(this, executionId, executionEngine));
    }

    public void navigateToLogs() {
        setView("logs", "Log Viewer", "Inspect standard output and standard error execution streams",
                new LogViewerView(this));
    }

    public void navigateToAnalytics() {
        setView("analytics", "Analytics", "Delivery reliability metrics and failure classifications",
                new AnalyticsView(dashboardService));
    }

    public void navigateToSettings() {
        setView("settings", "Settings", "Database connectivity, workspaces, and execution pools",
                new SettingsView(this));
    }

    public void navigateToProjectDetail(Project project) {
        setView("projects", project.getName(), "Project overview and associated delivery pipelines",
                new ProjectDetailView(this, project, projectService, pipelineService, executionEngine));
    }

    public void navigateToPipelineDetail(Pipeline pipeline) {
        setView("pipelines", pipeline.getName(), "Pipeline topology and historical execution runs",
                new PipelineDetailView(this, pipeline, pipelineService, executionEngine));
    }

    public Stage getPrimaryStage() {
        return primaryStage;
    }
}
