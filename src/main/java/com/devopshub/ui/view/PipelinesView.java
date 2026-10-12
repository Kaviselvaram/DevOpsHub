package com.devopshub.ui.view;

import com.devopshub.execution.ExecutionEngine;
import com.devopshub.model.Execution;
import com.devopshub.model.Pipeline;
import com.devopshub.model.Project;
import com.devopshub.model.TriggerType;
import com.devopshub.service.PipelineService;
import com.devopshub.service.ProjectService;
import com.devopshub.ui.component.*;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;

import java.util.List;

/**
 * Pipelines List View: Manage delivery pipelines, trigger executions, clone pipelines, and configure topologies.
 */
public class PipelinesView extends VBox {

    private final MainLayoutView mainLayout;
    private final PipelineService pipelineService;
    private final ExecutionEngine executionEngine;
    private final ProjectService projectService;

    private TextField searchField;
    private ComboBox<String> projectFilter;
    private TableView<Pipeline> tableView;
    private StackPane tableContainer;

    public PipelinesView(MainLayoutView mainLayout, PipelineService pipelineService, ExecutionEngine executionEngine) {
        this.mainLayout = mainLayout;
        this.pipelineService = pipelineService;
        this.executionEngine = executionEngine;
        this.projectService = new ProjectService();

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
        searchField.setPromptText("Search pipelines by name or description...");
        searchField.setPrefWidth(280);
        searchField.textProperty().addListener((obs, oldVal, newVal) -> applyFilters());

        Label projLabel = new Label("Project:");
        projLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #475569;");

        projectFilter = new ComboBox<>();
        projectFilter.setPrefWidth(200);
        projectFilter.getItems().add("ALL PROJECTS");
        projectFilter.setValue("ALL PROJECTS");
        List<Project> projs = projectService.getAllProjects();
        for (Project p : projs) {
            projectFilter.getItems().add(p.getName());
        }
        projectFilter.setOnAction(e -> applyFilters());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button newPipeBtn = new Button("New Pipeline");
        newPipeBtn.getStyleClass().add("btn-primary");
        newPipeBtn.setGraphic(new SvgIcon(SvgIcon.IconName.PLUS, 13, Color.WHITE));
        newPipeBtn.setOnAction(e -> mainLayout.navigateToPipelineBuilder(null));

        Button refreshBtn = new Button("Refresh");
        refreshBtn.getStyleClass().add("btn-secondary");
        refreshBtn.setGraphic(new SvgIcon(SvgIcon.IconName.REFRESH, 13, Color.web("#64748B")));
        refreshBtn.setOnAction(e -> refresh());

        toolbar.getChildren().addAll(searchField, projLabel, projectFilter, spacer, newPipeBtn, refreshBtn);
        this.getChildren().add(toolbar);
    }

