package com.devopshub.ui.view;

import com.devopshub.model.Project;
import com.devopshub.service.ProjectService;
import com.devopshub.ui.component.ConfirmationDialog;
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
    private TableView<Project> tableView;

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
        searchField.setPromptText("Search projects by name, description, or language...");
        searchField.setPrefWidth(350);
        searchField.textProperty().addListener((obs, oldVal, newVal) -> onSearch(newVal));

        Button searchBtn = new Button("Search");
        searchBtn.getStyleClass().add("btn-secondary");
        searchBtn.setGraphic(new SvgIcon(SvgIcon.IconName.SEARCH, 14, Color.web("#64748B")));
        searchBtn.setOnAction(e -> onSearch(searchField.getText()));

        Button refreshBtn = new Button("Refresh");
        refreshBtn.getStyleClass().add("btn-secondary");
        refreshBtn.setGraphic(new SvgIcon(SvgIcon.IconName.REFRESH, 14, Color.web("#64748B")));
        refreshBtn.setOnAction(e -> refresh());

        toolbar.getChildren().addAll(searchField, searchBtn, refreshBtn);
        this.getChildren().add(toolbar);
    }

    private void buildTable() {
        tableView = new TableView<>();
        tableView.getStyleClass().add("card");
        VBox.setVgrow(tableView, Priority.ALWAYS);
        tableView.setPrefHeight(600);

        TableColumn<Project, String> colName = new TableColumn<>("Project Name");
        colName.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getName()));
        colName.setPrefWidth(160);

        TableColumn<Project, String> colDesc = new TableColumn<>("Description");
        colDesc.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getDescription()));
        colDesc.setPrefWidth(220);

        TableColumn<Project, String> colRepo = new TableColumn<>("Repository");
        colRepo.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getRepositoryUrl()));
        colRepo.setPrefWidth(240);

        TableColumn<Project, String> colBranch = new TableColumn<>("Branch");
        colBranch.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getDefaultBranch()));
        colBranch.setPrefWidth(80);

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
        colActions.setPrefWidth(150);
        colActions.setCellFactory(col -> new TableCell<>() {
            private final Button editBtn = new Button();
            private final Button deleteBtn = new Button();
            private final HBox box = new HBox(6, editBtn, deleteBtn);
            {
                editBtn.getStyleClass().add("btn-secondary");
                editBtn.setGraphic(new SvgIcon(SvgIcon.IconName.EDIT, 12, Color.web("#0284C7")));
                editBtn.setTooltip(new Tooltip("Edit Project"));

                deleteBtn.getStyleClass().add("btn-secondary");
                deleteBtn.setGraphic(new SvgIcon(SvgIcon.IconName.TRASH, 12, Color.web("#E11D48")));
                deleteBtn.setTooltip(new Tooltip("Delete Project"));
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setGraphic(null);
                } else {
                    Project p = getTableRow().getItem();
                    editBtn.setOnAction(e -> showEditProjectModal(p));
                    deleteBtn.setOnAction(e -> {
                        boolean confirmed = ConfirmationDialog.show(mainLayout.getPrimaryStage(),
                                "Delete Project",
                                "Are you sure you want to delete project '" + p.getName() + "' and its associated pipelines?",
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
        this.getChildren().add(tableView);
    }

    private void onSearch(String q) {
        List<Project> results = projectService.searchProjects(q);
        tableView.getItems().setAll(results);
    }

    public void refresh() {
        List<Project> list = projectService.getAllProjects();
        tableView.getItems().setAll(list);
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

        Label titleLabel = new Label(existing == null ? "New Project" : "Edit Project");
        titleLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: #0F172A;");

        TextField nameField = new TextField(existing != null ? existing.getName() : "");
        nameField.setPromptText("Project Name (e.g. Payments Gateway)");

        TextArea descField = new TextArea(existing != null ? existing.getDescription() : "");
        descField.setPromptText("Project Description");
        descField.setPrefRowCount(3);

        TextField repoField = new TextField(existing != null ? existing.getRepositoryUrl() : "");
        repoField.setPromptText("Repository URL (e.g. https://github.com/vit/payments.git)");

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
            try {
                if (existing == null) {
                    projectService.createProject(nameField.getText(), descField.getText(),
                            repoField.getText(), branchField.getText(), typeCombo.getValue());
                    mainLayout.showToast("Project created successfully!", ToastNotification.ToastType.SUCCESS);
                } else {
                    projectService.updateProject(existing.getId(), nameField.getText(), descField.getText(),
                            repoField.getText(), branchField.getText(), typeCombo.getValue(), existing.getStatus());
                    mainLayout.showToast("Project updated successfully!", ToastNotification.ToastType.SUCCESS);
                }
                modal.close();
                refresh();
            } catch (Exception ex) {
                Alert alert = new Alert(Alert.AlertType.ERROR, ex.getMessage(), ButtonType.OK);
                alert.showAndWait();
            }
        });

        btnBox.getChildren().addAll(cancelBtn, saveBtn);

        form.getChildren().addAll(
                titleLabel,
                new Label("Project Name:"), nameField,
                new Label("Description:"), descField,
                new Label("Repository URL:"), repoField,
                new Label("Default Branch:"), branchField,
                new Label("Project Type:"), typeCombo,
                btnBox
        );

        Scene scene = new Scene(form, 480, 500);
        scene.getStylesheets().add(getClass().getResource("/css/devopshub.css").toExternalForm());
        modal.setScene(scene);
        modal.showAndWait();
    }
}
