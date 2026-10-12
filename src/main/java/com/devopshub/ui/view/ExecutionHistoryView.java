package com.devopshub.ui.view;

import com.devopshub.model.Execution;
import com.devopshub.model.Pipeline;
import com.devopshub.model.Project;
import com.devopshub.repository.ExecutionRepository;
import com.devopshub.service.PipelineService;
import com.devopshub.service.ProjectService;
import com.devopshub.ui.component.EmptyStateView;
import com.devopshub.ui.component.StatusBadge;
import com.devopshub.ui.component.SvgIcon;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;

import java.text.SimpleDateFormat;
import java.util.List;

/**
 * Historical Execution Audit Log View:
 * Multi-criteria search, project/pipeline filters, status toggles, and detailed execution drill-down.
 */
public class ExecutionHistoryView extends VBox {

    private static final SimpleDateFormat DATE_FMT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    private final MainLayoutView mainLayout;
    private final ExecutionRepository executionRepository;
    private final ProjectService projectService;
    private final PipelineService pipelineService;

    private TextField searchField;
    private ComboBox<String> statusFilter;
    private ComboBox<String> projectFilter;
    private TableView<Execution> tableView;
    private StackPane tableContainer;

    public ExecutionHistoryView(MainLayoutView mainLayout, com.devopshub.service.DashboardService dashboardService) {
        this.mainLayout = mainLayout;
        this.executionRepository = new ExecutionRepository();
        this.projectService = new ProjectService();
        this.pipelineService = new PipelineService();

        this.setSpacing(16);
        this.setPadding(new Insets(24));
        this.setStyle("-fx-background-color: transparent;");

        buildToolbar();
        buildTable();
        refresh();
    }

    private void buildToolbar() {
        HBox toolbar = new HBox(12);
        toolbar.setAlignment(Pos.CENTER_LEFT);

        searchField = new TextField();
        searchField.setPromptText("Search by build #, pipeline, or commit...");
        searchField.setPrefWidth(260);
        searchField.textProperty().addListener((obs, oldVal, newVal) -> applyFilter());

        Label statusLabel = new Label("Status:");
        statusLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #475569;");

        statusFilter = new ComboBox<>();
        statusFilter.getItems().addAll("ALL", "SUCCESS", "FAILED", "RUNNING", "CANCELLED");
        statusFilter.setValue("ALL");
        statusFilter.setOnAction(e -> applyFilter());

        Label projLabel = new Label("Project:");
        projLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #475569;");

        projectFilter = new ComboBox<>();
        projectFilter.setPrefWidth(180);
        projectFilter.getItems().add("ALL PROJECTS");
        projectFilter.setValue("ALL PROJECTS");
        for (Project p : projectService.getAllProjects()) {
            projectFilter.getItems().add(p.getName());
        }
        projectFilter.setOnAction(e -> applyFilter());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button refreshBtn = new Button("Refresh");
        refreshBtn.getStyleClass().add("btn-secondary");
        refreshBtn.setGraphic(new SvgIcon(SvgIcon.IconName.REFRESH, 13, Color.web("#64748B")));
        refreshBtn.setOnAction(e -> refresh());

        toolbar.getChildren().addAll(searchField, statusLabel, statusFilter, projLabel, projectFilter, spacer, refreshBtn);
        this.getChildren().add(toolbar);
    }

    private void buildTable() {
        tableContainer = new StackPane();
        VBox.setVgrow(tableContainer, Priority.ALWAYS);

        tableView = new TableView<>();
        tableView.getStyleClass().add("card");
        tableView.setPrefHeight(600);

        // Double-click row opens Execution Details
        tableView.setRowFactory(tv -> {
            TableRow<Execution> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && (!row.isEmpty())) {
                    Execution ex = row.getItem();
                    mainLayout.navigateToExecutionDetail(ex.getId());
                }
            });
            return row;
        });

        TableColumn<Execution, String> colBuild = new TableColumn<>("Build");
        colBuild.setCellValueFactory(d -> new SimpleStringProperty("#" + d.getValue().getExecutionNumber()));
        colBuild.setPrefWidth(80);

        TableColumn<Execution, String> colPipeline = new TableColumn<>("Pipeline");
        colPipeline.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getPipelineName()));
        colPipeline.setPrefWidth(180);

        TableColumn<Execution, String> colProject = new TableColumn<>("Project");
        colProject.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getProjectName()));
        colProject.setPrefWidth(160);

        TableColumn<Execution, String> colTrigger = new TableColumn<>("Trigger");
        colTrigger.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getTriggerType().name()));
        colTrigger.setPrefWidth(100);

        TableColumn<Execution, String> colDuration = new TableColumn<>("Duration");
        colDuration.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getDurationSeconds() + "s"));
        colDuration.setPrefWidth(90);

        TableColumn<Execution, String> colDate = new TableColumn<>("Timestamp");
        colDate.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getStartedAt() != null ?
                DATE_FMT.format(d.getValue().getStartedAt()) : "-"));
        colDate.setPrefWidth(160);

        TableColumn<Execution, Void> colStatus = new TableColumn<>("Status");
        colStatus.setPrefWidth(120);
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
        colAction.setPrefWidth(100);
        colAction.setCellFactory(col -> new TableCell<>() {
            private final Button inspectBtn = new Button("Inspect");
            {
                inspectBtn.getStyleClass().add("btn-secondary");
                inspectBtn.setStyle("-fx-padding: 3px 8px; -fx-font-size: 11px;");
            }
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setGraphic(null);
                } else {
                    Execution ex = getTableRow().getItem();
                    inspectBtn.setOnAction(e -> mainLayout.navigateToExecutionDetail(ex.getId()));
                    setGraphic(inspectBtn);
                }
            }
        });

        tableView.getColumns().addAll(colBuild, colPipeline, colProject, colTrigger, colDuration, colDate, colStatus, colAction);
        tableContainer.getChildren().add(tableView);
        this.getChildren().add(tableContainer);
    }

    private void applyFilter() {
        String q = searchField != null && searchField.getText() != null ? searchField.getText().trim().toLowerCase() : "";
        String status = statusFilter != null ? statusFilter.getValue() : "ALL";
        String proj = projectFilter != null ? projectFilter.getValue() : "ALL PROJECTS";

        List<Execution> all = executionRepository.findAll();
        List<Execution> filtered = all.stream().filter(e -> {
            boolean matchQ = q.isEmpty() ||
                    String.valueOf(e.getExecutionNumber()).contains(q) ||
                    (e.getPipelineName() != null && e.getPipelineName().toLowerCase().contains(q)) ||
                    (e.getProjectName() != null && e.getProjectName().toLowerCase().contains(q));
            boolean matchStatus = "ALL".equals(status) || e.getStatus().name().equalsIgnoreCase(status);
            boolean matchProj = "ALL PROJECTS".equals(proj) || (e.getProjectName() != null && e.getProjectName().equalsIgnoreCase(proj));
            return matchQ && matchStatus && matchProj;
        }).toList();

        tableView.getItems().setAll(filtered);

        if (filtered.isEmpty()) {
            if (tableContainer.getChildren().size() == 1) {
                EmptyStateView emptyState = new EmptyStateView(
                        SvgIcon.IconName.EXECUTIONS,
                        "No Executions Found",
                        "No execution runs match the specified search or filter criteria.",
                        "View All Pipelines",
                        mainLayout::navigateToPipelines
                );
                tableContainer.getChildren().add(emptyState);
            }
        } else {
            if (tableContainer.getChildren().size() > 1) {
                tableContainer.getChildren().remove(1);
            }
        }
    }

    public void refresh() {
        applyFilter();
    }
}
