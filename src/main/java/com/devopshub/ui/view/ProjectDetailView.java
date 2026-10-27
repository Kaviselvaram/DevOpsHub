package com.devopshub.ui.view;

import com.devopshub.execution.ExecutionEngine;
import com.devopshub.model.Execution;
import com.devopshub.model.Pipeline;
import com.devopshub.model.Project;
import com.devopshub.model.TriggerType;
import com.devopshub.repository.ExecutionRepository;
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

import java.text.SimpleDateFormat;
import java.util.List;

/**
 * Dedicated Project Detail View:
 * Full project metadata, associated delivery pipelines, and historical execution records.
 */
public class ProjectDetailView extends VBox {

    private static final SimpleDateFormat DATE_FMT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    private final MainLayoutView mainLayout;
    private final Project project;
    private final ProjectService projectService;
    private final PipelineService pipelineService;
    private final ExecutionRepository executionRepository;
    private final ExecutionEngine executionEngine;

    private TableView<Pipeline> pipelinesTable;
    private TableView<Execution> executionsTable;

    public ProjectDetailView(MainLayoutView mainLayout, Project project,
                             ProjectService projectService, PipelineService pipelineService,
                             ExecutionEngine executionEngine) {
        this.mainLayout = mainLayout;
        this.project = project;
        this.projectService = projectService;
        this.pipelineService = pipelineService;
        this.executionRepository = new ExecutionRepository();
        this.executionEngine = executionEngine;

        this.setSpacing(20);
        this.setPadding(new Insets(24));
        this.setStyle("-fx-background-color: transparent;");

        buildBreadcrumb();
        buildHeaderCard();
        buildMetricsRow();
        buildSections();
        loadData();
    }

    private void buildBreadcrumb() {
        HBox breadcrumb = new HBox(6);
        breadcrumb.getStyleClass().add("breadcrumb-bar");

        Label parentLink = new Label("Projects");
        parentLink.getStyleClass().add("breadcrumb-item");
        parentLink.setOnMouseClicked(e -> mainLayout.navigateToProjects());

        Label separator = new Label("/");
        separator.getStyleClass().add("breadcrumb-separator");

        Label current = new Label(project.getName());
        current.getStyleClass().add("breadcrumb-current");

        breadcrumb.getChildren().addAll(parentLink, separator, current);
        this.getChildren().add(breadcrumb);
    }

    private void buildHeaderCard() {
        HBox card = new HBox(16);
        card.getStyleClass().add("card");
        card.setAlignment(Pos.CENTER_LEFT);

        VBox info = new VBox(4);
        HBox titleRow = new HBox(10);
        titleRow.setAlignment(Pos.CENTER_LEFT);

        Label nameLabel = new Label(project.getName());
        nameLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: 800; -fx-text-fill: #FFFFFF;");

        StatusBadge statusBadge = new StatusBadge(project.getStatus());
        titleRow.getChildren().addAll(nameLabel, statusBadge);

        Label descLabel = new Label(project.getDescription() != null && !project.getDescription().isEmpty()
                ? project.getDescription() : "No description provided.");
        descLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: #8A8D9E;");

        HBox metaRow = new HBox(16);
        metaRow.setAlignment(Pos.CENTER_LEFT);
        metaRow.setStyle("-fx-padding: 4px 0 0 0;");

        Label repoLabel = new Label("Repo: " + project.getRepositoryUrl() + " (" + project.getDefaultBranch() + ")");
        repoLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #64748B; -fx-font-family: Menlo, monospace;");
        repoLabel.setWrapText(true);
        repoLabel.setMinWidth(0);

        Button copyUrlBtn = new Button();
        copyUrlBtn.setGraphic(new SvgIcon(SvgIcon.IconName.SHARE, 11, Color.web("#8A8D9E")));
        copyUrlBtn.getStyleClass().add("btn-secondary");
        copyUrlBtn.setStyle("-fx-padding: 2px 6px;");
        copyUrlBtn.setTooltip(new Tooltip("Copy Repository URL"));
        copyUrlBtn.setOnAction(e -> {
            javafx.scene.input.ClipboardContent cc = new javafx.scene.input.ClipboardContent();
            cc.putString(project.getRepositoryUrl());
            javafx.scene.input.Clipboard.getSystemClipboard().setContent(cc);
            mainLayout.showToast("Repository URL copied to clipboard!", ToastNotification.ToastType.SUCCESS);
        });

        Label typeLabel = new Label("Type: " + project.getProjectType());
        typeLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #0284C7; -fx-font-weight: bold;");

        metaRow.getChildren().addAll(repoLabel, copyUrlBtn, typeLabel);
        info.setMinWidth(0);
        HBox.setHgrow(info, Priority.ALWAYS);
        info.getChildren().addAll(titleRow, descLabel, metaRow);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox actions = new HBox(10);
        actions.setAlignment(Pos.CENTER_RIGHT);

        Button newPipeBtn = new Button("New Pipeline");
        newPipeBtn.getStyleClass().add("btn-primary");
        newPipeBtn.setGraphic(new SvgIcon(SvgIcon.IconName.PLUS, 12, Color.WHITE));
        newPipeBtn.setOnAction(e -> mainLayout.navigateToPipelineBuilder(null));

        Button backBtn = new Button("Back");
        backBtn.getStyleClass().add("btn-secondary");
        backBtn.setOnAction(e -> mainLayout.navigateToProjects());

        actions.getChildren().addAll(newPipeBtn, backBtn);
        card.getChildren().addAll(info, spacer, actions);
        this.getChildren().add(card);
    }

