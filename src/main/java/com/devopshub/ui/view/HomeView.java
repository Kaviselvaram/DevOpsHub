package com.devopshub.ui.view;

import com.devopshub.config.AppConfig;
import com.devopshub.config.DatabaseManager;
import com.devopshub.execution.ExecutionThreadPool;
import com.devopshub.model.Execution;
import com.devopshub.model.Project;
import com.devopshub.service.DashboardService;
import com.devopshub.service.ProjectService;
import com.devopshub.ui.component.StatusBadge;
import com.devopshub.ui.component.SvgIcon;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;

import java.util.List;

/**
 * Dedicated Home / Welcome Screen.
 * Acts as the default application landing page, providing rapid onboarding,
 * system health diagnostics, quick workflow shortcuts, and recent project cards.
 */

public class HomeView extends VBox {

    private final MainLayoutView mainLayout;
    private final ProjectService projectService;
    private final DashboardService dashboardService;

    public HomeView(MainLayoutView mainLayout, ProjectService projectService, DashboardService dashboardService) {
        this.mainLayout = mainLayout;
        this.projectService = projectService;
        this.dashboardService = dashboardService;

        this.setSpacing(24);
        this.setPadding(new Insets(24, 28, 36, 28));
        this.setStyle("-fx-background-color: transparent;");

        buildHeroSection();
        buildQuickActionCards();
        buildSystemHealthCard();
        buildRecentWorkflowsSection();
    }

