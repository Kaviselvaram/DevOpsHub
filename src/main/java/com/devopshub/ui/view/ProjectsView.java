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
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.File;
import java.util.List;

/**
 * Projects Management View matching visual reference Screen 2:
 * "Choose a project to work" Grid View + Table View toggle,
 * project cards with preview thumbnails, team avatars, and full CRUD persistence.
 */
public class ProjectsView extends VBox {

    private final MainLayoutView mainLayout;
    private final ProjectService projectService;
    private final com.devopshub.service.ProjectImportService importService = new com.devopshub.service.ProjectImportService();
    private final com.devopshub.git.GitService gitService = new com.devopshub.git.GitService();
    private final com.devopshub.service.PipelineService pipelineService = new com.devopshub.service.PipelineService();

    private TextField searchField;
    private ComboBox<String> typeFilter;
    private ComboBox<String> statusFilter;
    private Button gridViewBtn;
    private Button tableViewBtn;

    private StackPane contentArea;
    private FlowPane gridPane;
    private TableView<Project> tableView;
    private boolean isGridView = true;
    private Project selectedProject;

    public ProjectsView(MainLayoutView mainLayout, ProjectService projectService) {
        this.mainLayout = mainLayout;
        this.projectService = projectService;

        this.setSpacing(18);
        this.setPadding(new Insets(24));
        this.setStyle("-fx-background-color: transparent;");

        buildToolbar();
        buildContentArea();
        refresh();
    }

    private void buildToolbar() {
        HBox toolbar = new HBox(12);
        toolbar.setAlignment(Pos.CENTER_LEFT);

        VBox titleBox = new VBox(2);
        Label subtitle = new Label("Choose project:");
        subtitle.setStyle("-fx-font-size: 11px; -fx-text-fill: #8A8D9E;");
        Label title = new Label("Choose a project to work");
        title.setStyle("-fx-font-size: 18px; -fx-font-weight: 800; -fx-text-fill: #FFFFFF;");
        titleBox.getChildren().addAll(subtitle, title);

        searchField = new TextField();
        searchField.setPromptText("Search...");
        searchField.getStyleClass().add("header-search-field");
        searchField.setPrefWidth(220);
        searchField.textProperty().addListener((obs, oldVal, newVal) -> applyFilters());

        String today = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy"));
        Label datePill = new Label(today);
        datePill.getStyleClass().add("header-date-badge");

        typeFilter = new ComboBox<>();
        typeFilter.getItems().addAll("ALL", "Java", "Java/Spring", "Python", "NodeJS", "Next.js (NodeJS)", "Go");
        typeFilter.setValue("ALL");
        typeFilter.setOnAction(e -> applyFilters());

        statusFilter = new ComboBox<>();
        statusFilter.getItems().addAll("ALL", "ACTIVE", "INACTIVE", "ARCHIVED");
        statusFilter.setValue("ALL");
        statusFilter.setOnAction(e -> applyFilters());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // Actions matching reference: "+ Create", "Import", "Remove"
        Button createBtn = new Button("+ Create");
        createBtn.getStyleClass().add("btn-primary");
        createBtn.setMinWidth(Region.USE_PREF_SIZE);
        createBtn.setOnAction(e -> showCreateProjectModal());

        Button importBtn = new Button("Import");
        importBtn.getStyleClass().add("btn-secondary");
        importBtn.setGraphic(new SvgIcon(SvgIcon.IconName.SHARE, 13, Color.web("#8A8D9E")));
        importBtn.setMinWidth(Region.USE_PREF_SIZE);
        importBtn.setOnAction(e -> handleImportProject());

        Button removeBtn = new Button("Remove");
        removeBtn.getStyleClass().add("btn-secondary");
        removeBtn.setGraphic(new SvgIcon(SvgIcon.IconName.TRASH, 13, Color.web("#F43F5E")));
        removeBtn.setMinWidth(Region.USE_PREF_SIZE);
        removeBtn.setOnAction(e -> handleRemoveSelected());

        // Grid / Table View Switcher
        HBox viewSwitcher = new HBox(2);
        viewSwitcher.setStyle("-fx-background-color: #1A1B29; -fx-padding: 3px; -fx-background-radius: 8px; -fx-border-color: #26283C;");

        gridViewBtn = new Button();
        gridViewBtn.setGraphic(new SvgIcon(SvgIcon.IconName.GRID, 14, Color.web("#38BDF8")));
        gridViewBtn.setStyle("-fx-background-color: #24263A; -fx-padding: 5px 8px; -fx-background-radius: 6px;");
        gridViewBtn.setOnAction(e -> setViewMode(true));

        tableViewBtn = new Button();
        tableViewBtn.setGraphic(new SvgIcon(SvgIcon.IconName.LIST, 14, Color.web("#8A8D9E")));
        tableViewBtn.setStyle("-fx-background-color: transparent; -fx-padding: 5px 8px; -fx-background-radius: 6px;");
        tableViewBtn.setOnAction(e -> setViewMode(false));

        viewSwitcher.getChildren().addAll(gridViewBtn, tableViewBtn);

        toolbar.getChildren().addAll(titleBox, searchField, datePill, typeFilter, statusFilter, spacer, createBtn, importBtn, removeBtn, viewSwitcher);
        this.getChildren().add(toolbar);
    }

