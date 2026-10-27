package com.devopshub.ui.view;

import com.devopshub.model.Execution;
import com.devopshub.model.ExecutionStatus;
import com.devopshub.model.Pipeline;
import com.devopshub.model.Project;
import com.devopshub.repository.ExecutionRepository;
import com.devopshub.service.PipelineService;
import com.devopshub.service.ProjectService;
import com.devopshub.ui.component.ConfirmationDialog;
import com.devopshub.ui.component.EmptyStateView;
import com.devopshub.ui.component.StatusBadge;
import com.devopshub.ui.component.SvgIcon;
import com.devopshub.ui.component.ToastNotification;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

/**
 * Execution Board & Historical Audit View matching Reference Screen 3:
 * "Canban board" layout with status columns (Queued / Scheduled / In Progress / Done),
 * task cards with avatars and metadata, plus Table View switcher.
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
    private Button kanbanViewBtn;
    private Button tableViewBtn;

    private StackPane contentArea;
    private HBox kanbanColumnsBox;
    private TableView<Execution> tableView;
    private boolean isKanbanView = true;

    public ExecutionHistoryView(MainLayoutView mainLayout, com.devopshub.service.DashboardService dashboardService) {
        this.mainLayout = mainLayout;
        this.executionRepository = new ExecutionRepository();
        this.projectService = new ProjectService();
        this.pipelineService = new PipelineService();

        this.setSpacing(16);
        this.setPadding(new Insets(24));
        this.setStyle("-fx-background-color: transparent;");

        buildHeaderToolbar();
        buildContentArea();
        refresh();
    }

    private void buildHeaderToolbar() {
        HBox toolbar = new HBox(12);
        toolbar.setAlignment(Pos.CENTER_LEFT);

        VBox titleBox = new VBox(2);
        Label subtitle = new Label("Edit your plate:");
        subtitle.setStyle("-fx-font-size: 11px; -fx-text-fill: #8A8D9E;");
        Label screenTitle = new Label("Canban board");
        screenTitle.setStyle("-fx-font-size: 18px; -fx-font-weight: 800; -fx-text-fill: #FFFFFF;");
        titleBox.getChildren().addAll(subtitle, screenTitle);

        searchField = new TextField();
        searchField.setPromptText("Search...");
        searchField.getStyleClass().add("header-search-field");
        searchField.setPrefWidth(240);
        searchField.textProperty().addListener((obs, oldVal, newVal) -> applyFilter());

        String today = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy"));
        Label datePill = new Label(today);
        datePill.getStyleClass().add("header-date-badge");

        // Team avatar cluster
        HBox avatarCluster = createAvatarCluster();

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        statusFilter = new ComboBox<>();
        statusFilter.getItems().addAll("ALL", "SUCCESS", "FAILED", "RUNNING", "QUEUED", "CANCELLED");
        statusFilter.setValue("ALL");
        statusFilter.setOnAction(e -> applyFilter());

        projectFilter = new ComboBox<>();
        projectFilter.setPrefWidth(160);
        projectFilter.getItems().add("ALL PROJECTS");
        projectFilter.setValue("ALL PROJECTS");
        for (Project p : projectService.getAllProjects()) {
            projectFilter.getItems().add(p.getName());
        }
        projectFilter.setOnAction(e -> applyFilter());

        // Reference actions: "+ Create", "Import", "Remove"
        Button createBtn = new Button("+ Create");
        createBtn.getStyleClass().add("btn-primary");
        createBtn.setOnAction(e -> showQuickRunDialog());

        Button removeBtn = new Button("Remove");
        removeBtn.getStyleClass().add("btn-secondary");
        removeBtn.setGraphic(new SvgIcon(SvgIcon.IconName.TRASH, 13, Color.web("#F43F5E")));
        removeBtn.setOnAction(e -> handleCleanHistory());

        Button refreshBtn = new Button("Refresh");
        refreshBtn.getStyleClass().add("btn-secondary");
        refreshBtn.setGraphic(new SvgIcon(SvgIcon.IconName.REFRESH, 13, Color.web("#8A8D9E")));
        refreshBtn.setTooltip(new Tooltip("Refresh execution history and board cards"));
        refreshBtn.setOnAction(e -> refresh());

        // View Switcher (Kanban vs Table)
        HBox viewSwitcher = new HBox(2);
        viewSwitcher.setStyle("-fx-background-color: #1A1B29; -fx-padding: 3px; -fx-background-radius: 8px; -fx-border-color: #26283C;");

        kanbanViewBtn = new Button();
        kanbanViewBtn.setGraphic(new SvgIcon(SvgIcon.IconName.GRID, 14, Color.web("#38BDF8")));
        kanbanViewBtn.setStyle("-fx-background-color: #24263A; -fx-padding: 5px 8px; -fx-background-radius: 6px;");
        kanbanViewBtn.setTooltip(new Tooltip("Execution Status Kanban Board (Lifecycle Status View)"));
        kanbanViewBtn.setOnAction(e -> setViewMode(true));

        tableViewBtn = new Button();
        tableViewBtn.setGraphic(new SvgIcon(SvgIcon.IconName.LIST, 14, Color.web("#8A8D9E")));
        tableViewBtn.setStyle("-fx-background-color: transparent; -fx-padding: 5px 8px; -fx-background-radius: 6px;");
        tableViewBtn.setTooltip(new Tooltip("Execution History Data Table"));
        tableViewBtn.setOnAction(e -> setViewMode(false));

        viewSwitcher.getChildren().addAll(kanbanViewBtn, tableViewBtn);

        toolbar.getChildren().addAll(titleBox, searchField, datePill, avatarCluster, spacer,
                statusFilter, projectFilter, createBtn, removeBtn, refreshBtn, viewSwitcher);
        this.getChildren().add(toolbar);
    }

    private HBox createAvatarCluster() {
        HBox cluster = new HBox(-6);
        cluster.setAlignment(Pos.CENTER_LEFT);

        String[] colors = {"#38BDF8", "#8B5CF6", "#10B981"};
        String[] inits = {"NJ", "MP", "OH"};
        for (int i = 0; i < inits.length; i++) {
            StackPane av = new StackPane();
            av.setStyle(String.format("-fx-background-color: %s; -fx-min-width: 22px; -fx-min-height: 22px; -fx-max-width: 22px; -fx-max-height: 22px; -fx-background-radius: 11px; -fx-border-color: #13141F; -fx-border-width: 1.5px; -fx-border-radius: 11px;", colors[i]));
            Label l = new Label(inits[i]);
            l.setStyle("-fx-font-size: 8px; -fx-font-weight: bold; -fx-text-fill: #FFFFFF;");
            av.getChildren().add(l);
            cluster.getChildren().add(av);
        }
        return cluster;
    }

    private void buildContentArea() {
        contentArea = new StackPane();
        VBox.setVgrow(contentArea, Priority.ALWAYS);

        // 1. Kanban Board (Reference Screen 3)
        kanbanColumnsBox = new HBox(16);
        kanbanColumnsBox.setAlignment(Pos.TOP_LEFT);
        kanbanColumnsBox.setStyle("-fx-background-color: transparent;");

        ScrollPane kanbanScroll = new ScrollPane(kanbanColumnsBox);
        kanbanScroll.setFitToHeight(true);
        kanbanScroll.setStyle("-fx-background-color: transparent; -fx-background: transparent; -fx-padding: 0;");

        // 2. Table View (Data Audit)
        buildTable();

        contentArea.getChildren().setAll(kanbanScroll);
        this.getChildren().add(contentArea);
    }

    private void buildTable() {
        tableView = new TableView<>();
        tableView.getStyleClass().add("card");
        tableView.setPrefHeight(600);

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
    }

    private void setViewMode(boolean kanban) {
        this.isKanbanView = kanban;
        if (kanban) {
            kanbanViewBtn.setStyle("-fx-background-color: #24263A; -fx-padding: 5px 8px; -fx-background-radius: 6px;");
            tableViewBtn.setStyle("-fx-background-color: transparent; -fx-padding: 5px 8px; -fx-background-radius: 6px;");
            ScrollPane kanbanScroll = new ScrollPane(kanbanColumnsBox);
            kanbanScroll.setFitToHeight(true);
            kanbanScroll.setStyle("-fx-background-color: transparent; -fx-background: transparent; -fx-padding: 0;");
            contentArea.getChildren().setAll(kanbanScroll);
        } else {
            kanbanViewBtn.setStyle("-fx-background-color: transparent; -fx-padding: 5px 8px; -fx-background-radius: 6px;");
            tableViewBtn.setStyle("-fx-background-color: #24263A; -fx-padding: 5px 8px; -fx-background-radius: 6px;");
            contentArea.getChildren().setAll(tableView);
        }
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

        // Update Table
        tableView.getItems().setAll(filtered);

        // Update Kanban Columns matching Reference Screen 3
        populateKanbanColumns(filtered);
    }

    private void populateKanbanColumns(List<Execution> executions) {
        kanbanColumnsBox.getChildren().clear();

        List<Execution> queuedList = new ArrayList<>();
        List<Execution> todoList = new ArrayList<>();
        List<Execution> runningList = new ArrayList<>();
        List<Execution> doneList = new ArrayList<>();

        for (Execution ex : executions) {
            ExecutionStatus st = ex.getStatus();
            if (st == ExecutionStatus.RUNNING) {
                runningList.add(ex);
            } else if (st == ExecutionStatus.QUEUED) {
                queuedList.add(ex);
            } else if (st == ExecutionStatus.SUCCESS || st == ExecutionStatus.FAILED || st == ExecutionStatus.CANCELLED) {
                doneList.add(ex);
            } else {
                todoList.add(ex);
            }
        }

        // 4 Columns matching Reference Screen 3: User story: | To do: | In progress: | Done:
        kanbanColumnsBox.getChildren().addAll(
                createKanbanColumn("User story:", queuedList, "#8B5CF6"),
                createKanbanColumn("To do:", todoList, "#38BDF8"),
                createKanbanColumn("In progress:", runningList, "#F59E0B"),
                createKanbanColumn("Done:", doneList, "#10B981")
        );
    }

    private VBox createKanbanColumn(String title, List<Execution> items, String accentColor) {
        VBox col = new VBox(10);
        col.getStyleClass().add("kanban-column");
        col.setPrefWidth(270);
        col.setMinWidth(250);

        HBox header = new HBox(8);
        header.getStyleClass().add("kanban-column-header");
        header.setAlignment(Pos.CENTER_LEFT);

        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("kanban-column-title");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label countPill = new Label(String.valueOf(items.size()));
        countPill.getStyleClass().add("kanban-column-count");

        header.getChildren().addAll(titleLabel, spacer, countPill);

        VBox cardsBox = new VBox(8);
        cardsBox.setStyle("-fx-background-color: transparent;");

        ScrollPane scroll = new ScrollPane(cardsBox);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background-color: transparent; -fx-background: transparent; -fx-padding: 0;");
        VBox.setVgrow(scroll, Priority.ALWAYS);

        if (items.isEmpty()) {
            Label emptyLbl = new Label("No " + title.replace(":", "").toLowerCase() + " runs");
            emptyLbl.setStyle("-fx-font-size: 11px; -fx-text-fill: #555770; -fx-padding: 20px 8px; -fx-alignment: center;");
            cardsBox.getChildren().add(emptyLbl);
        } else {
            for (Execution ex : items) {
                cardsBox.getChildren().add(createExecutionKanbanCard(ex));
            }
        }

        // Bottom column actions matching reference: "+ Create" & "Remove"
        HBox bottomActions = new HBox(8);
        bottomActions.setAlignment(Pos.CENTER_LEFT);
        bottomActions.setStyle("-fx-padding: 6px 0 0 0;");

        Button createBtn = new Button("+ Create");
        createBtn.setStyle("-fx-background-color: #1E2030; -fx-text-fill: #8A8D9E; -fx-font-size: 11px; -fx-padding: 3px 8px; -fx-background-radius: 4px; -fx-cursor: hand;");
        createBtn.setOnAction(e -> showQuickRunDialog());

        Button removeBtn = new Button("Remove");
        removeBtn.setStyle("-fx-background-color: #1E2030; -fx-text-fill: #F43F5E; -fx-font-size: 11px; -fx-padding: 3px 8px; -fx-background-radius: 4px; -fx-cursor: hand;");
        removeBtn.setOnAction(e -> handleCleanHistory());

        bottomActions.getChildren().addAll(createBtn, removeBtn);

        col.getChildren().addAll(header, scroll, bottomActions);
        return col;
    }

    private VBox createExecutionKanbanCard(Execution ex) {
        VBox card = new VBox(8);
        card.getStyleClass().add("kanban-card");

        HBox top = new HBox(8);
        top.setAlignment(Pos.CENTER_LEFT);

        Label cardTitle = new Label("Build #" + ex.getExecutionNumber() + " - " + (ex.getPipelineName() != null ? ex.getPipelineName() : "Pipeline"));
        cardTitle.getStyleClass().add("kanban-card-title");
        cardTitle.setMaxWidth(160);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox avatars = createAvatarCluster();
        top.getChildren().addAll(cardTitle, spacer, avatars);

        Label desc = new Label(ex.getProjectName() != null ? "Project: " + ex.getProjectName() : "Automated CI/CD build run.");
        desc.getStyleClass().add("kanban-card-desc");
        desc.setWrapText(true);

        HBox footer = new HBox(6);
        footer.setAlignment(Pos.CENTER_LEFT);

        StatusBadge badge = StatusBadge.of(ex.getStatus());
        Label duration = new Label(ex.getDurationSeconds() > 0 ? ex.getDurationSeconds() + "s" : "Active");
        duration.setStyle("-fx-font-size: 10px; -fx-text-fill: #8A8D9E;");

        footer.getChildren().addAll(badge, duration);

        card.getChildren().addAll(top, desc, footer);
        card.setOnMouseClicked(e -> mainLayout.navigateToExecutionDetail(ex.getId()));

        return card;
    }


    private void showQuickRunDialog() {
        List<Pipeline> pipes = pipelineService.getAllPipelines();
        if (pipes.isEmpty()) {
            mainLayout.showToast("No pipelines configured. Create a pipeline first.", ToastNotification.ToastType.WARNING);
            return;
        }

        ChoiceDialog<Pipeline> dialog = new ChoiceDialog<>(pipes.get(0), pipes);
        dialog.setTitle("Trigger Pipeline Execution");
        dialog.setHeaderText("Select Pipeline to Run");
        dialog.setContentText("Pipeline:");

        dialog.showAndWait().ifPresent(p -> {
            try {
                com.devopshub.execution.ExecutionEngine engine = com.devopshub.execution.ExecutionEngine.getInstance();
                Execution ex = engine.startExecution(p, com.devopshub.model.TriggerType.MANUAL,
                        updated -> javafx.application.Platform.runLater(this::refresh), null);
                engine.addExecutionListener(ex.getId(), updated -> javafx.application.Platform.runLater(this::refresh));
                mainLayout.showToast("Execution #" + ex.getExecutionNumber() + " queued!", ToastNotification.ToastType.SUCCESS);
                refresh();
            } catch (Exception ex) {
                mainLayout.showToast("Failed to start run: " + ex.getMessage(), ToastNotification.ToastType.ERROR);
            }
        });
    }

    private void handleCleanHistory() {
        boolean confirmed = ConfirmationDialog.show(mainLayout.getPrimaryStage(),
                "Clean Execution History",
                "Are you sure you want to purge past execution runs?\nThis permanently cleans finished execution records, stage statuses, and logs from the database.",
                "Clean History");
        if (confirmed) {
            try {
                int purgedCount = executionRepository.cleanFinishedExecutions();
                mainLayout.showToast("Execution history cleaned (" + purgedCount + " runs purged).", ToastNotification.ToastType.SUCCESS);
                refresh();
            } catch (Exception e) {
                mainLayout.showToast("Failed to clean history: " + e.getMessage(), ToastNotification.ToastType.ERROR);
            }
        }
    }

    public void refresh() {
        applyFilter();
    }

    public void setSearchQuery(String query) {
        if (searchField != null) {
            searchField.setText(query != null ? query : "");
        }
    }
}
