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
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;

import java.util.*;

/**
 * Visual Pipeline Builder View:
 * Interactive stage composition, visual DAG flowchart, stage dependency configuration,
 * inline validation, and execution trigger.
 */
public class PipelineBuilderView extends VBox {

    private final MainLayoutView mainLayout;
    private final PipelineService pipelineService;
    private final ProjectService projectService;
    private final ExecutionEngine executionEngine;
    private final Pipeline existingPipeline;
    private final PipelineValidator validator = new PipelineValidator();

    private TextField nameField;
    private ComboBox<Project> projectCombo;
    private ComboBox<String> modeCombo;
    private TextArea descField;
    private Label pipelineNameError;

    private final ObservableList<Stage> stageList = FXCollections.observableArrayList();
    private ListView<Stage> stageListView;
    private VBox stageConfigBox;
    private Stage selectedStage;

    // Visual DAG Flow Canvas
    private HBox flowVisualizerContainer;

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
        this.executionEngine = new ExecutionEngine();
        this.existingPipeline = existingPipeline;

        this.setSpacing(18);
        this.setPadding(new Insets(24));
        this.setStyle("-fx-background-color: transparent;");

        buildBreadcrumb();
        buildHeaderForm();
        buildVisualFlowSection();
        buildStageComposer();
        buildActionButtons();

