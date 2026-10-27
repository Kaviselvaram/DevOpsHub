package com.devopshub.ui.view;

import com.devopshub.execution.ExecutionEngine;
import com.devopshub.model.*;
import com.devopshub.pipeline.PipelineValidator;
import com.devopshub.service.PipelineService;
import com.devopshub.service.ProjectService;
import com.devopshub.ui.component.SvgIcon;
import com.devopshub.ui.component.ToastNotification;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.CubicCurve;

import java.util.*;

/**
 * Visual Pipeline Builder & Interactive Workflow Map matching Reference Screen 1:
 * Connected stage pills with timeline grid, drag-order stage hierarchy,
 * shell/Maven step parameters, DAG cycle validation, and execution trigger.
 */
public class PipelineBuilderView extends VBox {

    private final MainLayoutView mainLayout;
    private final PipelineService pipelineService;
    private final ProjectService projectService;
    private final ExecutionEngine executionEngine;
    private final Pipeline existingPipeline;
    private final PipelineValidator validator = new PipelineValidator();

    private Label screenTitle;
    private Label subtitle;
    private TextField searchField;
    private TextField nameField;
    private ComboBox<Project> projectCombo;
    private ComboBox<String> modeCombo;
    private TextArea descField;
    private Label pipelineNameError;

    private final ObservableList<Stage> stageList = FXCollections.observableArrayList();
    private FilteredList<Stage> filteredStages;
    private ListView<Stage> stageListView;
    private VBox stageConfigBox;
    private Stage selectedStage;

    // Visual DAG Flow Map Canvas (Matching Reference Screen 1)
    private Pane workflowCanvas;
    private HBox timeScaleTabBar;

    // Stage Form Controls
    private TextField stageNameField;
    private Label stageNameError;
    private ComboBox<StageType> stageTypeCombo;
    private TextArea stageCmdField;
    private Label stageCmdError;
    private TextField stageTimeoutField;
    private Label stageTimeoutError;
    private CheckBox stageEnabledCheck;
    private TextField stageParallelGroupField;
    private VBox dependenciesBox;

    public PipelineBuilderView(MainLayoutView mainLayout, PipelineService pipelineService,
                               ProjectService projectService, Pipeline existingPipeline) {
        this.mainLayout = mainLayout;
        this.pipelineService = pipelineService;
        this.projectService = projectService;
        this.executionEngine = ExecutionEngine.getInstance();
        this.existingPipeline = existingPipeline;

        this.setSpacing(18);
        this.setPadding(new Insets(24));
        this.setStyle("-fx-background-color: transparent;");

        buildHeaderToolbar();
        buildInteractiveWorkflowSection();
        buildStageComposer();
        buildActionButtons();

        loadData();
        refreshVisualFlow();
    }

