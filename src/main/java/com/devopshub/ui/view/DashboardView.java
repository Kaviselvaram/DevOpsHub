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
 * Dashboard View matching Presentation Slide 9 specifications.
 * Displays live KPI metrics, recent executions, and recent project status.
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

    private TableView<Execution> recentExecutionsTable;
    private TableView<Project> recentProjectsTable;

    public DashboardView(MainLayoutView mainLayout, DashboardService dashboardService, ExecutionEngine executionEngine) {
        this.mainLayout = mainLayout;
        this.dashboardService = dashboardService;
        this.executionEngine = executionEngine;

        this.setSpacing(24);
        this.setPadding(new Insets(24));
        this.setStyle("-fx-background-color: transparent;");

        buildKpiSection();
        buildContentGrids();
        refreshData();
    }

    private void buildKpiSection() {
        HBox kpiGrid = new HBox(16);
        kpiGrid.setAlignment(Pos.CENTER_LEFT);

        projectsCard = new MetricCard("Projects", "0", "Total active projects",
                SvgIcon.IconName.PROJECTS, Color.web("#0284C7"));

        pipelinesCard = new MetricCard("Pipelines", "0", "Configured delivery pipelines",
                SvgIcon.IconName.PIPELINES, Color.web("#0D9488"));

        successRateCard = new MetricCard("Success Rate", "0%", "Historical delivery reliability",
                SvgIcon.IconName.CHECK_CIRCLE, Color.web("#10B981"));

        avgDurationCard = new MetricCard("Avg Build", "0s", "Average execution duration",
                SvgIcon.IconName.CLOCK, Color.web("#F59E0B"));

        successBuildsCard = new MetricCard("Successful", "0", "Completed passing runs",
                SvgIcon.IconName.CHECK_CIRCLE, Color.web("#059669"));

        failedBuildsCard = new MetricCard("Failed", "0", "Investigated failure runs",
                SvgIcon.IconName.X_CIRCLE, Color.web("#E11D48"));

        kpiGrid.getChildren().addAll(
                projectsCard, pipelinesCard, successRateCard,
                avgDurationCard, successBuildsCard, failedBuildsCard
        );

        this.getChildren().add(kpiGrid);
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
        execTitle.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: #0F172A;");
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
        projTitle.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: #0F172A;");
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
    }
}