    private void buildContentArea() {
        contentArea = new StackPane();
        VBox.setVgrow(contentArea, Priority.ALWAYS);

        // 1. Grid View (Reference Screen 2)
        gridPane = new FlowPane();
        gridPane.setHgap(20);
        gridPane.setVgap(20);
        gridPane.setPadding(new Insets(10, 0, 20, 0));
        gridPane.setStyle("-fx-background-color: transparent;");

        ScrollPane gridScroll = new ScrollPane(gridPane);
        gridScroll.setFitToWidth(true);
        gridScroll.setStyle("-fx-background-color: transparent; -fx-background: transparent; -fx-padding: 0;");

        // 2. Table View (Data Audit)
        buildTable();

        contentArea.getChildren().setAll(gridScroll);
        this.getChildren().add(contentArea);
    }

    private void buildTable() {
        tableView = new TableView<>();
        tableView.getStyleClass().add("card");
        tableView.setPrefHeight(600);

        tableView.setRowFactory(tv -> {
            TableRow<Project> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (!row.isEmpty()) {
                    selectedProject = row.getItem();
                    if (event.getClickCount() == 2) {
                        mainLayout.navigateToProjectDetail(selectedProject);
                    }
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
        colActions.setPrefWidth(160);
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
                deleteBtn.setGraphic(new SvgIcon(SvgIcon.IconName.TRASH, 12, Color.web("#F43F5E")));
                deleteBtn.setStyle("-fx-padding: 3px 6px;");
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
                    deleteBtn.setOnAction(e -> confirmDeleteProject(p));
                    setGraphic(box);
                }
            }
        });