    private void buildHeaderToolbar() {
        HBox topBar = new HBox(14);
        topBar.setAlignment(Pos.CENTER_LEFT);

        VBox titleBox = new VBox(2);
        subtitle = new Label("Project: None");
        subtitle.setStyle("-fx-font-size: 11px; -fx-text-fill: #8A8D9E;");
        screenTitle = new Label(existingPipeline == null ? "New Pipeline" : existingPipeline.getName());
        screenTitle.setStyle("-fx-font-size: 18px; -fx-font-weight: 800; -fx-text-fill: #FFFFFF;");
        titleBox.getChildren().addAll(subtitle, screenTitle);

        searchField = new TextField();
        searchField.setPromptText("Search stages, commands, or artifacts...");
        searchField.getStyleClass().add("header-search-field");
        searchField.setPrefWidth(240);
        searchField.textProperty().addListener((o, oldVal, newVal) -> {
            if (filteredStages != null) {
                filteredStages.setPredicate(s -> {
                    if (newVal == null || newVal.isBlank()) return true;
                    String q = newVal.toLowerCase().trim();
                    boolean nameMatch = s.getName() != null && s.getName().toLowerCase().contains(q);
                    boolean cmdMatch = s.getCommand() != null && s.getCommand().toLowerCase().contains(q);
                    boolean typeMatch = s.getStageType() != null && s.getStageType().name().toLowerCase().contains(q);
                    return nameMatch || cmdMatch || typeMatch;
                });
            }
        });

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // Date pill matching current date
        String todayDate = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy"));
        Label datePill = new Label(todayDate);
        datePill.getStyleClass().add("header-date-badge");

        // Scale Tabs matching reference: Hours | Day | Week | Month
        timeScaleTabBar = new HBox(2);
        timeScaleTabBar.setStyle("-fx-background-color: #1A1B29; -fx-padding: 3px; -fx-background-radius: 8px; -fx-border-color: #27283B;");

        String[] tabs = {"Hours", "Day", "Week", "Month"};
        for (int i = 0; i < tabs.length; i++) {
            Button tab = new Button(tabs[i]);
            boolean active = i == 0;
            tab.setStyle(String.format("-fx-background-color: %s; -fx-text-fill: %s; -fx-font-size: 11px; -fx-font-weight: %s; -fx-padding: 4px 10px; -fx-background-radius: 6px; -fx-cursor: hand;",
                    active ? "#26283C" : "transparent", active ? "#38BDF8" : "#8A8D9E", active ? "bold" : "normal"));
            int idx = i;
            tab.setOnAction(e -> {
                for (var node : timeScaleTabBar.getChildren()) {
                    ((Button) node).setStyle("-fx-background-color: transparent; -fx-text-fill: #8A8D9E; -fx-font-size: 11px; -fx-padding: 4px 10px; -fx-background-radius: 6px; -fx-cursor: hand;");
                }
                tab.setStyle("-fx-background-color: #26283C; -fx-text-fill: #38BDF8; -fx-font-size: 11px; -fx-font-weight: bold; -fx-padding: 4px 10px; -fx-background-radius: 6px; -fx-cursor: hand;");
            });
            timeScaleTabBar.getChildren().add(tab);
        }

        topBar.getChildren().addAll(titleBox, searchField, spacer, datePill, timeScaleTabBar);
        this.getChildren().add(topBar);

        // Metadata Card
        VBox metaCard = new VBox(10);
        metaCard.getStyleClass().add("card");
        metaCard.setStyle("-fx-background-color: #181926; -fx-padding: 16px;");

        GridPane grid = new GridPane();
        grid.setHgap(16);
        grid.setVgap(10);

        nameField = new TextField();
        nameField.setPromptText("Pipeline Name (e.g. Master-Release-CI)");
        nameField.textProperty().addListener((o, old, val) -> {
            if (val.trim().isEmpty()) {
                pipelineNameError.setText("Pipeline name is required.");
                pipelineNameError.setVisible(true);
                screenTitle.setText(existingPipeline != null ? existingPipeline.getName() : "New Pipeline");
            } else {
                pipelineNameError.setVisible(false);
                screenTitle.setText(val.trim());
            }
        });

        pipelineNameError = new Label();
        pipelineNameError.getStyleClass().add("form-error-label");
        pipelineNameError.setVisible(false);

        VBox nameBox = new VBox(2, nameField, pipelineNameError);

        projectCombo = new ComboBox<>();
        projectCombo.setPromptText("Select Associated Project");
        projectCombo.valueProperty().addListener((o, old, val) -> {
            subtitle.setText(val != null ? "Project: " + val.getName() : "Project: None");
        });

        modeCombo = new ComboBox<>();
        modeCombo.getItems().addAll("SEQUENTIAL", "PARALLEL_FORK");
        modeCombo.setValue("SEQUENTIAL");
        modeCombo.valueProperty().addListener((o, old, val) -> refreshVisualFlow());

        descField = new TextArea();
        descField.setPromptText("Pipeline Description and Objectives");
        descField.setPrefRowCount(2);

        Label nameLbl = new Label("Pipeline Name:");
        nameLbl.setMinWidth(Region.USE_PREF_SIZE);
        Label projLbl = new Label("Project:");
        projLbl.setMinWidth(Region.USE_PREF_SIZE);
        Label modeLbl = new Label("Execution Mode:");
        modeLbl.setMinWidth(Region.USE_PREF_SIZE);
        Label descLbl = new Label("Description:");
        descLbl.setMinWidth(Region.USE_PREF_SIZE);

        grid.add(nameLbl, 0, 0);
        grid.add(nameBox, 1, 0);
        grid.add(projLbl, 2, 0);
        grid.add(projectCombo, 3, 0);

        grid.add(modeLbl, 0, 1);
        grid.add(modeCombo, 1, 1);
        grid.add(descLbl, 2, 1);
        grid.add(descField, 3, 1);

        metaCard.getChildren().add(grid);
        this.getChildren().add(metaCard);
    }

