package com.devopshub.ui.view;

import com.devopshub.execution.ExecutionEngine;
import com.devopshub.model.Execution;
import com.devopshub.model.Pipeline;
import com.devopshub.model.Stage;
import com.devopshub.model.TriggerType;
import com.devopshub.repository.ExecutionRepository;
import com.devopshub.service.PipelineService;
import com.devopshub.ui.component.ConfirmationDialog;
import com.devopshub.ui.component.StatusBadge;
import com.devopshub.ui.component.SvgIcon;
import com.devopshub.ui.component.ToastNotification;
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
 * Dedicated Pipeline Detail View:
 * Stage composition, DAG dependencies, execution statistics, and run triggers.
 */
public class PipelineDetailView extends VBox {

    private static final SimpleDateFormat DATE_FMT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    private final MainLayoutView mainLayout;
    private final Pipeline pipeline;
    private final PipelineService pipelineService;
    private final ExecutionEngine executionEngine;
    private final ExecutionRepository executionRepository;

    private TableView<Stage> stagesTable;
    private TableView<Execution> executionsTable;

    public PipelineDetailView(MainLayoutView mainLayout, Pipeline pipeline,
                              PipelineService pipelineService, ExecutionEngine executionEngine) {
        this.mainLayout = mainLayout;
        this.pipeline = pipeline;
        this.pipelineService = pipelineService;
        this.executionEngine = executionEngine;
        this.executionRepository = new ExecutionRepository();

        this.setSpacing(20);
        this.setPadding(new Insets(24));
        this.setStyle("-fx-background-color: transparent;");

        buildBreadcrumb();
        buildHeaderCard();
        buildStagesSection();
        buildExecutionsSection();
        loadData();
    }

    private void buildBreadcrumb() {
        HBox breadcrumb = new HBox(6);
        breadcrumb.getStyleClass().add("breadcrumb-bar");

        Label parentLink = new Label("Pipelines");
        parentLink.getStyleClass().add("breadcrumb-item");
        parentLink.setOnMouseClicked(e -> mainLayout.navigateToPipelines());

        Label separator = new Label("/");
        separator.getStyleClass().add("breadcrumb-separator");

        Label current = new Label(pipeline.getName());
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

        Label nameLabel = new Label(pipeline.getName());
        nameLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: 800; -fx-text-fill: #0F172A;");

        StatusBadge statusBadge = new StatusBadge(pipeline.getLastExecutionStatus());
        titleRow.getChildren().addAll(nameLabel, statusBadge);

        Label descLabel = new Label(pipeline.getDescription() != null && !pipeline.getDescription().isEmpty()
                ? pipeline.getDescription() : "Automated delivery workflow.");
        descLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: #475569;");

        HBox metaRow = new HBox(16);
        metaRow.setAlignment(Pos.CENTER_LEFT);
        metaRow.setStyle("-fx-padding: 4px 0 0 0;");

        Label projLabel = new Label("Project: " + pipeline.getProjectName());
        projLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #0284C7; -fx-font-weight: bold;");

        Label modeLabel = new Label("Execution Mode: " + pipeline.getExecutionMode());
        modeLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #64748B;");

        metaRow.getChildren().addAll(projLabel, modeLabel);
        info.getChildren().addAll(titleRow, descLabel, metaRow);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox actions = new HBox(10);
        actions.setAlignment(Pos.CENTER_RIGHT);

        Button runBtn = new Button("Run Pipeline");
        runBtn.getStyleClass().add("btn-primary");
        runBtn.setGraphic(new SvgIcon(SvgIcon.IconName.PLAY, 12, Color.WHITE));
        runBtn.setOnAction(e -> {
            mainLayout.showToast("Starting pipeline execution...", ToastNotification.ToastType.INFO);
            Execution ex = executionEngine.startExecution(pipeline, TriggerType.MANUAL, null, null);
            mainLayout.navigateToExecutionDetail(ex.getId());
        });

        Button configBtn = new Button("Configure");
        configBtn.getStyleClass().add("btn-secondary");
        configBtn.setGraphic(new SvgIcon(SvgIcon.IconName.SETTINGS, 12, Color.web("#64748B")));
        configBtn.setOnAction(e -> mainLayout.navigateToPipelineBuilder(pipeline));

        Button dupBtn = new Button("Duplicate");
        dupBtn.getStyleClass().add("btn-secondary");
        dupBtn.setGraphic(new SvgIcon(SvgIcon.IconName.COPY, 12, Color.web("#64748B")));
        dupBtn.setOnAction(e -> {
            try {
                Pipeline copy = pipelineService.duplicatePipeline(pipeline.getId());
                mainLayout.showToast("Pipeline duplicated as: " + copy.getName(), ToastNotification.ToastType.SUCCESS);
                mainLayout.navigateToPipelineDetail(copy);
            } catch (Exception ex) {
                mainLayout.showToast("Failed to duplicate: " + ex.getMessage(), ToastNotification.ToastType.ERROR);
            }
        });

        Button backBtn = new Button("Back");
        backBtn.getStyleClass().add("btn-secondary");
        backBtn.setOnAction(e -> mainLayout.navigateToPipelines());

        actions.getChildren().addAll(runBtn, configBtn, dupBtn, backBtn);
        card.getChildren().addAll(info, spacer, actions);
        this.getChildren().add(card);
    }

