package com.devopshub.ui.view;

import com.devopshub.execution.ExecutionEngine;
import com.devopshub.model.DashboardMetrics;
import com.devopshub.model.Execution;
import com.devopshub.model.Project;
import com.devopshub.service.DashboardService;
import com.devopshub.ui.component.MetricCard;
import com.devopshub.ui.component.StatusBadge;
import com.devopshub.ui.component.SvgIcon;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;

import java.util.List;

/**
 * Operational Dashboard View matching visual reference specification:
 * Header action bar, KPI metrics cards, 3 Team/Pipeline status cards with traffic lights,
 * real execution audit table, and active projects overview.
 */
public class DashboardView extends VBox {

    private final MainLayoutView mainLayout;
    private final DashboardService dashboardService;
    private final ExecutionEngine executionEngine;

    private MetricCard projectsCard;
    private MetricCard pipelinesCard;
    private MetricCard successRateCard;
    private MetricCard avgDurationCard;
    private MetricCard successBuildsCard;
    private MetricCard failedBuildsCard;

    // Team Operational Cards matching reference
    private VBox buildTeamCard;
    private VBox qaTeamCard;
    private VBox opsTeamCard;

    private TableView<Execution> recentExecutionsTable;
    private TableView<Project> recentProjectsTable;

    public DashboardView(MainLayoutView mainLayout, DashboardService dashboardService, ExecutionEngine executionEngine) {
        this.mainLayout = mainLayout;
        this.dashboardService = dashboardService;
        this.executionEngine = executionEngine;

        this.setSpacing(20);
        this.setPadding(new Insets(24));
        this.setStyle("-fx-background-color: transparent;");

        buildHeaderActionBar();
        buildKpiSection();
        buildTeamOperationalSection();
        buildContentGrids();
        refreshData();
    }

    private void buildHeaderActionBar() {
        HBox topBar = new HBox(12);
        topBar.setAlignment(Pos.CENTER_LEFT);

        VBox titleBox = new VBox(2);
        Label subtitle = new Label("Operational truth:");
        subtitle.setStyle("-fx-font-size: 11px; -fx-text-fill: #8A8D9E;");
        Label title = new Label("DevOpsHub Dashboard");
        title.setStyle("-fx-font-size: 18px; -fx-font-weight: 800; -fx-text-fill: #FFFFFF;");
        titleBox.getChildren().addAll(subtitle, title);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        String todayDate = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy"));
        Label datePill = new Label(todayDate);
        datePill.getStyleClass().add("header-date-badge");

        Button triggerRunBtn = new Button("+ Trigger Run");
        triggerRunBtn.getStyleClass().add("btn-primary");
        triggerRunBtn.setOnAction(e -> mainLayout.navigateToExecutions());

        Button newProjBtn = new Button("New Project");
        newProjBtn.getStyleClass().add("btn-secondary");
        newProjBtn.setOnAction(e -> mainLayout.navigateToProjects());

        Button refreshBtn = new Button("Refresh");
        refreshBtn.getStyleClass().add("btn-secondary");
        refreshBtn.setGraphic(new SvgIcon(SvgIcon.IconName.REFRESH, 13, Color.web("#8A8D9E")));
        refreshBtn.setOnAction(e -> refreshData());

        topBar.getChildren().addAll(titleBox, spacer, datePill, triggerRunBtn, newProjBtn, refreshBtn);
        this.getChildren().add(topBar);
    }

    private void buildKpiSection() {
        HBox kpiGrid = new HBox(16);
        kpiGrid.setAlignment(Pos.CENTER_LEFT);

        projectsCard = new MetricCard("Projects", "0", "Total active repositories",
                SvgIcon.IconName.PROJECTS, Color.web("#38BDF8"));

        pipelinesCard = new MetricCard("Pipelines", "0", "Configured delivery flows",
                SvgIcon.IconName.PIPELINES, Color.web("#8B5CF6"));

        successRateCard = new MetricCard("Success Rate", "0%", "Historical build reliability",
                SvgIcon.IconName.CHECK_CIRCLE, Color.web("#10B981"));

        avgDurationCard = new MetricCard("Avg Duration", "0s", "Average execution runtime",
                SvgIcon.IconName.CLOCK, Color.web("#F59E0B"));

        successBuildsCard = new MetricCard("Successful", "0", "Completed passing runs",
                SvgIcon.IconName.CHECK_CIRCLE, Color.web("#10B981"));

        failedBuildsCard = new MetricCard("Failed", "0", "Investigated failure runs",
                SvgIcon.IconName.X_CIRCLE, Color.web("#F43F5E"));

        kpiGrid.getChildren().addAll(
                projectsCard, pipelinesCard, successRateCard,
                avgDurationCard, successBuildsCard, failedBuildsCard
        );

        this.getChildren().add(kpiGrid);
    }