        loadData();
        refreshVisualFlow();
    }

    private void buildBreadcrumb() {
        HBox breadcrumb = new HBox(6);
        breadcrumb.getStyleClass().add("breadcrumb-bar");

        Label parentLink = new Label("Pipelines");
        parentLink.getStyleClass().add("breadcrumb-item");
        parentLink.setOnMouseClicked(e -> mainLayout.navigateToPipelines());

        Label separator = new Label("/");
        separator.getStyleClass().add("breadcrumb-separator");

        String currentTitle = existingPipeline == null ? "Create Pipeline" : "Edit: " + existingPipeline.getName();
        Label current = new Label(currentTitle);
        current.getStyleClass().add("breadcrumb-current");

        breadcrumb.getChildren().addAll(parentLink, separator, current);
        this.getChildren().add(breadcrumb);
    }

    private void buildHeaderForm() {
        VBox card = new VBox(12);
        card.getStyleClass().add("card");

        Label sectionTitle = new Label("Pipeline Metadata & Execution Mode");
        sectionTitle.getStyleClass().add("card-header");

        GridPane grid = new GridPane();
        grid.setHgap(16);
        grid.setVgap(10);

        nameField = new TextField();
        nameField.setPromptText("Pipeline Name (e.g. Master-Release-CI)");
        nameField.textProperty().addListener((o, old, val) -> {
            if (val.trim().isEmpty()) {
                pipelineNameError.setText("Pipeline name is required.");
                pipelineNameError.setVisible(true);
            } else {
                pipelineNameError.setVisible(false);
            }
        });

        pipelineNameError = new Label();
        pipelineNameError.getStyleClass().add("form-error-label");
        pipelineNameError.setVisible(false);

        VBox nameBox = new VBox(2, nameField, pipelineNameError);

        projectCombo = new ComboBox<>();
        projectCombo.setPromptText("Select Associated Project");

        modeCombo = new ComboBox<>();
        modeCombo.getItems().addAll("SEQUENTIAL", "PARALLEL_FORK");
        modeCombo.setValue("SEQUENTIAL");
        modeCombo.valueProperty().addListener((o, old, val) -> refreshVisualFlow());

        descField = new TextArea();
        descField.setPromptText("Pipeline Description and Objectives");
        descField.setPrefRowCount(2);

        grid.add(new Label("Pipeline Name:"), 0, 0);
        grid.add(nameBox, 1, 0);
        grid.add(new Label("Project:"), 2, 0);
        grid.add(projectCombo, 3, 0);

        grid.add(new Label("Execution Mode:"), 0, 1);
        grid.add(modeCombo, 1, 1);
        grid.add(new Label("Description:"), 2, 1);
        grid.add(descField, 3, 1);

        card.getChildren().addAll(sectionTitle, grid);
        this.getChildren().add(card);
    }

    private void buildVisualFlowSection() {
        VBox card = new VBox(10);
        card.getStyleClass().add("card");

        HBox header = new HBox(10);
        header.setAlignment(Pos.CENTER_LEFT);
        Label title = new Label("Visual Pipeline Execution Flowchart");
        title.getStyleClass().add("card-header");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label legend = new Label("Blue: Sequential Stage | Purple: Parallel Fork Group");
        legend.setStyle("-fx-font-size: 11px; -fx-text-fill: #64748B;");

        header.getChildren().addAll(title, spacer, legend);

        ScrollPane scroll = new ScrollPane();
        scroll.setFitToHeight(true);
        scroll.setPrefHeight(90);
        scroll.setStyle("-fx-background-color: transparent; -fx-background: transparent; -fx-padding: 0;");

        flowVisualizerContainer = new HBox(12);
        flowVisualizerContainer.setAlignment(Pos.CENTER_LEFT);
        flowVisualizerContainer.setPadding(new Insets(6));

        scroll.setContent(flowVisualizerContainer);
        card.getChildren().addAll(header, scroll);
        this.getChildren().add(card);
    }

    private void refreshVisualFlow() {
        if (flowVisualizerContainer == null) return;
        flowVisualizerContainer.getChildren().clear();

        if (stageList.isEmpty()) {
            Label empty = new Label("No stages defined yet. Add a stage below to build your pipeline flowchart.");
            empty.setStyle("-fx-font-size: 12px; -fx-text-fill: #94A3B8;");
            flowVisualizerContainer.getChildren().add(empty);
            return;
        }

        for (int i = 0; i < stageList.size(); i++) {
            Stage stage = stageList.get(i);
            boolean isParallel = stage.getParallelGroup() != null && !stage.getParallelGroup().trim().isEmpty();

            VBox node = new VBox(4);
            node.getStyleClass().add("flow-node");
            if (isParallel) {
                node.getStyleClass().add("flow-node-parallel");
            }
            node.setMinWidth(130);

            Label name = new Label((i + 1) + ". " + stage.getName());
            name.getStyleClass().add("flow-node-title");

            String badgeText = isParallel ? "FORK: " + stage.getParallelGroup() : stage.getStageType().name();
            Label badge = new Label(badgeText);
            badge.getStyleClass().add("flow-node-badge");

            node.getChildren().addAll(name, badge);
            flowVisualizerContainer.getChildren().add(node);

            if (i < stageList.size() - 1) {
                Label arrow = new Label("➔");
                arrow.getStyleClass().add("flow-arrow-label");
                flowVisualizerContainer.getChildren().add(arrow);
            }
        }
    }

    private void buildStageComposer() {
        HBox composer = new HBox(20);
        VBox.setVgrow(composer, Priority.ALWAYS);

        // Left List: Stages Order
        VBox leftBox = new VBox(10);
        leftBox.getStyleClass().add("card");
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
        addStageBtn.setGraphic(new SvgIcon(SvgIcon.IconName.PLUS, 12, Color.web("#0284C7")));
        addStageBtn.setOnAction(e -> addNewStage());

        listHeader.getChildren().addAll(stagesTitle, spacer, addStageBtn);

        stageListView = new ListView<>(stageList);
        VBox.setVgrow(stageListView, Priority.ALWAYS);
        stageListView.setPrefHeight(350);
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
                    Label idx = new Label(String.valueOf(getIndex() + 1));
                    idx.setStyle("-fx-font-weight: bold; -fx-text-fill: #64748B; -fx-min-width: 18px;");
                    Label name = new Label(item.getName());
                    name.setStyle("-fx-font-weight: bold; -fx-text-fill: #0F172A;");
                    Label type = new Label("[" + item.getStageType() + "]");
                    type.setStyle("-fx-font-size: 11px; -fx-text-fill: #0284C7;");
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
        depTitle.setStyle("-fx-font-weight: bold; -fx-font-size: 12px; -fx-text-fill: #475569;");
        dependenciesBox.getChildren().add(depTitle);

        stageConfigBox.getChildren().addAll(
                configTitle,
                new Label("Stage Name:"), stageNameField, stageNameError,
                new Label("Stage Type:"), stageTypeCombo,
                new Label("Shell / Subprocess Command:"), stageCmdField, stageCmdError,
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
        depTitle.setStyle("-fx-font-weight: bold; -fx-font-size: 12px; -fx-text-fill: #475569;");
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
            noOther.setStyle("-fx-font-size: 11px; -fx-text-fill: #94A3B8;");
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
        validateBtn.setGraphic(new SvgIcon(SvgIcon.IconName.CHECK_CIRCLE, 13, Color.web("#059669")));
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
            stageListView.getSelectionModel().select(0);
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
                "echo \"Running task step...\"", 180, true, null);
        stageList.add(stage);
        stageListView.getSelectionModel().select(stage);
        refreshVisualFlow();
    }

    private void removeSelectedStage() {
        int idx = stageListView.getSelectionModel().getSelectedIndex();
        if (idx >= 0) {
            stageList.remove(idx);
            if (!stageList.isEmpty()) {
                stageListView.getSelectionModel().select(Math.min(idx, stageList.size() - 1));
            }
            refreshVisualFlow();
        }
    }

    private void moveStage(int delta) {
        int idx = stageListView.getSelectionModel().getSelectedIndex();
        int newIdx = idx + delta;
        if (idx >= 0 && newIdx >= 0 && newIdx < stageList.size()) {
            Stage item = stageList.remove(idx);
            stageList.add(newIdx, item);
            stageListView.getSelectionModel().select(newIdx);
            for (int k = 0; k < stageList.size(); k++) {
                stageList.get(k).setStageOrder(k + 1);
            }
            refreshVisualFlow();
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