    private void buildTable() {
        tableContainer = new StackPane();
        VBox.setVgrow(tableContainer, Priority.ALWAYS);

        tableView = new TableView<>();
        tableView.getStyleClass().add("card");
        tableView.setPrefHeight(600);

        // Double-click row opens Pipeline Details
        tableView.setRowFactory(tv -> {
            TableRow<Pipeline> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && (!row.isEmpty())) {
                    Pipeline p = row.getItem();
                    mainLayout.navigateToPipelineDetail(p);
                }
            });
            return row;
        });

        TableColumn<Pipeline, String> colName = new TableColumn<>("Pipeline Name");
        colName.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getName()));
        colName.setPrefWidth(180);

        TableColumn<Pipeline, String> colProject = new TableColumn<>("Project");
        colProject.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getProjectName()));
        colProject.setPrefWidth(160);

        TableColumn<Pipeline, String> colMode = new TableColumn<>("Execution Mode");
        colMode.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getExecutionMode()));
        colMode.setPrefWidth(130);

        TableColumn<Pipeline, Number> colStages = new TableColumn<>("Stages");
        colStages.setCellValueFactory(d -> new SimpleIntegerProperty(d.getValue().getStages() != null ? d.getValue().getStages().size() : 0));
        colStages.setPrefWidth(70);

        TableColumn<Pipeline, Number> colRuns = new TableColumn<>("Runs");
        colRuns.setCellValueFactory(d -> new SimpleIntegerProperty(d.getValue().getExecutionCount()));
        colRuns.setPrefWidth(70);

        TableColumn<Pipeline, Void> colStatus = new TableColumn<>("Last Status");
        colStatus.setPrefWidth(110);
        colStatus.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setGraphic(null);
                } else {
                    Pipeline p = getTableRow().getItem();
                    setGraphic(new StatusBadge(p.getLastExecutionStatus()));
                }
            }
        });

        TableColumn<Pipeline, Void> colActions = new TableColumn<>("Actions");
        colActions.setPrefWidth(260);
        colActions.setCellFactory(col -> new TableCell<>() {
            private final Button runBtn = new Button("Run");
            private final Button viewBtn = new Button("View");
            private final Button configBtn = new Button("Edit");
            private final Button dupBtn = new Button("Copy");
            private final Button deleteBtn = new Button();
            private final HBox box = new HBox(6, runBtn, viewBtn, configBtn, dupBtn, deleteBtn);
            {
                runBtn.getStyleClass().add("btn-primary");
                runBtn.setGraphic(new SvgIcon(SvgIcon.IconName.PLAY, 10, Color.WHITE));
                runBtn.setStyle("-fx-padding: 3px 8px; -fx-font-size: 11px;");

                viewBtn.getStyleClass().add("btn-secondary");
                viewBtn.setStyle("-fx-padding: 3px 8px; -fx-font-size: 11px;");

                configBtn.getStyleClass().add("btn-secondary");
                configBtn.setStyle("-fx-padding: 3px 8px; -fx-font-size: 11px;");

                dupBtn.getStyleClass().add("btn-secondary");
                dupBtn.setStyle("-fx-padding: 3px 8px; -fx-font-size: 11px;");

                deleteBtn.getStyleClass().add("btn-secondary");
                deleteBtn.setGraphic(new SvgIcon(SvgIcon.IconName.TRASH, 11, Color.web("#E11D48")));
                deleteBtn.setStyle("-fx-padding: 3px 6px;");
                deleteBtn.setTooltip(new Tooltip("Delete Pipeline"));
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setGraphic(null);
                } else {
                    Pipeline p = getTableRow().getItem();
                    runBtn.setOnAction(e -> {
                        mainLayout.showToast("Starting pipeline execution...", ToastNotification.ToastType.INFO);
                        Execution ex = executionEngine.startExecution(p, TriggerType.MANUAL, null, null);
                        mainLayout.navigateToExecutionDetail(ex.getId());
                    });
                    viewBtn.setOnAction(e -> mainLayout.navigateToPipelineDetail(p));
                    configBtn.setOnAction(e -> mainLayout.navigateToPipelineBuilder(p));
                    dupBtn.setOnAction(e -> {
                        try {
                            Pipeline copy = pipelineService.duplicatePipeline(p.getId());
                            mainLayout.showToast("Pipeline duplicated as: " + copy.getName(), ToastNotification.ToastType.SUCCESS);
                            refresh();
                        } catch (Exception ex) {
                            mainLayout.showToast("Duplicate error: " + ex.getMessage(), ToastNotification.ToastType.ERROR);
                        }
                    });
                    deleteBtn.setOnAction(e -> {
                        boolean confirmed = ConfirmationDialog.show(mainLayout.getPrimaryStage(),
                                "Delete Pipeline",
                                "Are you sure you want to delete pipeline '" + p.getName() + "'?",
                                "Delete Pipeline");
                        if (confirmed) {
                            pipelineService.deletePipeline(p.getId());
                            mainLayout.showToast("Pipeline deleted", ToastNotification.ToastType.SUCCESS);
                            refresh();
                        }
                    });
                    setGraphic(box);
                }
            }
        });

        tableView.getColumns().addAll(colName, colProject, colMode, colStages, colRuns, colStatus, colActions);
        tableContainer.getChildren().add(tableView);
        this.getChildren().add(tableContainer);
    }

    private void applyFilters() {
        String q = searchField != null && searchField.getText() != null ? searchField.getText().trim().toLowerCase() : "";
        String proj = projectFilter != null ? projectFilter.getValue() : "ALL PROJECTS";

        List<Pipeline> all = pipelineService.getAllPipelines();
        List<Pipeline> filtered = all.stream().filter(p -> {
            boolean matchQ = q.isEmpty() ||
                    p.getName().toLowerCase().contains(q) ||
                    (p.getDescription() != null && p.getDescription().toLowerCase().contains(q));
            boolean matchProj = "ALL PROJECTS".equals(proj) || (p.getProjectName() != null && p.getProjectName().equalsIgnoreCase(proj));
            return matchQ && matchProj;
        }).toList();

        tableView.getItems().setAll(filtered);

        if (filtered.isEmpty()) {
            if (tableContainer.getChildren().size() == 1) {
                EmptyStateView emptyState = new EmptyStateView(
                        SvgIcon.IconName.PIPELINES,
                        "No Pipelines Found",
                        "No pipelines match the current search filters. Create a new pipeline to automate tasks.",
                        "Create New Pipeline",
                        () -> mainLayout.navigateToPipelineBuilder(null)
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
        applyFilters();
    }
}
