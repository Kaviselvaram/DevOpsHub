package com.devopshub.ui.view;

import com.devopshub.model.Execution;
import com.devopshub.service.DashboardService;
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
 * Historical Execution Audit Log View.
 */
public class ExecutionHistoryView extends VBox {

    private static final SimpleDateFormat DATE_FMT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    private final MainLayoutView mainLayout;
    private final DashboardService dashboardService;

    private TableView<Execution> tableView;
    private ComboBox<String> statusFilter;

    public ExecutionHistoryView(MainLayoutView mainLayout, DashboardService dashboardService) {
        this.mainLayout = mainLayout;
        this.dashboardService = dashboardService;

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

        Label filterLabel = new Label("Filter by Status:");
        filterLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #475569;");

        statusFilter = new ComboBox<>();
        statusFilter.getItems().addAll("ALL", "SUCCESS", "FAILED", "RUNNING", "CANCELLED");
        statusFilter.setValue("ALL");
        statusFilter.setOnAction(e -> applyFilter());

        Button refreshBtn = new Button("Refresh");
        refreshBtn.getStyleClass().add("btn-secondary");
        refreshBtn.setGraphic(new SvgIcon(SvgIcon.IconName.REFRESH, 14, Color.web("#64748B")));
        refreshBtn.setOnAction(e -> refresh());

        toolbar.getChildren().addAll(filterLabel, statusFilter, refreshBtn);
        this.getChildren().add(toolbar);
    }

    private void buildTable() {
        tableView = new TableView<>();
        tableView.getStyleClass().add("card");
        VBox.setVgrow(tableView, Priority.ALWAYS);
        tableView.setPrefHeight(600);

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
                inspectBtn.setStyle("-fx-padding: 4px 10px; -fx-font-size: 11px;");
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
        this.getChildren().add(tableView);
    }

    private void applyFilter() {
        String filter = statusFilter.getValue();
        List<Execution> all = dashboardService.getRecentExecutions(50);
        if ("ALL".equals(filter)) {
            tableView.getItems().setAll(all);
        } else {
            tableView.getItems().setAll(all.stream()
                    .filter(e -> e.getStatus().name().equalsIgnoreCase(filter))
                    .toList());
        }
    }

    public void refresh() {
        applyFilter();
    }
}
