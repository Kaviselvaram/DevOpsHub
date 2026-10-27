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
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
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
    private final AlertsSidebarView alertsSidebar;

    private final Map<String, Button> navButtons = new HashMap<>();
    private String currentViewName = "home";
    private Node currentViewNode;

    public MainLayoutView(Stage primaryStage) {
        this.primaryStage = primaryStage;
        this.projectService = new ProjectService();
        this.pipelineService = new PipelineService();
        this.dashboardService = new DashboardService();
        this.executionEngine = ExecutionEngine.getInstance();

        this.getStyleClass().add("root");

        // Center Content Stack (holds active view and floating toasts)
        this.contentStack = new StackPane();
        VBox.setVgrow(contentStack, Priority.ALWAYS);

        // Right Alerts Sidebar matching reference
        this.alertsSidebar = new AlertsSidebarView(this, dashboardService);
        this.setRight(alertsSidebar);

        // Build Shell Components
        this.setLeft(createSidebar());

        VBox centerArea = new VBox();
        VBox.setVgrow(centerArea, Priority.ALWAYS);

        // Header matching reference
        HBox topHeader = new HBox(16);
        topHeader.getStyleClass().add("top-header");
        topHeader.setAlignment(Pos.CENTER_LEFT);

        VBox titleBox = new VBox(2);
        pageTitleLabel = new Label("Home");
        pageTitleLabel.getStyleClass().add("header-title");
        pageSubtitleLabel = new Label("Welcome to DevOpsHub CI/CD Management Platform");
        pageSubtitleLabel.getStyleClass().add("header-subtitle");
        titleBox.getChildren().addAll(pageTitleLabel, pageSubtitleLabel);

        // Reference Header Search Field connected to active view filtering (UI-02)
        TextField searchInput = new TextField();
        searchInput.setPromptText("Search workspace...");
        searchInput.getStyleClass().add("header-search-field");
        searchInput.setPrefWidth(180);
        searchInput.setMaxWidth(220);
        searchInput.textProperty().addListener((obs, oldVal, newVal) -> handleGlobalSearch(newVal));
        searchInput.setOnAction(e -> handleGlobalSearch(searchInput.getText()));

        // Reference Date Badge
        Label dateBadge = new Label(java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy")));
        dateBadge.getStyleClass().add("header-date-badge");
        dateBadge.setMinWidth(Region.USE_PREF_SIZE);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // Header DB Status indicator (DAT-01)
        boolean isH2 = DatabaseManager.getInstance().isH2Fallback();
        boolean isMem = DatabaseManager.getInstance().isH2InMemory();
        HBox dbHeaderPill = new HBox(6);
        dbHeaderPill.setAlignment(Pos.CENTER_LEFT);
        dbHeaderPill.setMinWidth(Region.USE_PREF_SIZE);
        dbHeaderPill.setStyle("-fx-background-color: #1A1D2B; -fx-padding: 5px 12px; -fx-background-radius: 12px; -fx-border-color: #26293C; -fx-cursor: hand;");
        SvgIcon dbDot = new SvgIcon(SvgIcon.IconName.CHECK_CIRCLE, 12, isH2 ? (isMem ? Color.web("#F59E0B") : Color.web("#38BDF8")) : Color.web("#10B981"));
        Label dbStatusText = new Label(isH2 ? (isMem ? "H2 Memory" : "H2 Disk Fallback") : "MySQL Live");
        dbStatusText.setStyle(String.format("-fx-font-size: 11px; -fx-font-weight: 700; -fx-text-fill: %s;", isH2 ? (isMem ? "#F59E0B" : "#38BDF8") : "#10B981"));
        dbHeaderPill.getChildren().addAll(dbDot, dbStatusText);
        dbHeaderPill.setOnMouseClicked(e -> {
            long ms = DatabaseManager.getInstance().pingLatencyMillis();
            showToast(String.format("Active Database: %s (Latency: %d ms)", DatabaseManager.getInstance().getDatabaseType(), ms), ToastNotification.ToastType.INFO);
        });

        // Bell Icon Toggle for Alerts Panel
        Button toggleAlertsBtn = new Button();
        toggleAlertsBtn.getStyleClass().add("btn-secondary");
        toggleAlertsBtn.setGraphic(new SvgIcon(SvgIcon.IconName.BELL, 13, Color.web("#8A8D9E")));
        toggleAlertsBtn.setTooltip(new Tooltip("Toggle Alerts & Activity Panel"));
        toggleAlertsBtn.setOnAction(e -> {
            if (this.getRight() != null) {
                this.setRight(null);
            } else {
                this.setRight(alertsSidebar);
            }
        });

        headerActionsBox = new HBox(10);
        headerActionsBox.setAlignment(Pos.CENTER_RIGHT);

        topHeader.getChildren().addAll(titleBox, searchInput, dateBadge, spacer, dbHeaderPill, toggleAlertsBtn, headerActionsBox);
        centerArea.getChildren().addAll(topHeader, contentStack);

        this.setCenter(centerArea);

        // Genuine Responsive Layout Adaptation
        this.widthProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal.doubleValue() < 1100.0) {
                if (this.getRight() != null) {
                    this.setRight(null);
                }
            } else {
                if (this.getRight() == null) {
                    this.setRight(alertsSidebar);
                }
            }
        });

        // Initial Startup View: Home Screen (as mandated by prompt)
        navigateToHome();
    }

    private Node createSidebar() {
        VBox sidebar = new VBox(6);
        sidebar.getStyleClass().add("sidebar");

        // macOS Window Control Circles (Red, Yellow, Green)
        HBox macLights = new HBox(7);
        macLights.getStyleClass().add("mac-traffic-lights");
        Region dotRed = new Region();
        dotRed.getStyleClass().add("mac-dot-red");
        Region dotYellow = new Region();
        dotYellow.getStyleClass().add("mac-dot-yellow");
        Region dotGreen = new Region();
        dotGreen.getStyleClass().add("mac-dot-green");
        macLights.getChildren().addAll(dotRed, dotYellow, dotGreen);

        // Brand Logo Area
        HBox brandBox = new HBox(10);
        brandBox.setAlignment(Pos.CENTER_LEFT);
        brandBox.setPadding(new Insets(2, 6, 14, 6));

        SvgIcon brandIcon = new SvgIcon(SvgIcon.IconName.CPU, 22, Color.web("#38BDF8"));
        VBox brandTextBox = new VBox(2);
        Label brandTitle = new Label("DEVOPSHUB");
        brandTitle.getStyleClass().add("sidebar-brand-title");
        Label brandSubtitle = new Label("INTELLIGENT CI/CD");
        brandSubtitle.getStyleClass().add("sidebar-brand-subtitle");
        brandTextBox.getChildren().addAll(brandTitle, brandSubtitle);

        brandBox.getChildren().addAll(brandIcon, brandTextBox);

        // Nav Section Label
        Label navTitle = new Label("NAVIGATION");
        navTitle.getStyleClass().add("nav-section-title");

        // Nav Buttons (Home, Dashboard, Map (Builder), Projects, Pipelines, Executions, Logs, Analytics, Settings)
        Button homeBtn = createNavButton("Home", SvgIcon.IconName.HOME, this::navigateToHome);
        Button dashBtn = createNavButton("Dashboard", SvgIcon.IconName.DASHBOARD, this::navigateToDashboard);
        Button mapBtn = createNavButton("Map (Builder)", SvgIcon.IconName.MAP, () -> navigateToPipelineBuilder(null));
        Button projBtn = createNavButton("Projects", SvgIcon.IconName.PROJECTS, this::navigateToProjects);
        Button pipeBtn = createNavButton("Pipelines", SvgIcon.IconName.PIPELINES, this::navigateToPipelines);
        Button execBtn = createNavButton("Executions", SvgIcon.IconName.EXECUTIONS, this::navigateToExecutions);
        Button logBtn = createNavButton("Logs", SvgIcon.IconName.LOGS, this::navigateToLogs);
        Button analyticsBtn = createNavButton("Analytics", SvgIcon.IconName.ANALYTICS, this::navigateToAnalytics);
        Button settingsBtn = createNavButton("Settings", SvgIcon.IconName.SETTINGS, this::navigateToSettings);

        navButtons.put("home", homeBtn);
        navButtons.put("dashboard", dashBtn);
        navButtons.put("builder", mapBtn);
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
        dbStatusBox.setPadding(new Insets(10, 10, 8, 10));
        dbStatusBox.setStyle("-fx-background-color: #1A1C29; -fx-background-radius: 6px; -fx-border-color: #27283B;");

        boolean isH2 = DatabaseManager.getInstance().isH2Fallback();
        boolean isMem = DatabaseManager.getInstance().isH2InMemory();
        SvgIcon dbIcon = new SvgIcon(SvgIcon.IconName.DATABASE, 14, isH2 ? (isMem ? Color.web("#F59E0B") : Color.web("#38BDF8")) : Color.web("#10B981"));
        String dbLabelText = isH2 ? (isMem ? "H2 Memory (Ephemeral)" : "H2 Fallback (Disk)") : "MySQL 8.0 Live";
        Label dbLabel = new Label(dbLabelText);
        dbLabel.setStyle(String.format("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: %s;",
                isH2 ? (isMem ? "#FBBF24" : "#38BDF8") : "#34D399"));
        dbStatusBox.getChildren().addAll(dbIcon, dbLabel);
        dbStatusBox.setCursor(javafx.scene.Cursor.HAND);
        String tooltipText;
        if (!isH2) {
            tooltipText = "Connected to live MySQL 8.0 server (localhost:3306). Authoritative persistent storage active. Click to check settings.";
        } else if (isMem) {
            tooltipText = "Warning: Running in ephemeral in-memory H2 fallback mode. Data will NOT persist after application restart. Click to check settings.";
        } else {
            tooltipText = "Primary MySQL unreachable. Running in durable disk-backed H2 fallback mode (./data/devopshub). Data persists across restarts. Click to check settings.";
        }
        Tooltip.install(dbStatusBox, new Tooltip(tooltipText));
        dbStatusBox.setOnMouseClicked(e -> navigateToSettings());

        sidebar.getChildren().addAll(
                macLights, brandBox, navTitle,
                homeBtn, dashBtn, projBtn, pipeBtn, mapBtn, execBtn, logBtn, analyticsBtn, settingsBtn,
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
        if (this.currentViewNode instanceof ExecutionDetailView edv) {
            edv.dispose();
        }
        setActiveNav(viewName);
        this.currentViewNode = viewNode;
        pageTitleLabel.setText(title);
        pageSubtitleLabel.setText(subtitle);

        headerActionsBox.getChildren().clear();
        if (actionButtons != null) {
            headerActionsBox.getChildren().addAll(actionButtons);
        }

        ScrollPane scroll = new ScrollPane(viewNode);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scroll.setStyle("-fx-background-color: transparent; -fx-background: transparent; -fx-padding: 0;");

        contentStack.getChildren().setAll(scroll);
    }

    public void handleGlobalSearch(String query) {
        String q = query != null ? query.trim() : "";
        if (currentViewNode instanceof ProjectsView pv) {
            pv.setSearchQuery(q);
        } else if (currentViewNode instanceof PipelinesView plv) {
            plv.setSearchQuery(q);
        } else if (currentViewNode instanceof ExecutionHistoryView ehv) {
            ehv.setSearchQuery(q);
        } else if (currentViewNode instanceof LogViewerView lvv) {
            lvv.setSearchQuery(q);
        } else if (!q.isEmpty()) {
            navigateToPipelines();
            if (currentViewNode instanceof PipelinesView plv) {
                plv.setSearchQuery(q);
            }
        }
    }

    public void showToast(String message, ToastNotification.ToastType type) {
        Platform.runLater(() -> ToastNotification.show(contentStack, message, type));
    }

    // Navigation Methods
    public void navigateToHome() {
        setView("home", "Home", "Welcome to DevOpsHub CI/CD Management Platform",
                new HomeView(this, projectService, dashboardService));
    }

    public AlertsSidebarView getAlertsSidebar() {
        return alertsSidebar;
    }

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
        setView("builder", title, "Visual stage builder and dependency configuration",
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