        tableView.getColumns().addAll(colName, colDesc, colRepo, colBranch, colType, colPipelines, colStatus, colActions);
    }

    private void setViewMode(boolean grid) {
        this.isGridView = grid;
        if (grid) {
            gridViewBtn.setStyle("-fx-background-color: #24263A; -fx-padding: 5px 8px; -fx-background-radius: 6px;");
            tableViewBtn.setStyle("-fx-background-color: transparent; -fx-padding: 5px 8px; -fx-background-radius: 6px;");
            ScrollPane gridScroll = new ScrollPane(gridPane);
            gridScroll.setFitToWidth(true);
            gridScroll.setStyle("-fx-background-color: transparent; -fx-background: transparent; -fx-padding: 0;");
            contentArea.getChildren().setAll(gridScroll);
        } else {
            gridViewBtn.setStyle("-fx-background-color: transparent; -fx-padding: 5px 8px; -fx-background-radius: 6px;");
            tableViewBtn.setStyle("-fx-background-color: #24263A; -fx-padding: 5px 8px; -fx-background-radius: 6px;");
            contentArea.getChildren().setAll(tableView);
        }
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

        // Update Table
        tableView.getItems().setAll(filtered);

        // Update Grid
        gridPane.getChildren().clear();
        if (filtered.isEmpty()) {
            EmptyStateView emptyState = new EmptyStateView(
                    SvgIcon.IconName.PROJECTS,
                    "No Projects Found",
                    "No projects match the current search filters. Create a new project to get started.",
                    "Create New Project",
                    this::showCreateProjectModal
            );
            if (isGridView) {
                contentArea.getChildren().setAll(emptyState);
            }
        } else {
            for (Project p : filtered) {
                gridPane.getChildren().add(createProjectGridCard(p));
            }
            if (isGridView) {
                ScrollPane gridScroll = new ScrollPane(gridPane);
                gridScroll.setFitToWidth(true);
                gridScroll.setStyle("-fx-background-color: transparent; -fx-background: transparent; -fx-padding: 0;");
                contentArea.getChildren().setAll(gridScroll);
            }
        }
    }

    /**
     * Creates a Project Card matching Reference Screen 2 ("Choose a project to work"):
     * Includes stylized thumbnail illustration banner, title, relative modified time, and team avatars.
     */
    private VBox createProjectGridCard(Project project) {
        VBox card = new VBox(10);
        card.getStyleClass().add("project-grid-card");
        card.setPrefWidth(260);

        // 1. Thumbnail Banner with UI/Code Mockup Graphic
        StackPane thumbnail = new StackPane();
        thumbnail.getStyleClass().add("project-thumbnail-area");

        // Inner decorative preview graphic based on project type/name
        HBox mockupGraphic = createMockupGraphic(project);
        thumbnail.getChildren().add(mockupGraphic);

        // 2. Info Row: Title & Status
        HBox infoRow = new HBox(8);
        infoRow.setAlignment(Pos.CENTER_LEFT);

        Label title = new Label(project.getName());
        title.getStyleClass().add("project-card-title");
        title.setMaxWidth(140);

        boolean isSample = project.getId() != null &&
                (project.getId().startsWith("proj-00") || project.getId().startsWith("proj-ref") ||
                 (project.getRepositoryUrl() != null && project.getRepositoryUrl().contains("github.com/vit/")));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        StatusBadge statusBadge = new StatusBadge(project.getStatus());
        if (isSample) {
            Label demoBadge = new Label("SAMPLE");
            demoBadge.setStyle("-fx-background-color: rgba(245, 158, 11, 0.15); -fx-text-fill: #F59E0B; " +
                    "-fx-font-size: 9px; -fx-font-weight: bold; -fx-padding: 2px 6px; -fx-background-radius: 4px;");
            infoRow.getChildren().addAll(title, demoBadge, spacer, statusBadge);
        } else {
            infoRow.getChildren().addAll(title, spacer, statusBadge);
        }

        // 3. Footer: Last Modified & Branch
        HBox footer = new HBox(8);
        footer.setAlignment(Pos.CENTER_LEFT);

        Label editedTime = new Label(formatRelativeTime(project.getUpdatedAt() != null ? project.getUpdatedAt() : project.getCreatedAt()));
        editedTime.getStyleClass().add("project-card-subtitle");

        Region footerSpacer = new Region();
        HBox.setHgrow(footerSpacer, Priority.ALWAYS);

        // Real Branch / Type Pill
        Label branchPill = createBranchPill(project);
        footer.getChildren().addAll(editedTime, footerSpacer, branchPill);

        card.getChildren().addAll(thumbnail, infoRow, footer);

        card.setOnMouseClicked(e -> {
            selectedProject = project;
            if (e.getClickCount() == 2) {
                mainLayout.navigateToProjectDetail(project);
            }
        });

        // Context Menu on right click
        ContextMenu contextMenu = new ContextMenu();
        MenuItem openItem = new MenuItem("Open Project");
        openItem.setOnAction(e -> mainLayout.navigateToProjectDetail(project));
        MenuItem editItem = new MenuItem("Edit Configuration");
        editItem.setOnAction(e -> showEditProjectModal(project));
        MenuItem deleteItem = new MenuItem("Delete Project");
        deleteItem.setOnAction(e -> confirmDeleteProject(project));
        contextMenu.getItems().addAll(openItem, editItem, new SeparatorMenuItem(), deleteItem);

        card.setOnContextMenuRequested(e -> contextMenu.show(card, e.getScreenX(), e.getScreenY()));

        return card;
    }

    private HBox createMockupGraphic(Project project) {
        HBox graphic = new HBox(8);
        graphic.setAlignment(Pos.CENTER);
        graphic.setPadding(new Insets(10));

        String name = project.getName() != null ? project.getName().toLowerCase() : "";
        String accentColor = "#38BDF8";
        String secondaryColor = "#8B5CF6";

        if (name.contains("promo")) {
            accentColor = "#EC4899";
            secondaryColor = "#8B5CF6";
        } else if (name.contains("gold")) {
            accentColor = "#F59E0B";
            secondaryColor = "#D97706";
        } else if (name.contains("store") && !name.contains("in")) {
            accentColor = "#10B981";
            secondaryColor = "#38BDF8";
        } else if (name.contains("like")) {
            accentColor = "#EF4444";
            secondaryColor = "#F97316";
        } else if (name.contains("instore")) {
            accentColor = "#0284C7";
            secondaryColor = "#38BDF8";
        }

        VBox miniCard1 = new VBox(4);
        miniCard1.setStyle("-fx-background-color: #1A1C29; -fx-padding: 6px; -fx-background-radius: 6px; -fx-min-width: 65px;");
        Region bar1 = new Region();
        bar1.setStyle(String.format("-fx-background-color: %s; -fx-pref-height: 4px; -fx-pref-width: 30px; -fx-background-radius: 2px;", accentColor));
        Region bar2 = new Region();
        bar2.setStyle("-fx-background-color: #33364D; -fx-pref-height: 3px; -fx-pref-width: 45px; -fx-background-radius: 2px;");
        miniCard1.getChildren().addAll(bar1, bar2);

        VBox miniCard2 = new VBox(4);
        miniCard2.setStyle("-fx-background-color: #222538; -fx-padding: 6px; -fx-background-radius: 6px; -fx-min-width: 75px;");
        HBox topDotRow = new HBox(4);
        Region dot = new Region();
        dot.setStyle(String.format("-fx-background-color: %s; -fx-min-width: 6px; -fx-min-height: 6px; -fx-background-radius: 3px;", accentColor));
        Region tag = new Region();
        tag.setStyle(String.format("-fx-background-color: %s; -fx-pref-height: 4px; -fx-pref-width: 24px; -fx-background-radius: 2px;", secondaryColor));
        topDotRow.getChildren().addAll(dot, tag);

        Region line = new Region();
        line.setStyle("-fx-background-color: #4A4E6B; -fx-pref-height: 3px; -fx-pref-width: 50px; -fx-background-radius: 2px;");
        miniCard2.getChildren().addAll(topDotRow, line);

        graphic.getChildren().addAll(miniCard1, miniCard2);
        return graphic;
    }

    private Label createBranchPill(Project project) {
        String branch = project.getDefaultBranch() != null && !project.getDefaultBranch().isEmpty()
                ? project.getDefaultBranch() : "main";
        Label pill = new Label(branch);
        pill.setStyle("-fx-background-color: #24263A; -fx-text-fill: #8A8D9E; -fx-font-size: 10px; " +
                "-fx-padding: 2px 6px; -fx-background-radius: 4px; -fx-border-color: #33364D; -fx-border-radius: 4px;");
        return pill;
    }

    private String formatRelativeTime(java.sql.Timestamp ts) {
        if (ts == null) return "Created recently";
        long diff = System.currentTimeMillis() - ts.getTime();
        long hours = diff / (3600 * 1000);
        if (hours < 1) return "Edited just now";
        if (hours < 24) return "Edited " + hours + " hours ago";
        long days = hours / 24;
        return "Edited " + days + " days ago";
    }

    private void handleImportProject() {
        Alert choice = new Alert(Alert.AlertType.CONFIRMATION);
        choice.initOwner(mainLayout.getPrimaryStage());
        choice.setTitle("Import Project Source");
        choice.setHeaderText("Choose Project Source Type");
        choice.setContentText("Select whether to import from an existing local project folder or an uncompressed .zip archive:");

        ButtonType btnDir = new ButtonType("Local Directory");
        ButtonType btnZip = new ButtonType("Archive File (.zip)");
        ButtonType btnCancel = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        choice.getButtonTypes().setAll(btnDir, btnZip, btnCancel);

        var result = choice.showAndWait();
        if (result.isEmpty() || result.get() == btnCancel) return;

        if (result.get() == btnDir) {
            javafx.stage.DirectoryChooser dirChooser = new javafx.stage.DirectoryChooser();
            dirChooser.setTitle("Select Local Project Root Directory");
            File initial = new File(System.getProperty("user.dir"));
            if (initial.exists()) dirChooser.setInitialDirectory(initial);

            File selectedDir = dirChooser.showDialog(mainLayout.getPrimaryStage());
            if (selectedDir != null) {
                try {
                    com.devopshub.service.ProjectImportService.DetectedProject detected = importService.inspectDirectory(selectedDir);
                    String branch = detected.isHasGit() ? gitService.getCurrentBranch(selectedDir) : "main";
                    Project p = projectService.createProject(detected.getName(),
                            "Imported " + detected.getBuildSystem() + " project from " + selectedDir.getAbsolutePath(),
                            selectedDir.getAbsolutePath(), branch, detected.getProjectType());

                    // Auto-generate authentic delivery pipeline
                    List<com.devopshub.model.Stage> stages = importService.generateAuthenticStages(detected.getBuildSystem(), null);
                    pipelineService.createPipeline(p.getId(), p.getName() + "-CI",
                            "Automated delivery pipeline for " + detected.getBuildSystem(), "SEQUENTIAL", stages);

                    mainLayout.showToast(String.format("Project '%s' (%s) imported with delivery pipeline!",
                            p.getName(), detected.getBuildSystem()), ToastNotification.ToastType.SUCCESS);
                    refresh();
                } catch (Exception ex) {
                    mainLayout.showToast("Import failed: " + ex.getMessage(), ToastNotification.ToastType.ERROR);
                }
            }
        } else if (result.get() == btnZip) {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Select Project Archive (.zip)");
            fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Zip Archives (*.zip)", "*.zip"));
            File initial = new File(System.getProperty("user.dir"));
            if (initial.exists()) fileChooser.setInitialDirectory(initial);

            File selectedZip = fileChooser.showOpenDialog(mainLayout.getPrimaryStage());
            if (selectedZip != null) {
                try {
                    String folderName = selectedZip.getName().replaceAll("(?i)\\.zip$", "");
                    File targetExtractDir = new File(com.devopshub.config.AppConfig.getInstance().getWorkspaceDir(), "imported/" + folderName);
                    importService.extractArchiveSafely(selectedZip, targetExtractDir);

                    com.devopshub.service.ProjectImportService.DetectedProject detected = importService.inspectDirectory(targetExtractDir);
                    Project p = projectService.createProject(detected.getName(),
                            "Extracted " + detected.getBuildSystem() + " project from " + selectedZip.getName(),
                            targetExtractDir.getAbsolutePath(), "main", detected.getProjectType());

                    List<com.devopshub.model.Stage> stages = importService.generateAuthenticStages(detected.getBuildSystem(), null);
                    pipelineService.createPipeline(p.getId(), p.getName() + "-CI",
                            "Automated delivery pipeline for " + detected.getBuildSystem(), "SEQUENTIAL", stages);

                    mainLayout.showToast(String.format("Archive '%s' extracted safely (%s) with delivery pipeline!",
                            p.getName(), detected.getBuildSystem()), ToastNotification.ToastType.SUCCESS);
                    refresh();
                } catch (Exception ex) {
                    mainLayout.showToast("Archive import failed: " + ex.getMessage(), ToastNotification.ToastType.ERROR);
                }
            }
        }
    }

    private void handleRemoveSelected() {
        if (selectedProject == null) {
            mainLayout.showToast("Select a project first to remove.", ToastNotification.ToastType.WARNING);
            return;
        }
        confirmDeleteProject(selectedProject);
    }

    private void confirmDeleteProject(Project p) {
        boolean confirmed = ConfirmationDialog.show(mainLayout.getPrimaryStage(),
                "Delete Project",
                "Are you sure you want to delete project '" + p.getName() + "' and its associated delivery pipelines?\nThis operation is irreversible.",
                "Delete Project");
        if (confirmed) {
            try {
                projectService.deleteProject(p.getId());
                mainLayout.showToast("Project deleted successfully", ToastNotification.ToastType.SUCCESS);
                selectedProject = null;
                refresh();
            } catch (Exception ex) {
                mainLayout.showToast("Failed to delete project: " + ex.getMessage(), ToastNotification.ToastType.ERROR);
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
        modal.setResizable(true);

        VBox form = new VBox(14);
        form.setPadding(new Insets(24));
        form.setStyle("-fx-background-color: #181926; -fx-border-color: #27283A;");

        Label titleLabel = new Label(existing == null ? "Create Project" : "Edit Project");
        titleLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: 800; -fx-text-fill: #FFFFFF;");

        Label errorBanner = new Label();
        errorBanner.getStyleClass().add("form-error-label");
        errorBanner.setVisible(false);

        TextField nameField = new TextField(existing != null ? existing.getName() : "");
        nameField.setPromptText("Project Name (e.g. Payments Gateway)");

        TextArea descField = new TextArea(existing != null ? existing.getDescription() : "");
        descField.setPromptText("Project Description");
        descField.setPrefRowCount(3);

        TextField repoField = new TextField(existing != null ? existing.getRepositoryUrl() : "");
        repoField.setPromptText("Repository URL or local path");

        TextField branchField = new TextField(existing != null ? existing.getDefaultBranch() : "main");
        branchField.setPromptText("Default Branch (e.g. main)");

        ComboBox<String> typeCombo = new ComboBox<>();
        typeCombo.getItems().addAll("Java", "Java/Spring", "Python", "NodeJS", "Next.js (NodeJS)", "Go");
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
                nameField.setStyle("-fx-border-color: #F43F5E;");
                return;
            }
            if (!gitService.isValidRepositoryUrl(repo)) {
                errorBanner.setText("Invalid repository URL or local directory path.");
                errorBanner.setVisible(true);
                repoField.setStyle("-fx-border-color: #F43F5E;");
                return;
            }

            try {
                if (existing == null) {
                    String selectedType = typeCombo.getValue();
                    File localPath = new File(repo);
                    com.devopshub.service.ProjectImportService.DetectedProject detected = null;
                    File tempWorkspace = null;
                    if (localPath.exists() && localPath.isDirectory()) {
                        try {
                            detected = importService.inspectDirectory(localPath);
                            selectedType = detected.getProjectType();
                        } catch (Exception ignored) {}
                    } else if (gitService.isValidRepositoryUrl(repo)) {
                        try {
                            String branch = branchField.getText().trim().isEmpty() ? "main" : branchField.getText().trim();
                            tempWorkspace = new File(com.devopshub.config.AppConfig.getInstance().getWorkspaceDir(), "temp-inspect-" + java.util.UUID.randomUUID());
                            detected = importService.inspectGitRepository(repo, branch, tempWorkspace);
                            selectedType = detected.getProjectType();
                        } catch (Exception ex) {
                            // Non-fatal if offline/unreachable during form entry; fallback to selected combo type
                            if (tempWorkspace != null && tempWorkspace.exists()) {
                                gitService.safeCleanupDirectory(tempWorkspace);
                                tempWorkspace = null;
                            }
                        }
                    }

                    Project created = projectService.createProject(name, descField.getText().trim(),
                            repo, branchField.getText().trim(), selectedType);

                    // Move inspected repository directly to persistent project workspace
                    if (tempWorkspace != null && tempWorkspace.exists()) {
                        try {
                            File projectWs = gitService.getProjectWorkspace(created.getId());
                            if (projectWs.exists() && (projectWs.list() == null || projectWs.list().length == 0)) {
                                projectWs.delete();
                            }
                            boolean moved = tempWorkspace.renameTo(projectWs);
                            if (!moved) {
                                gitService.safeCleanupDirectory(tempWorkspace);
                            }
                        } catch (Exception ex) {
                            gitService.safeCleanupDirectory(tempWorkspace);
                        }
                    }

                    // Create automated delivery pipeline tailored to genuine detected build system
                    List<com.devopshub.model.Stage> defaultStages = detected != null ?
                            importService.generateAuthenticStages(detected, null) :
                            importService.generateAuthenticStages(selectedType, null);

                    pipelineService.createPipeline(created.getId(), created.getName() + "-CI",
                            "Automated delivery pipeline for " + created.getName(), "SEQUENTIAL", defaultStages);

                    mainLayout.showToast(String.format("Project '%s' (%s) created with delivery pipeline!",
                            created.getName(), selectedType), ToastNotification.ToastType.SUCCESS);
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

        Label lblName = new Label("Project Name:");
        lblName.setStyle("-fx-font-weight: bold; -fx-text-fill: #E2E8F0;");
        Label lblDesc = new Label("Description:");
        lblDesc.setStyle("-fx-font-weight: bold; -fx-text-fill: #E2E8F0;");
        Label lblRepo = new Label("Repository URL / Local Path:");
        lblRepo.setStyle("-fx-font-weight: bold; -fx-text-fill: #E2E8F0;");
        Label lblBranch = new Label("Default Branch:");
        lblBranch.setStyle("-fx-font-weight: bold; -fx-text-fill: #E2E8F0;");
        Label lblType = new Label("Project Type:");
        lblType.setStyle("-fx-font-weight: bold; -fx-text-fill: #E2E8F0;");

        form.getChildren().addAll(
                titleLabel,
                errorBanner,
                lblName, nameField,
                lblDesc, descField,
                lblRepo, repoField,
                lblBranch, branchField,
                lblType, typeCombo,
                btnBox
        );

        ScrollPane modalScroll = new ScrollPane(form);
        modalScroll.setFitToWidth(true);
        modalScroll.setStyle("-fx-background-color: transparent; -fx-background: #181926; -fx-padding: 0;");

        Scene scene = new Scene(modalScroll, 520, 620);
        scene.getStylesheets().add(getClass().getResource("/css/devopshub.css").toExternalForm());
        modal.setScene(scene);
        modal.showAndWait();
    }

    public void setSearchQuery(String query) {
        if (searchField != null) {
            searchField.setText(query != null ? query : "");
        }
    }
}