    private HBox teamRow;

    /**
     * Team Operational Cards matching visual reference:
     * Calculated dynamically from stage execution telemetry (Build, QA, Deployment).
     */
    private void buildTeamOperationalSection() {
        teamRow = new HBox(16);
        teamRow.setAlignment(Pos.CENTER_LEFT);
        updateTeamCards();
        this.getChildren().add(teamRow);
    }

    private void updateTeamCards() {
        if (teamRow == null) return;
        teamRow.getChildren().clear();

        var buildStats = dashboardService.getBuildStageStats();
        var testStats = dashboardService.getTestStageStats();
        var deployStats = dashboardService.getDeployStageStats();

        buildTeamCard = createTeamCard("Build & Compile Operations",
                buildStats.getFailedRuns() + " issues",
                buildStats.getRunningRuns() + " running",
                (buildStats.getTotalRuns() - buildStats.getFailedRuns()) + " completed",
                String.format("%.1fs runtime", buildStats.getTotalDurationSeconds()),
                buildStats.getTotalRuns() + " stages",
                buildStats.getTotalRuns() == 0 ? "Ready" : (buildStats.getFailedRuns() == 0 ? "100% Reliable" : "Needs Review"));

        qaTeamCard = createTeamCard("Test & Quality Assurance",
                testStats.getFailedRuns() + " failed",
                testStats.getRunningRuns() + " active",
                (testStats.getTotalRuns() - testStats.getFailedRuns()) + " passing",
                String.format("%.1fs runtime", testStats.getTotalDurationSeconds()),
                testStats.getTotalRuns() + " suites",
                testStats.getTotalRuns() == 0 ? "Ready" : (testStats.getFailedRuns() == 0 ? "Passing" : "Failing"));

        opsTeamCard = createTeamCard("Packaging & Deployment Ops",
                deployStats.getFailedRuns() + " errors",
                deployStats.getRunningRuns() + " pending",
                (deployStats.getTotalRuns() - deployStats.getFailedRuns()) + " releases",
                String.format("%.1fs runtime", deployStats.getTotalDurationSeconds()),
                deployStats.getTotalRuns() + " targets",
                deployStats.getTotalRuns() == 0 ? "Ready" : "Verified");

        HBox.setHgrow(buildTeamCard, Priority.ALWAYS);
        HBox.setHgrow(qaTeamCard, Priority.ALWAYS);
        HBox.setHgrow(opsTeamCard, Priority.ALWAYS);

        teamRow.getChildren().addAll(buildTeamCard, qaTeamCard, opsTeamCard);
    }