    private void buildStagesSection() {
        VBox section = new VBox(10);
        section.getStyleClass().add("card");

        HBox secHeader = new HBox();
        secHeader.setAlignment(Pos.CENTER_LEFT);
        Label title = new Label("Configured Pipeline Stages");
        title.getStyleClass().add("card-header");
        secHeader.getChildren().add(title);

        stagesTable = new TableView<>();
        stagesTable.setPrefHeight(200);

        TableColumn<Stage, Number> colOrder = new TableColumn<>("#");
        colOrder.setCellValueFactory(d -> new SimpleIntegerProperty(d.getValue().getStageOrder()));
        colOrder.setPrefWidth(40);

        TableColumn<Stage, String> colName = new TableColumn<>("Stage Name");
        colName.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getName()));
        colName.setPrefWidth(180);

        TableColumn<Stage, String> colType = new TableColumn<>("Type");
        colType.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getStageType().name()));
        colType.setPrefWidth(120);

        TableColumn<Stage, String> colCmd = new TableColumn<>("Command / Subprocess Script");
        colCmd.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getCommand()));
        colCmd.setPrefWidth(320);

        TableColumn<Stage, Number> colTimeout = new TableColumn<>("Timeout (s)");
        colTimeout.setCellValueFactory(d -> new SimpleIntegerProperty(d.getValue().getTimeoutSeconds()));
        colTimeout.setPrefWidth(90);

        TableColumn<Stage, String> colGroup = new TableColumn<>("Parallel Group");
        colGroup.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getParallelGroup() != null ? d.getValue().getParallelGroup() : "None"));
        colGroup.setPrefWidth(120);

        stagesTable.getColumns().addAll(colOrder, colName, colType, colCmd, colTimeout, colGroup);
        section.getChildren().addAll(secHeader, stagesTable);
        this.getChildren().add(section);
    }

    private void buildExecutionsSection() {
        VBox section = new VBox(10);
        section.getStyleClass().add("card");

        HBox secHeader = new HBox();
        secHeader.setAlignment(Pos.CENTER_LEFT);
        Label title = new Label("Execution History for this Pipeline");
        title.getStyleClass().add("card-header");
        secHeader.getChildren().add(title);

        executionsTable = new TableView<>();
        executionsTable.setPrefHeight(240);

        TableColumn<Execution, String> colBuild = new TableColumn<>("Build");
        colBuild.setCellValueFactory(d -> new SimpleStringProperty("#" + d.getValue().getExecutionNumber()));
        colBuild.setPrefWidth(80);

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

        executionsTable.getColumns().addAll(colBuild, colTrigger, colDuration, colDate, colStatus, colInspect);
        section.getChildren().addAll(secHeader, executionsTable);
        this.getChildren().add(section);
    }

    private void loadData() {
        if (pipeline.getStages() != null) {
            stagesTable.getItems().setAll(pipeline.getStages());
        }
        List<Execution> runs = executionRepository.findByPipelineId(pipeline.getId());
        executionsTable.getItems().setAll(runs);
    }
}