    private void buildMetricsRow() {
        HBox row = new HBox(16);
        List<Pipeline> pipes = pipelineService.getPipelinesByProjectId(project.getId());
        List<Execution> execs = executionRepository.findByProjectId(project.getId());

        long successCount = execs.stream().filter(e -> e.getStatus().name().equalsIgnoreCase("SUCCESS")).count();
        double successRate = execs.isEmpty() ? 0.0 : ((double) successCount / execs.size()) * 100.0;

        MetricCard card1 = new MetricCard("Associated Pipelines", String.valueOf(pipes.size()), "Defined CI/CD workflows",
                SvgIcon.IconName.PIPELINES, Color.web("#0284C7"));
        MetricCard card2 = new MetricCard("Total Executions", String.valueOf(execs.size()), "Historical pipeline runs",
                SvgIcon.IconName.EXECUTIONS, Color.web("#0D9488"));
        MetricCard card3 = new MetricCard("Success Rate", String.format("%.1f%%", successRate), "Delivery reliability",
                SvgIcon.IconName.CHECK_CIRCLE, Color.web("#10B981"));

        row.getChildren().addAll(card1, card2, card3);
        this.getChildren().add(row);
    }

    private void buildSections() {
        // Associated Pipelines Table
        VBox pipeBox = new VBox(10);
        pipeBox.getStyleClass().add("card");

        HBox pipeHeader = new HBox();
        pipeHeader.setAlignment(Pos.CENTER_LEFT);
        Label pipeTitle = new Label("Associated Delivery Pipelines");
        pipeTitle.getStyleClass().add("card-header");
        pipeHeader.getChildren().add(pipeTitle);

        pipelinesTable = new TableView<>();
        pipelinesTable.setPrefHeight(200);

        TableColumn<Pipeline, String> colName = new TableColumn<>("Pipeline Name");
        colName.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getName()));
        colName.setPrefWidth(220);

        TableColumn<Pipeline, String> colMode = new TableColumn<>("Mode");
        colMode.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getExecutionMode()));
        colMode.setPrefWidth(120);

        TableColumn<Pipeline, Number> colStages = new TableColumn<>("Stages");
        colStages.setCellValueFactory(d -> new SimpleIntegerProperty(d.getValue().getStages() != null ? d.getValue().getStages().size() : 0));
        colStages.setPrefWidth(80);

        TableColumn<Pipeline, Void> colStatus = new TableColumn<>("Status");
        colStatus.setPrefWidth(120);
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
        colActions.setPrefWidth(180);
        colActions.setCellFactory(col -> new TableCell<>() {
            private final Button runBtn = new Button("Run");
            private final Button configBtn = new Button("Edit");
            private final HBox box = new HBox(6, runBtn, configBtn);
            {
                runBtn.getStyleClass().add("btn-primary");
                runBtn.setStyle("-fx-padding: 3px 8px; -fx-font-size: 11px;");
                configBtn.getStyleClass().add("btn-secondary");
                configBtn.setStyle("-fx-padding: 3px 8px; -fx-font-size: 11px;");
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
                    configBtn.setOnAction(e -> mainLayout.navigateToPipelineBuilder(p));
                    setGraphic(box);
                }
            }
        });

        pipelinesTable.getColumns().addAll(colName, colMode, colStages, colStatus, colActions);
        pipeBox.getChildren().addAll(pipeHeader, pipelinesTable);

        // Project Executions History Table
        VBox execBox = new VBox(10);
        execBox.getStyleClass().add("card");

        HBox execHeader = new HBox();
        execHeader.setAlignment(Pos.CENTER_LEFT);
        Label execTitle = new Label("Recent Executions for this Project");
        execTitle.getStyleClass().add("card-header");
        execHeader.getChildren().add(execTitle);

        executionsTable = new TableView<>();
        executionsTable.setPrefHeight(220);

        TableColumn<Execution, String> colBuild = new TableColumn<>("Build");
        colBuild.setCellValueFactory(d -> new SimpleStringProperty("#" + d.getValue().getExecutionNumber()));
        colBuild.setPrefWidth(80);

        TableColumn<Execution, String> colPipeName = new TableColumn<>("Pipeline");
        colPipeName.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getPipelineName()));
        colPipeName.setPrefWidth(200);

        TableColumn<Execution, String> colDuration = new TableColumn<>("Duration");
        colDuration.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getDurationSeconds() + "s"));
        colDuration.setPrefWidth(90);

        TableColumn<Execution, String> colDate = new TableColumn<>("Timestamp");
        colDate.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getStartedAt() != null ?
                DATE_FMT.format(d.getValue().getStartedAt()) : "-"));
        colDate.setPrefWidth(160);

        TableColumn<Execution, Void> colExecStatus = new TableColumn<>("Status");
        colExecStatus.setPrefWidth(120);
        colExecStatus.setCellFactory(col -> new TableCell<>() {
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

        TableColumn<Execution, Void> colInspect = new TableColumn<>("Action");
        colInspect.setPrefWidth(100);
        colInspect.setCellFactory(col -> new TableCell<>() {
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

        executionsTable.getColumns().addAll(colBuild, colPipeName, colDuration, colDate, colExecStatus, colInspect);
        execBox.getChildren().addAll(execHeader, executionsTable);

        this.getChildren().addAll(pipeBox, execBox);
    }

    private void loadData() {
        List<Pipeline> pipes = pipelineService.getPipelinesByProjectId(project.getId());
        pipelinesTable.getItems().setAll(pipes);

        List<Execution> execs = executionRepository.findByProjectId(project.getId());
        executionsTable.getItems().setAll(execs);
    }
}
