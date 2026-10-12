package com.devopshub.ui.view;

import com.devopshub.execution.ExecutionEngine;
import com.devopshub.model.Execution;
import com.devopshub.model.Pipeline;
import com.devopshub.model.TriggerType;
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

import java.util.List;

/**
 * Pipelines List View: Manage delivery pipelines, trigger executions, and configure topologies.
 */
public class PipelinesView extends VBox {

    private final MainLayoutView mainLayout;
    private final PipelineService pipelineService;
    private final ExecutionEngine executionEngine;

    private TableView<Pipeline> tableView;

    public PipelinesView(MainLayoutView mainLayout, PipelineService pipelineService, ExecutionEngine executionEngine) {
        this.mainLayout = mainLayout;
        this.pipelineService = pipelineService;
        this.executionEngine = executionEngine;

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

        Button refreshBtn = new Button("Refresh");
        refreshBtn.getStyleClass().add("btn-secondary");
        refreshBtn.setGraphic(new SvgIcon(SvgIcon.IconName.REFRESH, 14, Color.web("#64748B")));
        refreshBtn.setOnAction(e -> refresh());

        toolbar.getChildren().add(refreshBtn);
        this.getChildren().add(toolbar);
    }

    private void buildTable() {
        tableView = new TableView<>();
        tableView.getStyleClass().add("card");
        VBox.setVgrow(tableView, Priority.ALWAYS);
        tableView.setPrefHeight(600);

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
        colActions.setPrefWidth(210);
        colActions.setCellFactory(col -> new TableCell<>() {
            private final Button runBtn = new Button("Run");
            private final Button configBtn = new Button("Configure");
            private final Button deleteBtn = new Button();
            private final HBox box = new HBox(6, runBtn, configBtn, deleteBtn);
            {
                runBtn.getStyleClass().add("btn-primary");
                runBtn.setGraphic(new SvgIcon(SvgIcon.IconName.PLAY, 11, Color.WHITE));
                runBtn.setStyle("-fx-padding: 4px 10px; -fx-font-size: 11px;");

                configBtn.getStyleClass().add("btn-secondary");
                configBtn.setGraphic(new SvgIcon(SvgIcon.IconName.SETTINGS, 11, Color.web("#64748B")));
                configBtn.setStyle("-fx-padding: 4px 10px; -fx-font-size: 11px;");

                deleteBtn.getStyleClass().add("btn-secondary");
                deleteBtn.setGraphic(new SvgIcon(SvgIcon.IconName.TRASH, 11, Color.web("#E11D48")));
                deleteBtn.setStyle("-fx-padding: 4px 8px;");
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
        this.getChildren().add(tableView);
    }

    public void refresh() {
        List<Pipeline> list = pipelineService.getAllPipelines();
        tableView.getItems().setAll(list);
    }
}