    /**
     * Interactive Workflow Map matching Reference Screen 1:
     * Dark timeline canvas with connected pills and curved connectors.
     */
    private void buildInteractiveWorkflowSection() {
        VBox mapBox = new VBox(8);
        mapBox.getStyleClass().add("card");
        mapBox.setStyle("-fx-background-color: #13141F; -fx-border-color: #212232; -fx-padding: 18px;");

        HBox mapHeader = new HBox(8);
        mapHeader.setAlignment(Pos.CENTER_LEFT);

        Label mapTitle = new Label("Interactive Execution Topology Map");
        mapTitle.setStyle("-fx-font-size: 13px; -fx-font-weight: 700; -fx-text-fill: #FFFFFF;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label tip = new Label("Click any stage pill to inspect parameters below");
        tip.setStyle("-fx-font-size: 11px; -fx-text-fill: #8A8D9E;");

        mapHeader.getChildren().addAll(mapTitle, spacer, tip);

        workflowCanvas = new Pane();
        workflowCanvas.setPrefHeight(230);
        workflowCanvas.setMinHeight(230);
        workflowCanvas.setStyle("-fx-background-color: #10111A; -fx-background-radius: 8px; -fx-border-color: #1E2030; -fx-border-radius: 8px;");

        mapBox.getChildren().addAll(mapHeader, workflowCanvas);
        this.getChildren().add(mapBox);
    }

    private void refreshVisualFlow() {
        if (workflowCanvas == null) return;
        workflowCanvas.getChildren().clear();

        // 1. Draw subtle background vertical timeline guides
        double width = Math.max(760, workflowCanvas.getWidth());
        int cols = 6;
        for (int i = 1; i < cols; i++) {
            double x = (width / cols) * i;
            Region line = new Region();
            line.setStyle("-fx-background-color: #181928; -fx-pref-width: 1px;");
            line.setLayoutX(x);
            line.setLayoutY(0);
            line.setPrefHeight(230);
            workflowCanvas.getChildren().add(line);
        }

        if (stageList.isEmpty()) {
            Label empty = new Label("No stages defined yet. Use the composer below to add stages.");
            empty.setStyle("-fx-font-size: 12px; -fx-text-fill: #8A8D9E;");
            empty.setLayoutX(30);
            empty.setLayoutY(100);
            workflowCanvas.getChildren().add(empty);
            return;
        }

        // 2. Render connected pills matching Reference Screen 1
        String[] pillColors = {"#F43F5E", "#38BDF8", "#F59E0B", "#EC4899", "#8B5CF6", "#10B981"};
        List<HBox> renderedPills = new ArrayList<>();
        List<double[]> pillCoordinates = new ArrayList<>();

        double startX = 30;
        double startY = 30;
        double stepX = 140;
        double stepY = 32;

        for (int i = 0; i < stageList.size(); i++) {
            Stage stage = stageList.get(i);
            String accentColor = pillColors[i % pillColors.length];

            HBox pill = new HBox(8);
            pill.getStyleClass().add("timeline-pill");
            pill.setStyle(String.format("-fx-background-color: #1E1F2D; -fx-border-color: %s; -fx-border-width: 1px 1px 1px 4px; -fx-border-radius: 18px; -fx-background-radius: 18px; -fx-padding: 6px 14px; -fx-cursor: hand;", accentColor));

            Label pillLabel = new Label(stage.getName());
            pillLabel.getStyleClass().add("timeline-pill-title");

            // Team Avatars on pill
            HBox avatars = createMiniAvatarGroup(i);

            pill.getChildren().addAll(pillLabel, avatars);

            // Stagger position diagonally/across like reference
            double px = startX + (i * stepX);
            double py = startY + ((i % 4) * stepY);

            pill.setLayoutX(px);
            pill.setLayoutY(py);

            final Stage stageRef = stage;
            pill.setOnMouseClicked(e -> {
                stageListView.getSelectionModel().select(stageRef);
            });

            workflowCanvas.getChildren().add(pill);
            renderedPills.add(pill);
            pillCoordinates.add(new double[]{px, py});
        }

        // 3. Draw Connecting Curves between stages
        for (int i = 0; i < pillCoordinates.size() - 1; i++) {
            double[] from = pillCoordinates.get(i);
            double[] to = pillCoordinates.get(i + 1);

            CubicCurve curve = new CubicCurve();
            curve.setStartX(from[0] + 120);
            curve.setStartY(from[1] + 16);
            curve.setControlX1(from[0] + 150);
            curve.setControlY1(from[1] + 16);
            curve.setControlX2(to[0] - 30);
            curve.setControlY2(to[1] + 16);
            curve.setEndX(to[0]);
            curve.setEndY(to[1] + 16);

            curve.setStroke(Color.web("#3A3D54"));
            curve.setStrokeWidth(1.8);
            curve.setFill(null);

            // Insert curves behind pills
            workflowCanvas.getChildren().add(0, curve);
        }
    }

    private HBox createMiniAvatarGroup(int idx) {
        HBox group = new HBox(-4);
        group.setAlignment(Pos.CENTER_RIGHT);

        String[] colors = {"#38BDF8", "#8B5CF6", "#10B981"};
        String[][] inits = {{"NJ", "MP"}, {"OH", "MV"}, {"TK", "AL"}};

        StackPane a1 = new StackPane();
        a1.setStyle(String.format("-fx-background-color: %s; -fx-min-width: 16px; -fx-min-height: 16px; -fx-max-width: 16px; -fx-max-height: 16px; -fx-background-radius: 8px;", colors[idx % 3]));
        Label l1 = new Label(inits[idx % 3][0]);
        l1.setStyle("-fx-font-size: 7px; -fx-font-weight: bold; -fx-text-fill: #FFFFFF;");
        a1.getChildren().add(l1);

        StackPane a2 = new StackPane();
        a2.setStyle(String.format("-fx-background-color: %s; -fx-min-width: 16px; -fx-min-height: 16px; -fx-max-width: 16px; -fx-max-height: 16px; -fx-background-radius: 8px;", colors[(idx + 1) % 3]));
        Label l2 = new Label(inits[idx % 3][1]);
        l2.setStyle("-fx-font-size: 7px; -fx-font-weight: bold; -fx-text-fill: #FFFFFF;");
        a2.getChildren().add(l2);

        group.getChildren().addAll(a1, a2);
        return group;
    }



    private void buildStageComposer() {
        HBox composer = new HBox(20);
        VBox.setVgrow(composer, Priority.ALWAYS);

        // Left List: Stages Order
        VBox leftBox = new VBox(10);
        leftBox.getStyleClass().add("card");
        leftBox.setStyle("-fx-background-color: #181926;");
        leftBox.setPrefWidth(350);
        leftBox.setMinWidth(300);

        HBox listHeader = new HBox(8);
        listHeader.setAlignment(Pos.CENTER_LEFT);
        Label stagesTitle = new Label("Stage Hierarchy");
        stagesTitle.getStyleClass().add("card-header");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button addStageBtn = new Button("Add Stage");
        addStageBtn.getStyleClass().add("btn-secondary");
        addStageBtn.setGraphic(new SvgIcon(SvgIcon.IconName.PLUS, 12, Color.web("#38BDF8")));
        addStageBtn.setOnAction(e -> addNewStage());

        listHeader.getChildren().addAll(stagesTitle, spacer, addStageBtn);

        filteredStages = new FilteredList<>(stageList, s -> true);
        stageListView = new ListView<>(filteredStages);
        VBox.setVgrow(stageListView, Priority.ALWAYS);
        stageListView.setPrefHeight(320);
        stageListView.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(Stage item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    HBox row = new HBox(8);
                    row.setAlignment(Pos.CENTER_LEFT);
                    int realIdx = stageList.indexOf(item);
                    Label idx = new Label(String.valueOf(realIdx >= 0 ? realIdx + 1 : getIndex() + 1));
                    idx.setStyle("-fx-font-weight: bold; -fx-text-fill: #616478; -fx-min-width: 18px;");
                    String stageNameText = (item.getName() != null && !item.getName().isBlank()) ? item.getName() : "Stage " + (realIdx >= 0 ? realIdx + 1 : getIndex() + 1);
                    Label name = new Label(stageNameText);
                    name.setStyle("-fx-font-weight: bold; -fx-text-fill: #FFFFFF;");
                    HBox.setHgrow(name, Priority.ALWAYS);
                    Label type = new Label("[" + item.getStageType() + "]");
                    type.setStyle("-fx-font-size: 11px; -fx-text-fill: #38BDF8;");
                    type.setMinWidth(Region.USE_PREF_SIZE);
                    row.getChildren().addAll(idx, name, type);
                    setGraphic(row);
                }
            }
        });

        stageListView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                bindStageToForm(newVal);
            }
        });

        // Reorder buttons
        HBox orderBtns = new HBox(8);
        Button upBtn = new Button("Move Up");
        upBtn.getStyleClass().add("btn-secondary");
        upBtn.setOnAction(e -> moveStage(-1));

        Button downBtn = new Button("Move Down");
        downBtn.getStyleClass().add("btn-secondary");
        downBtn.setOnAction(e -> moveStage(1));

        Button deleteStageBtn = new Button("Remove");
        deleteStageBtn.getStyleClass().add("btn-danger");
        deleteStageBtn.setOnAction(e -> removeSelectedStage());

        orderBtns.getChildren().addAll(upBtn, downBtn, deleteStageBtn);
        leftBox.getChildren().addAll(listHeader, stageListView, orderBtns);

        // Right Panel: Selected Stage Detail Configuration
        stageConfigBox = new VBox(10);
        stageConfigBox.getStyleClass().add("card");
        stageConfigBox.setStyle("-fx-background-color: #181926;");
        HBox.setHgrow(stageConfigBox, Priority.ALWAYS);

        Label configTitle = new Label("Stage Configuration & Execution Command");
        configTitle.getStyleClass().add("card-header");

        stageNameField = new TextField();
        stageNameError = new Label();
        stageNameError.getStyleClass().add("form-error-label");
        stageNameError.setVisible(false);
        stageNameField.textProperty().addListener((o, old, val) -> {
            if (selectedStage != null) {
                selectedStage.setName(val);
                stageListView.refresh();
                refreshVisualFlow();
            }
        });

        stageTypeCombo = new ComboBox<>();
        stageTypeCombo.getItems().addAll(StageType.values());
        stageTypeCombo.valueProperty().addListener((o, old, val) -> {
            if (selectedStage != null && val != null) {
                selectedStage.setStageType(val);
                stageListView.refresh();
                refreshVisualFlow();
            }
        });

        stageCmdField = new TextArea();
        stageCmdField.setPrefRowCount(3);
        stageCmdField.setStyle("-fx-font-family: Menlo, 'JetBrains Mono', monospace; -fx-font-size: 12px;");
        stageCmdError = new Label();
        stageCmdError.getStyleClass().add("form-error-label");
        stageCmdError.setVisible(false);
        stageCmdField.textProperty().addListener((o, old, val) -> {
            if (selectedStage != null) selectedStage.setCommand(val);
        });

        stageTimeoutField = new TextField();
        stageTimeoutError = new Label();
        stageTimeoutError.getStyleClass().add("form-error-label");
        stageTimeoutError.setVisible(false);
        stageTimeoutField.textProperty().addListener((o, old, val) -> {
            if (selectedStage != null) {
                try {
                    int sec = Integer.parseInt(val.trim());
                    selectedStage.setTimeoutSeconds(sec);
                    stageTimeoutError.setVisible(false);
                } catch (Exception e) {
                    stageTimeoutError.setText("Timeout must be a valid integer seconds.");
                    stageTimeoutError.setVisible(true);
                }
            }
        });

        stageEnabledCheck = new CheckBox("Stage Enabled for Execution");
        stageEnabledCheck.selectedProperty().addListener((o, old, val) -> {
            if (selectedStage != null) selectedStage.setEnabled(val);
        });

        stageParallelGroupField = new TextField();
        stageParallelGroupField.setPromptText("Parallel Group Tag (e.g. TEST_FORK)");
        stageParallelGroupField.textProperty().addListener((o, old, val) -> {
            if (selectedStage != null) {
                selectedStage.setParallelGroup(val.trim().isEmpty() ? null : val.trim());
                refreshVisualFlow();
            }
        });

        // Dependencies checklist section
        dependenciesBox = new VBox(6);
        Label depTitle = new Label("Prerequisite Stage Dependencies (DAG):");
        depTitle.setStyle("-fx-font-weight: bold; -fx-font-size: 12px; -fx-text-fill: #E2E8F0;");
        dependenciesBox.getChildren().add(depTitle);

        stageConfigBox.getChildren().addAll(
                configTitle,
                new Label("Stage Name:"), stageNameField, stageNameError,
                new Label("Stage Type:"), stageTypeCombo,
                new Label("Shell / Maven Command:"), stageCmdField, stageCmdError,
                new Label("Timeout (seconds):"), stageTimeoutField, stageTimeoutError,
                new Label("Parallel Group Tag (Optional):"), stageParallelGroupField,
                stageEnabledCheck,
                dependenciesBox
        );

        composer.getChildren().addAll(leftBox, stageConfigBox);
        this.getChildren().add(composer);
    }

    private void updateDependenciesChecklist() {
        if (selectedStage == null || dependenciesBox == null) return;
        dependenciesBox.getChildren().clear();

        Label depTitle = new Label("Prerequisite Stage Dependencies (DAG):");
        depTitle.setStyle("-fx-font-weight: bold; -fx-font-size: 12px; -fx-text-fill: #E2E8F0;");
        dependenciesBox.getChildren().add(depTitle);

        List<String> currentDeps = selectedStage.getDependsOnStageIds();
        if (currentDeps == null) {
            currentDeps = new ArrayList<>();
            selectedStage.setDependsOnStageIds(currentDeps);
        }

        FlowPane chips = new FlowPane(8, 8);
        for (Stage other : stageList) {
            if (other.getId().equals(selectedStage.getId())) continue;

            CheckBox cb = new CheckBox(other.getName());
            cb.setSelected(currentDeps.contains(other.getId()));
            List<String> finalCurrentDeps = currentDeps;
            cb.selectedProperty().addListener((obs, old, isSelected) -> {
                if (isSelected) {
                    if (!finalCurrentDeps.contains(other.getId())) finalCurrentDeps.add(other.getId());
                } else {
                    finalCurrentDeps.remove(other.getId());
                }
            });
            chips.getChildren().add(cb);
        }

        if (chips.getChildren().isEmpty()) {
            Label noOther = new Label("No other stages available to depend on.");
            noOther.setStyle("-fx-font-size: 11px; -fx-text-fill: #8A8D9E;");
            dependenciesBox.getChildren().add(noOther);
        } else {
            dependenciesBox.getChildren().add(chips);
        }
    }

    private void buildActionButtons() {
        HBox actions = new HBox(12);
        actions.setAlignment(Pos.CENTER_RIGHT);

        Button cancelBtn = new Button("Cancel");
        cancelBtn.getStyleClass().add("btn-secondary");
        cancelBtn.setOnAction(e -> mainLayout.navigateToPipelines());

        Button validateBtn = new Button("Validate DAG");
        validateBtn.getStyleClass().add("btn-secondary");
        validateBtn.setGraphic(new SvgIcon(SvgIcon.IconName.CHECK_CIRCLE, 13, Color.web("#10B981")));
        validateBtn.setOnAction(e -> validateCurrentPipeline());

        Button saveBtn = new Button("Save Pipeline");
        saveBtn.getStyleClass().add("btn-primary");
        saveBtn.setOnAction(e -> savePipeline(false));

        Button saveAndRunBtn = new Button("Save & Run Now");
        saveAndRunBtn.getStyleClass().add("btn-success");
        saveAndRunBtn.setGraphic(new SvgIcon(SvgIcon.IconName.PLAY, 12, Color.WHITE));
        saveAndRunBtn.setOnAction(e -> savePipeline(true));

        actions.getChildren().addAll(cancelBtn, validateBtn, saveBtn, saveAndRunBtn);
        this.getChildren().add(actions);
    }

    private void loadData() {
        List<Project> projs = projectService.getAllProjects();
        projectCombo.getItems().setAll(projs);

        if (existingPipeline != null) {
            nameField.setText(existingPipeline.getName());
            descField.setText(existingPipeline.getDescription());
            modeCombo.setValue(existingPipeline.getExecutionMode());
            for (Project p : projs) {
                if (p.getId().equals(existingPipeline.getProjectId())) {
                    projectCombo.setValue(p);
                    break;
                }
            }
            if (existingPipeline.getStages() != null) {
                stageList.setAll(existingPipeline.getStages());
            }
        } else {
            if (!projs.isEmpty()) {
                projectCombo.setValue(projs.get(0));
            }
            stageList.setAll(pipelineService.createDefaultStages(UUID.randomUUID().toString()));
        }

        if (!stageList.isEmpty()) {
            stageListView.getSelectionModel().select(stageList.get(0));
        }
    }

    private void bindStageToForm(Stage stage) {
        this.selectedStage = stage;
        stageNameField.setText(stage.getName());
        stageTypeCombo.setValue(stage.getStageType());
        stageCmdField.setText(stage.getCommand());
        stageTimeoutField.setText(String.valueOf(stage.getTimeoutSeconds()));
        stageEnabledCheck.setSelected(stage.isEnabled());
        stageParallelGroupField.setText(stage.getParallelGroup() != null ? stage.getParallelGroup() : "");
        updateDependenciesChecklist();
    }

    private void addNewStage() {
        Stage stage = new Stage(UUID.randomUUID().toString(),
                existingPipeline != null ? existingPipeline.getId() : UUID.randomUUID().toString(),
                "New Stage", StageType.BUILD, stageList.size() + 1,
                "mvn compile -B", 180, true, null);
        stageList.add(stage);
        stageListView.getSelectionModel().select(stage);
        refreshVisualFlow();
    }

    private void removeSelectedStage() {
        Stage selected = stageListView.getSelectionModel().getSelectedItem();
        if (selected != null) {
            int idx = stageList.indexOf(selected);
            if (idx >= 0) {
                String removedId = selected.getId();
                stageList.remove(idx);
                // Prune dangling dependencies from remaining stages
                for (Stage remaining : stageList) {
                    if (remaining.getDependsOnStageIds() != null) {
                        remaining.getDependsOnStageIds().remove(removedId);
                    }
                }
                if (!stageList.isEmpty()) {
                    int nextIdx = Math.min(idx, stageList.size() - 1);
                    stageListView.getSelectionModel().select(stageList.get(nextIdx));
                }
                updateDependenciesChecklist();
                refreshVisualFlow();
            }
        }
    }

    private void moveStage(int delta) {
        Stage selected = stageListView.getSelectionModel().getSelectedItem();
        if (selected != null) {
            int idx = stageList.indexOf(selected);
            int newIdx = idx + delta;
            if (idx >= 0 && newIdx >= 0 && newIdx < stageList.size()) {
                Stage item = stageList.remove(idx);
                stageList.add(newIdx, item);
                stageListView.getSelectionModel().select(item);
                for (int k = 0; k < stageList.size(); k++) {
                    stageList.get(k).setStageOrder(k + 1);
                }
                refreshVisualFlow();
            }
        }
    }

    private boolean validateCurrentPipeline() {
        try {
            Pipeline temp = buildPipelineModel();
            validator.validate(temp);
            mainLayout.showToast("Pipeline DAG is valid without cycles!", ToastNotification.ToastType.SUCCESS);
            return true;
        } catch (Exception ex) {
            mainLayout.showToast("Validation Error: " + ex.getMessage(), ToastNotification.ToastType.ERROR);
            return false;
        }
    }

    private Pipeline buildPipelineModel() {
        String name = nameField.getText().trim();
        if (name.isEmpty()) {
            throw new IllegalArgumentException("Pipeline name cannot be empty.");
        }
        Project proj = projectCombo.getValue();
        if (proj == null) {
            throw new IllegalArgumentException("Please select an associated project.");
        }
        Pipeline p = new Pipeline();
        p.setId(existingPipeline != null ? existingPipeline.getId() : UUID.randomUUID().toString());
        p.setProjectId(proj.getId());
        p.setName(name);
        p.setDescription(descField.getText());
        p.setExecutionMode(modeCombo.getValue());
        p.setStages(new ArrayList<>(stageList));
        return p;
    }

    private void savePipeline(boolean runImmediately) {
        if (!validateCurrentPipeline()) {
            return;
        }
        try {
            Pipeline model = buildPipelineModel();
            Pipeline saved;
            if (existingPipeline == null) {
                saved = pipelineService.createPipeline(model.getProjectId(), model.getName(),
                        model.getDescription(), model.getExecutionMode(), model.getStages());
                mainLayout.showToast("Pipeline created successfully!", ToastNotification.ToastType.SUCCESS);
            } else {
                saved = pipelineService.updatePipeline(model.getId(), model.getName(), model.getDescription(),
                        model.getExecutionMode(), true, model.getStages());
                mainLayout.showToast("Pipeline updated successfully!", ToastNotification.ToastType.SUCCESS);
            }

            if (runImmediately) {
                mainLayout.showToast("Initiating execution...", ToastNotification.ToastType.INFO);
                Execution ex = executionEngine.startExecution(saved, TriggerType.MANUAL, null, null);
                mainLayout.navigateToExecutionDetail(ex.getId());
            } else {
                mainLayout.navigateToPipelines();
            }
        } catch (Exception ex) {
            Alert alert = new Alert(Alert.AlertType.ERROR, ex.getMessage(), ButtonType.OK);
            alert.showAndWait();
        }
    }
}