    private VBox createTeamCard(String title, String redText, String amberText, String greenText,
                                String stat1, String stat2, String stat3) {
        VBox card = new VBox(10);
        card.getStyleClass().add("card");
        card.setStyle("-fx-background-color: #181926; -fx-border-color: #26283C; -fx-padding: 14px;");

        HBox header = new HBox(8);
        header.setAlignment(Pos.CENTER_LEFT);

        Label titleLabel = new Label(title);
        titleLabel.setStyle("-fx-font-size: 13px; -fx-font-weight: 700; -fx-text-fill: #FFFFFF;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox avatars = createMiniAvatarCluster();
        header.getChildren().addAll(titleLabel, spacer, avatars);

        VBox statusBox = new VBox(5);
        statusBox.getChildren().addAll(
                createTrafficRow("#F43F5E", redText),
                createTrafficRow("#F59E0B", amberText),
                createTrafficRow("#10B981", greenText)
        );

        HBox footer = new HBox(12);
        footer.setAlignment(Pos.CENTER_LEFT);
        footer.setStyle("-fx-padding: 8px 0 0 0; -fx-border-color: #222436; -fx-border-width: 1px 0 0 0;");

        Label s1 = new Label(stat1);
        s1.setStyle("-fx-font-size: 10px; -fx-text-fill: #8A8D9E;");
        Label s2 = new Label(stat2);
        s2.setStyle("-fx-font-size: 10px; -fx-text-fill: #8A8D9E;");
        Label s3 = new Label(stat3);
        s3.setStyle("-fx-font-size: 10px; -fx-text-fill: #8A8D9E;");

        footer.getChildren().addAll(s1, s2, s3);
        card.getChildren().addAll(header, statusBox, footer);
        return card;
    }

    private HBox createTrafficRow(String color, String text) {
        HBox row = new HBox(8);
        row.setAlignment(Pos.CENTER_LEFT);

        Region dot = new Region();
        dot.setStyle(String.format("-fx-background-color: %s; -fx-background-radius: 4px; -fx-min-width: 8px; -fx-min-height: 8px; -fx-max-width: 8px; -fx-max-height: 8px;", color));

        Label lbl = new Label(text);
        lbl.setStyle("-fx-font-size: 11px; -fx-text-fill: #A0A5BA;");

        row.getChildren().addAll(dot, lbl);
        return row;
    }

    private HBox createMiniAvatarCluster() {
        HBox cluster = new HBox(-4);
        cluster.setAlignment(Pos.CENTER_RIGHT);

        String[] colors = {"#38BDF8", "#8B5CF6"};
        String[] inits = {"NJ", "MP"};
        for (int i = 0; i < inits.length; i++) {
            StackPane av = new StackPane();
            av.setStyle(String.format("-fx-background-color: %s; -fx-min-width: 18px; -fx-min-height: 18px; -fx-max-width: 18px; -fx-max-height: 18px; -fx-background-radius: 9px;", colors[i]));
            Label l = new Label(inits[i]);
            l.setStyle("-fx-font-size: 7px; -fx-font-weight: bold; -fx-text-fill: #FFFFFF;");
            av.getChildren().add(l);
            cluster.getChildren().add(av);
        }
        return cluster;
    }

    private void buildContentGrids() {
        HBox contentGrid = new HBox(20);
        contentGrid.setAlignment(Pos.TOP_LEFT);
        VBox.setVgrow(contentGrid, Priority.ALWAYS);

        // Left Column: Recent Executions Feed
        VBox execSection = new VBox(12);
        execSection.getStyleClass().add("card");
        HBox.setHgrow(execSection, Priority.ALWAYS);
        execSection.setPrefWidth(550);

        HBox execHeader = new HBox();
        execHeader.setAlignment(Pos.CENTER_LEFT);
        Label execTitle = new Label("Recent Executions");
        execTitle.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: #FFFFFF;");
        Region spacer1 = new Region();
        HBox.setHgrow(spacer1, Priority.ALWAYS);

        Button viewAllExecs = new Button("View All");
        viewAllExecs.getStyleClass().add("btn-secondary");
        viewAllExecs.setOnAction(e -> mainLayout.navigateToExecutions());

        execHeader.getChildren().addAll(execTitle, spacer1, viewAllExecs);

        recentExecutionsTable = new TableView<>();
        recentExecutionsTable.setPrefHeight(280);

        TableColumn<Execution, String> colBuild = new TableColumn<>("Build");
        colBuild.setCellValueFactory(d -> new SimpleStringProperty("#" + d.getValue().getExecutionNumber()));
        colBuild.setPrefWidth(70);

        TableColumn<Execution, String> colPipeline = new TableColumn<>("Pipeline");
        colPipeline.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getPipelineName()));
        colPipeline.setPrefWidth(150);

