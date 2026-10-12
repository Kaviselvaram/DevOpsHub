package com.devopshub.ui.view;

import com.devopshub.model.Project;
import com.devopshub.service.ProjectService;
import com.devopshub.ui.component.ConfirmationDialog;
import com.devopshub.ui.component.EmptyStateView;
import com.devopshub.ui.component.StatusBadge;
import com.devopshub.ui.component.SvgIcon;
import com.devopshub.ui.component.ToastNotification;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.util.List;

/**
 * Projects Management View: CRUD operations, repository linkage, search, and filtering.
 */
public class ProjectsView extends VBox {

    private final MainLayoutView mainLayout;
    private final ProjectService projectService;

    private TextField searchField;
    private ComboBox<String> typeFilter;
    private ComboBox<String> statusFilter;
    private TableView<Project> tableView;
    private StackPane tableContainer;

    public ProjectsView(MainLayoutView mainLayout, ProjectService projectService) {
        this.mainLayout = mainLayout;
        this.projectService = projectService;

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
        searchField.setPromptText("Search projects by name, description, or repository...");
        searchField.setPrefWidth(320);
        searchField.textProperty().addListener((obs, oldVal, newVal) -> applyFilters());

        Label typeLabel = new Label("Type:");
        typeLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #475569;");

        typeFilter = new ComboBox<>();
        typeFilter.getItems().addAll("ALL", "Java", "Java/Spring", "Python", "NodeJS", "Go");
        typeFilter.setValue("ALL");
        typeFilter.setOnAction(e -> applyFilters());

        Label statusLabel = new Label("Status:");
        statusLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #475569;");

        statusFilter = new ComboBox<>();
        statusFilter.getItems().addAll("ALL", "ACTIVE", "INACTIVE", "ARCHIVED");
        statusFilter.setValue("ALL");
        statusFilter.setOnAction(e -> applyFilters());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button newProjBtn = new Button("New Project");
        newProjBtn.getStyleClass().add("btn-primary");
        newProjBtn.setGraphic(new SvgIcon(SvgIcon.IconName.PLUS, 13, Color.WHITE));
        newProjBtn.setOnAction(e -> showCreateProjectModal());

        Button refreshBtn = new Button("Refresh");
        refreshBtn.getStyleClass().add("btn-secondary");
        refreshBtn.setGraphic(new SvgIcon(SvgIcon.IconName.REFRESH, 13, Color.web("#64748B")));
        refreshBtn.setOnAction(e -> refresh());

        toolbar.getChildren().addAll(searchField, typeLabel, typeFilter, statusLabel, statusFilter, spacer, newProjBtn, refreshBtn);
        this.getChildren().add(toolbar);
    }