    private void buildHeroSection() {
        VBox heroCard = new VBox(14);
        heroCard.getStyleClass().add("card");
        heroCard.setStyle("-fx-background-color: #161825; -fx-border-color: #26283C; -fx-border-radius: 14px; -fx-background-radius: 14px; -fx-padding: 28px;");

        HBox topTag = new HBox(8);
        topTag.setAlignment(Pos.CENTER_LEFT);

        Label badge = new Label("DEVOPSHUB 2026 • INTELLIGENT CI/CD");
        badge.setStyle("-fx-font-size: 10px; -fx-font-weight: 800; -fx-text-fill: #38BDF8; -fx-background-color: #16243A; -fx-padding: 4px 10px; -fx-background-radius: 12px;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        boolean isH2 = DatabaseManager.getInstance().isH2Fallback();
        boolean isMem = DatabaseManager.getInstance().isH2InMemory();
        Label dbTag = new Label(isH2 ? (isMem ? "H2 Memory Active" : "H2 Fallback (Disk)") : "MySQL 8.0 Connected");
        dbTag.setStyle(String.format("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: %s; -fx-background-color: #1A1D2B; -fx-padding: 4px 10px; -fx-background-radius: 10px;",
                isH2 ? (isMem ? "#F59E0B" : "#38BDF8") : "#10B981"));

        topTag.getChildren().addAll(badge, spacer, dbTag);

        Label headline = new Label("Intelligent CI/CD Management Platform");
        headline.setStyle("-fx-font-size: 26px; -fx-font-weight: 800; -fx-text-fill: #FFFFFF;");

        Label description = new Label("Orchestrate, validate, and execute resilient Java delivery pipelines with local execution safety, automated rule-based failure diagnosis, and verified database persistence.");
        description.setStyle("-fx-font-size: 13px; -fx-text-fill: #8A8D9E; -fx-max-width: 820px;");
        description.setWrapText(true);

        HBox ctaBox = new HBox(12);
        ctaBox.setAlignment(Pos.CENTER_LEFT);
        ctaBox.setPadding(new Insets(8, 0, 0, 0));

        Button createProjBtn = new Button("Create New Project");
        createProjBtn.getStyleClass().add("btn-primary");
        createProjBtn.setGraphic(new SvgIcon(SvgIcon.IconName.PLUS, 14, Color.WHITE));
        createProjBtn.setOnAction(e -> mainLayout.navigateToProjects());

        Button openBuilderBtn = new Button("Launch Pipeline Builder");
        openBuilderBtn.getStyleClass().add("btn-secondary");
        openBuilderBtn.setGraphic(new SvgIcon(SvgIcon.IconName.PIPELINES, 14, Color.web("#94A3B8")));
        openBuilderBtn.setOnAction(e -> mainLayout.navigateToPipelineBuilder(null));

        Button viewDashboardBtn = new Button("Operational Dashboard");
        viewDashboardBtn.getStyleClass().add("btn-secondary");
        viewDashboardBtn.setGraphic(new SvgIcon(SvgIcon.IconName.DASHBOARD, 14, Color.web("#94A3B8")));
        viewDashboardBtn.setOnAction(e -> mainLayout.navigateToDashboard());

        ctaBox.getChildren().addAll(createProjBtn, openBuilderBtn, viewDashboardBtn);

        heroCard.getChildren().addAll(topTag, headline, description, ctaBox);
        this.getChildren().add(heroCard);
    }

    private void buildQuickActionCards() {
        Label sectionTitle = new Label("QUICK WORKFLOW ACTIONS");
        sectionTitle.setStyle("-fx-font-size: 11px; -fx-font-weight: 700; -fx-text-fill: #616478; -fx-letter-spacing: 0.5px;");

        GridPane grid = new GridPane();
        grid.setHgap(16);
        grid.setVgap(16);

        VBox action1 = createActionTile("Manage Projects", "Configure source repositories, build profiles, and working directories.",
                SvgIcon.IconName.PROJECTS, "#38BDF8", () -> mainLayout.navigateToProjects());
        VBox action2 = createActionTile("Pipeline Builder", "Compose DAG stages, shell commands, Maven compile, and test steps.",
                SvgIcon.IconName.MAP, "#8B5CF6", () -> mainLayout.navigateToPipelineBuilder(null));
        VBox action3 = createActionTile("Kanban & Executions", "Monitor active and historical runs on an interactive status board.",
                SvgIcon.IconName.EXECUTIONS, "#10B981", () -> mainLayout.navigateToExecutions());
        VBox action4 = createActionTile("System Settings", "Configure database pools, workspace roots, and execution concurrency.",
                SvgIcon.IconName.SETTINGS, "#F59E0B", () -> mainLayout.navigateToSettings());

        ColumnConstraints col = new ColumnConstraints();
        col.setPercentWidth(25);
        grid.getColumnConstraints().addAll(col, col, col, col);

        grid.add(action1, 0, 0);
        grid.add(action2, 1, 0);
        grid.add(action3, 2, 0);
        grid.add(action4, 3, 0);

        this.getChildren().addAll(sectionTitle, grid);
    }

    private VBox createActionTile(String title, String desc, SvgIcon.IconName icon, String accentColor, Runnable action) {
        VBox tile = new VBox(10);
        tile.getStyleClass().add("card");
        tile.setStyle("-fx-cursor: hand; -fx-padding: 16px; -fx-background-color: #181926;");
        tile.setOnMouseClicked(e -> action.run());

        HBox header = new HBox(10);
        header.setAlignment(Pos.CENTER_LEFT);

        StackPane iconCircle = new StackPane(new SvgIcon(icon, 16, Color.web(accentColor)));
        iconCircle.setStyle(String.format("-fx-background-color: %s20; -fx-background-radius: 8px; -fx-min-width: 32px; -fx-min-height: 32px; -fx-max-width: 32px; -fx-max-height: 32px;", accentColor));

        Label titleLabel = new Label(title);
        titleLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: 700; -fx-text-fill: #FFFFFF;");

        header.getChildren().addAll(iconCircle, titleLabel);

        Label descLabel = new Label(desc);
        descLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #8A8D9E;");
        descLabel.setWrapText(true);

        tile.getChildren().addAll(header, descLabel);
        return tile;
    }

    private void buildSystemHealthCard() {
        VBox healthCard = new VBox(12);
        healthCard.getStyleClass().add("card");
        healthCard.setStyle("-fx-background-color: #141520; -fx-padding: 18px;");

        HBox header = new HBox(8);
        header.setAlignment(Pos.CENTER_LEFT);
        Label headerTitle = new Label("SYSTEM ENVIRONMENT & RUNTIME HEALTH");
        headerTitle.setStyle("-fx-font-size: 11px; -fx-font-weight: 700; -fx-text-fill: #616478;");
        header.getChildren().add(headerTitle);

        HBox statsRow = new HBox(20);
        statsRow.setAlignment(Pos.CENTER_LEFT);

        boolean isH2 = DatabaseManager.getInstance().isH2Fallback();
        boolean isMem = DatabaseManager.getInstance().isH2InMemory();
        long latency = DatabaseManager.getInstance().pingLatencyMillis();

        VBox stat1 = createStatBlock("DATABASE",
                isH2 ? (isMem ? "H2 In-Memory" : "H2 Disk Fallback") : "MySQL 8.0 (localhost:3306)",
                latency >= 0 ? latency + " ms latency" : "Unresponsive",
                isH2 ? (isMem ? "#F59E0B" : "#38BDF8") : "#10B981");
        VBox stat2 = createStatBlock("EXECUTION POOL", "4 Concurrent Pipelines", "Max 4 Pipelines / 16 Stages", "#38BDF8");
        VBox stat3 = createStatBlock("JAVA RUNTIME", System.getProperty("java.version"), System.getProperty("os.name") + " (" + System.getProperty("os.arch") + ")", "#8B5CF6");
        VBox stat4 = createStatBlock("WORKSPACE ROOT", AppConfig.getInstance().getWorkspaceDir(), "Local process isolation active", "#F43F5E");

        HBox.setHgrow(stat1, Priority.ALWAYS);
        HBox.setHgrow(stat2, Priority.ALWAYS);
        HBox.setHgrow(stat3, Priority.ALWAYS);
        HBox.setHgrow(stat4, Priority.ALWAYS);

        statsRow.getChildren().addAll(stat1, stat2, stat3, stat4);
        healthCard.getChildren().addAll(header, statsRow);
        this.getChildren().add(healthCard);
    }

    private VBox createStatBlock(String label, String value, String sub, String color) {
        VBox box = new VBox(4);
        Label lbl = new Label(label);
        lbl.setStyle("-fx-font-size: 10px; -fx-font-weight: bold; -fx-text-fill: #616478;");

        Label val = new Label(value);
        val.setStyle("-fx-font-size: 13px; -fx-font-weight: 700; -fx-text-fill: #FFFFFF;");

        Label s = new Label(sub);
        s.setStyle(String.format("-fx-font-size: 10px; -fx-text-fill: %s;", color));

        box.getChildren().addAll(lbl, val, s);
        return box;
    }

    private void buildRecentWorkflowsSection() {
        HBox sectionHeader = new HBox(8);
        sectionHeader.setAlignment(Pos.CENTER_LEFT);

        Label title = new Label("RECENT PROJECTS & PIPELINES");
        title.setStyle("-fx-font-size: 11px; -fx-font-weight: 700; -fx-text-fill: #616478;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button viewAllBtn = new Button("View All Projects");
        viewAllBtn.getStyleClass().add("btn-secondary");
        viewAllBtn.setOnAction(e -> mainLayout.navigateToProjects());

        sectionHeader.getChildren().addAll(title, spacer, viewAllBtn);
        this.getChildren().add(sectionHeader);

        List<Project> projects = projectService.getAllProjects();
        if (projects == null || projects.isEmpty()) {
            VBox emptyBox = new VBox(12);
            emptyBox.setAlignment(Pos.CENTER);
            emptyBox.setStyle("-fx-background-color: #181926; -fx-padding: 32px; -fx-background-radius: 10px; -fx-border-color: #27283A;");

            Label emptyTitle = new Label("No Projects Configured Yet");
            emptyTitle.setStyle("-fx-font-size: 15px; -fx-font-weight: 700; -fx-text-fill: #FFFFFF;");

            Label emptySub = new Label("Initialize your first project repository to configure CI/CD delivery pipelines.");
            emptySub.setStyle("-fx-font-size: 12px; -fx-text-fill: #8A8D9E;");

            Button createBtn = new Button("Initialize Project");
            createBtn.getStyleClass().add("btn-primary");
            createBtn.setOnAction(e -> mainLayout.navigateToProjects());

            emptyBox.getChildren().addAll(emptyTitle, emptySub, createBtn);
            this.getChildren().add(emptyBox);
        } else {
            HBox projectCardsRow = new HBox(16);
            int count = 0;
            for (Project p : projects) {
                if (count >= 3) break;
                VBox pCard = createProjectPreviewCard(p);
                HBox.setHgrow(pCard, Priority.ALWAYS);
                projectCardsRow.getChildren().add(pCard);
                count++;
            }
            this.getChildren().add(projectCardsRow);
        }
    }

    private VBox createProjectPreviewCard(Project project) {
        VBox card = new VBox(10);
        card.getStyleClass().add("project-grid-card");
        card.setOnMouseClicked(e -> mainLayout.navigateToProjectDetail(project));

        HBox top = new HBox(8);
        top.setAlignment(Pos.CENTER_LEFT);

        Label name = new Label(project.getName());
        name.getStyleClass().add("project-card-title");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        StatusBadge badge = new StatusBadge(project.getStatus());
        top.getChildren().addAll(name, spacer, badge);

        Label desc = new Label(project.getDescription() != null && !project.getDescription().isEmpty()
                ? project.getDescription() : "Repository: " + project.getRepositoryUrl());
        desc.getStyleClass().add("project-card-subtitle");
        desc.setWrapText(true);
        desc.setMinWidth(0);

        card.getChildren().addAll(top, desc);
        return card;
    }
}