        TableColumn<Execution, String> colDuration = new TableColumn<>("Duration");
        colDuration.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getDurationSeconds() + "s"));
        colDuration.setPrefWidth(80);

        TableColumn<Execution, Void> colStatus = new TableColumn<>("Status");
        colStatus.setPrefWidth(110);
        colStatus.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setGraphic(null);
                } else {
                    Execution ex = getTableRow().getItem();
                    setGraphic(StatusBadge.of(ex.getStatus()));
                }
            }
        });

        TableColumn<Execution, Void> colAction = new TableColumn<>("Action");
        colAction.setPrefWidth(90);
        colAction.setCellFactory(col -> new TableCell<>() {
            private final Button btn = new Button("Inspect");
            {
                btn.getStyleClass().add("btn-secondary");
                btn.setStyle("-fx-padding: 3px 8px; -fx-font-size: 11px;");
            }
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setGraphic(null);
                } else {
                    Execution ex = getTableRow().getItem();
                    btn.setOnAction(e -> mainLayout.navigateToExecutionDetail(ex.getId()));
                    setGraphic(btn);
                }
            }
        });

        recentExecutionsTable.getColumns().addAll(colBuild, colPipeline, colDuration, colStatus, colAction);
        execSection.getChildren().addAll(execHeader, recentExecutionsTable);

        // Right Column: Active Projects Table
        VBox projSection = new VBox(12);
        projSection.getStyleClass().add("card");
        HBox.setHgrow(projSection, Priority.ALWAYS);
        projSection.setPrefWidth(450);

        HBox projHeader = new HBox();
        projHeader.setAlignment(Pos.CENTER_LEFT);
        Label projTitle = new Label("Recent Projects");
        projTitle.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: #FFFFFF;");
        Region spacer2 = new Region();
        HBox.setHgrow(spacer2, Priority.ALWAYS);

        Button viewAllProjs = new Button("View All");
        viewAllProjs.getStyleClass().add("btn-secondary");
        viewAllProjs.setOnAction(e -> mainLayout.navigateToProjects());

        projHeader.getChildren().addAll(projTitle, spacer2, viewAllProjs);

        recentProjectsTable = new TableView<>();
        recentProjectsTable.setPrefHeight(280);

        TableColumn<Project, String> colProjName = new TableColumn<>("Project");
        colProjName.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getName()));
        colProjName.setPrefWidth(160);

        TableColumn<Project, String> colType = new TableColumn<>("Type");
        colType.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getProjectType()));
        colType.setPrefWidth(90);

        TableColumn<Project, Void> colProjStatus = new TableColumn<>("Status");
        colProjStatus.setPrefWidth(110);
        colProjStatus.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setGraphic(null);
                } else {
                    Project p = getTableRow().getItem();
                    setGraphic(new StatusBadge(p.getStatus()));
                }
            }
        });

        TableColumn<Project, Void> colProjAction = new TableColumn<>("Action");
        colProjAction.setPrefWidth(90);
        colProjAction.setCellFactory(col -> new TableCell<>() {
            private final Button btn = new Button("Inspect");
            {
                btn.getStyleClass().add("btn-secondary");
                btn.setStyle("-fx-padding: 3px 8px; -fx-font-size: 11px;");
            }
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setGraphic(null);
                } else {
                    Project p = getTableRow().getItem();
                    btn.setOnAction(e -> mainLayout.navigateToProjectDetail(p));
                    setGraphic(btn);
                }
            }
        });

        recentProjectsTable.setRowFactory(tv -> {
            TableRow<Project> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && (!row.isEmpty())) {
                    Project p = row.getItem();
                    mainLayout.navigateToProjectDetail(p);
                }
            });
            return row;
        });

        recentProjectsTable.getColumns().addAll(colProjName, colType, colProjStatus, colProjAction);
        projSection.getChildren().addAll(projHeader, recentProjectsTable);

        contentGrid.getChildren().addAll(execSection, projSection);
        this.getChildren().add(contentGrid);
    }

    public void refreshData() {
        DashboardMetrics m = dashboardService.getMetrics();
        projectsCard.setValue(String.valueOf(m.getTotalProjects()));
        pipelinesCard.setValue(String.valueOf(m.getTotalPipelines()));
        successRateCard.setValue(m.getSuccessRate() + "%");
        avgDurationCard.setValue(m.getAvgDurationSeconds() + "s");
        successBuildsCard.setValue(String.valueOf(m.getSuccessfulExecutions()));
        failedBuildsCard.setValue(String.valueOf(m.getFailedExecutions()));

        List<Execution> recentExecs = dashboardService.getRecentExecutions(8);
        recentExecutionsTable.getItems().setAll(recentExecs);

        List<Project> recentProjs = dashboardService.getRecentProjects();
        recentProjectsTable.getItems().setAll(recentProjs);

        updateTeamCards();
    }
}