    private void buildTable() {
        tableContainer = new StackPane();
        VBox.setVgrow(tableContainer, Priority.ALWAYS);

        tableView = new TableView<>();
        tableView.getStyleClass().add("card");
        tableView.setPrefHeight(600);

        // Double-click row opens Project Details
        tableView.setRowFactory(tv -> {
            TableRow<Project> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && (!row.isEmpty())) {
                    Project p = row.getItem();
                    mainLayout.navigateToProjectDetail(p);
                }
            });
            return row;
        });

        TableColumn<Project, String> colName = new TableColumn<>("Project Name");
        colName.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getName()));
        colName.setPrefWidth(180);

        TableColumn<Project, String> colDesc = new TableColumn<>("Description");
        colDesc.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getDescription()));
        colDesc.setPrefWidth(220);

        TableColumn<Project, String> colRepo = new TableColumn<>("Repository");
        colRepo.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getRepositoryUrl()));
        colRepo.setPrefWidth(240);

        TableColumn<Project, String> colBranch = new TableColumn<>("Branch");
        colBranch.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getDefaultBranch()));
        colBranch.setPrefWidth(90);

        TableColumn<Project, String> colType = new TableColumn<>("Type");
        colType.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getProjectType()));
        colType.setPrefWidth(90);

        TableColumn<Project, Number> colPipelines = new TableColumn<>("Pipelines");
        colPipelines.setCellValueFactory(d -> new SimpleIntegerProperty(d.getValue().getPipelineCount()));
        colPipelines.setPrefWidth(80);

        TableColumn<Project, Void> colStatus = new TableColumn<>("Status");
        colStatus.setPrefWidth(100);
        colStatus.setCellFactory(col -> new TableCell<>() {
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

        TableColumn<Project, Void> colActions = new TableColumn<>("Actions");
        colActions.setPrefWidth(180);
        colActions.setCellFactory(col -> new TableCell<>() {
            private final Button viewBtn = new Button("View");
            private final Button editBtn = new Button("Edit");
            private final Button deleteBtn = new Button();
            private final HBox box = new HBox(6, viewBtn, editBtn, deleteBtn);
            {
                viewBtn.getStyleClass().add("btn-secondary");
                viewBtn.setStyle("-fx-padding: 3px 8px; -fx-font-size: 11px;");

                editBtn.getStyleClass().add("btn-secondary");
                editBtn.setStyle("-fx-padding: 3px 8px; -fx-font-size: 11px;");

                deleteBtn.getStyleClass().add("btn-secondary");
                deleteBtn.setGraphic(new SvgIcon(SvgIcon.IconName.TRASH, 12, Color.web("#E11D48")));
                deleteBtn.setStyle("-fx-padding: 3px 6px;");
                deleteBtn.setTooltip(new Tooltip("Delete Project"));
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setGraphic(null);
                } else {
                    Project p = getTableRow().getItem();
                    viewBtn.setOnAction(e -> mainLayout.navigateToProjectDetail(p));
                    editBtn.setOnAction(e -> showEditProjectModal(p));
                    deleteBtn.setOnAction(e -> {
                        boolean confirmed = ConfirmationDialog.show(mainLayout.getPrimaryStage(),
                                "Delete Project",
                                "Are you sure you want to delete project '" + p.getName() + "' and its associated delivery pipelines?\nThis operation is irreversible.",
                                "Delete Project");
                        if (confirmed) {
                            try {
                                projectService.deleteProject(p.getId());
                                mainLayout.showToast("Project deleted successfully", ToastNotification.ToastType.SUCCESS);
                                refresh();
                            } catch (Exception ex) {
                                mainLayout.showToast("Failed to delete project: " + ex.getMessage(), ToastNotification.ToastType.ERROR);
                            }
                        }
                    });
                    setGraphic(box);
                }
            }
        });

        tableView.getColumns().addAll(colName, colDesc, colRepo, colBranch, colType, colPipelines, colStatus, colActions);
        tableContainer.getChildren().add(tableView);
        this.getChildren().add(tableContainer);
    }

    private void applyFilters() {
        String q = searchField != null && searchField.getText() != null ? searchField.getText().trim().toLowerCase() : "";
        String type = typeFilter != null ? typeFilter.getValue() : "ALL";
        String status = statusFilter != null ? statusFilter.getValue() : "ALL";

        List<Project> all = projectService.getAllProjects();
        List<Project> filtered = all.stream().filter(p -> {
            boolean matchQ = q.isEmpty() ||
                    p.getName().toLowerCase().contains(q) ||
                    (p.getDescription() != null && p.getDescription().toLowerCase().contains(q)) ||
                    (p.getRepositoryUrl() != null && p.getRepositoryUrl().toLowerCase().contains(q));
            boolean matchType = "ALL".equals(type) || (p.getProjectType() != null && p.getProjectType().equalsIgnoreCase(type));
            boolean matchStatus = "ALL".equals(status) || (p.getStatus() != null && p.getStatus().equalsIgnoreCase(status));
            return matchQ && matchType && matchStatus;
        }).toList();

        tableView.getItems().setAll(filtered);

        if (filtered.isEmpty()) {
            if (tableContainer.getChildren().size() == 1) {
                EmptyStateView emptyState = new EmptyStateView(
                        SvgIcon.IconName.PROJECTS,
                        "No Projects Found",
                        "No projects match the current search filters. Create a new project to get started.",
                        "Create New Project",
                        this::showCreateProjectModal
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

    public void showCreateProjectModal() {
        showProjectModal(null);
    }

    public void showEditProjectModal(Project existing) {
        showProjectModal(existing);
    }

    private void showProjectModal(Project existing) {
        Stage modal = new Stage();
        modal.initModality(Modality.APPLICATION_MODAL);
        modal.initOwner(mainLayout.getPrimaryStage());
        modal.setTitle(existing == null ? "Create New Project" : "Edit Project Details");
        modal.setResizable(false);

        VBox form = new VBox(14);
        form.setPadding(new Insets(24));
        form.setStyle("-fx-background-color: #FFFFFF;");

        Label titleLabel = new Label(existing == null ? "Create Project" : "Edit Project");
        titleLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: 800; -fx-text-fill: #0F172A;");

        Label errorBanner = new Label();
        errorBanner.getStyleClass().add("form-error-label");
        errorBanner.setVisible(false);

        TextField nameField = new TextField(existing != null ? existing.getName() : "");
        nameField.setPromptText("Project Name (e.g. Payments Gateway)");

        TextArea descField = new TextArea(existing != null ? existing.getDescription() : "");
        descField.setPromptText("Project Description");
        descField.setPrefRowCount(3);

        TextField repoField = new TextField(existing != null ? existing.getRepositoryUrl() : "");
        repoField.setPromptText("Repository URL (e.g. https://github.com/org/payments.git)");

        TextField branchField = new TextField(existing != null ? existing.getDefaultBranch() : "main");
        branchField.setPromptText("Default Branch (e.g. main)");

        ComboBox<String> typeCombo = new ComboBox<>();
        typeCombo.getItems().addAll("Java", "Java/Spring", "Python", "NodeJS", "Go");
        typeCombo.setValue(existing != null ? existing.getProjectType() : "Java");

        HBox btnBox = new HBox(10);
        btnBox.setAlignment(Pos.CENTER_RIGHT);

        Button cancelBtn = new Button("Cancel");
        cancelBtn.getStyleClass().add("btn-secondary");
        cancelBtn.setOnAction(e -> modal.close());

        Button saveBtn = new Button(existing == null ? "Create Project" : "Save Changes");
        saveBtn.getStyleClass().add("btn-primary");
        saveBtn.setOnAction(e -> {
            String name = nameField.getText().trim();
            String repo = repoField.getText().trim();
            if (name.isEmpty()) {
                errorBanner.setText("Project name is required.");
                errorBanner.setVisible(true);
                nameField.setStyle("-fx-border-color: #E11D48;");
                return;
            }
            if (repo.isEmpty()) {
                errorBanner.setText("Repository URL is required.");
                errorBanner.setVisible(true);
                repoField.setStyle("-fx-border-color: #E11D48;");
                return;
            }

            try {
                if (existing == null) {
                    projectService.createProject(name, descField.getText().trim(),
                            repo, branchField.getText().trim(), typeCombo.getValue());
                    mainLayout.showToast("Project created successfully!", ToastNotification.ToastType.SUCCESS);
                } else {
                    projectService.updateProject(existing.getId(), name, descField.getText().trim(),
                            repo, branchField.getText().trim(), typeCombo.getValue(), existing.getStatus());
                    mainLayout.showToast("Project updated successfully!", ToastNotification.ToastType.SUCCESS);
                }
                modal.close();
                refresh();
            } catch (Exception ex) {
                errorBanner.setText("Error: " + ex.getMessage());
                errorBanner.setVisible(true);
            }
        });

        btnBox.getChildren().addAll(cancelBtn, saveBtn);

        form.getChildren().addAll(
                titleLabel,
                errorBanner,
                new Label("Project Name:"), nameField,
                new Label("Description:"), descField,
                new Label("Repository URL:"), repoField,
                new Label("Default Branch:"), branchField,
                new Label("Project Type:"), typeCombo,
                btnBox
        );

        Scene scene = new Scene(form, 480, 520);
        scene.getStylesheets().add(getClass().getResource("/css/devopshub.css").toExternalForm());
        modal.setScene(scene);
        modal.showAndWait();
    }
}
