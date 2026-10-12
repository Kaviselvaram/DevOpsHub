package com.devopshub.ui.view;

import com.devopshub.model.Pipeline;
import com.devopshub.model.Project;
import com.devopshub.model.Stage;
import com.devopshub.model.StageType;
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

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Pipeline Builder View: Visual stage composition, dependency configuration, and DAG validation.
 */
public class PipelineBuilderView extends VBox {

    private final MainLayoutView mainLayout;
    private final PipelineService pipelineService;
    private final ProjectService projectService;
    private final Pipeline existingPipeline;
    private final PipelineValidator validator = new PipelineValidator();

    private TextField nameField;
    private ComboBox<Project> projectCombo;
    private ComboBox<String> modeCombo;
    private TextArea descField;

    private final ObservableList<Stage> stageList = FXCollections.observableArrayList();
    private ListView<Stage> stageListView;
    private VBox stageConfigBox;
    private Stage selectedStage;

    // Stage Form Controls
    private TextField stageNameField;
    private ComboBox<StageType> stageTypeCombo;
    private TextArea stageCmdField;
    private TextField stageTimeoutField;
    private CheckBox stageEnabledCheck;
    private TextField stageParallelGroupField;

    public PipelineBuilderView(MainLayoutView mainLayout, PipelineService pipelineService,
                               ProjectService projectService, Pipeline existingPipeline) {
        this.mainLayout = mainLayout;
        this.pipelineService = pipelineService;
        this.projectService = projectService;
        this.existingPipeline = existingPipeline;

        this.setSpacing(20);
        this.setPadding(new Insets(24));
        this.setStyle("-fx-background-color: transparent;");

        buildHeaderForm();
        buildStageComposer();
        buildActionButtons();

        loadData();
    }

    private void buildHeaderForm() {
        VBox card = new VBox(12);
        card.getStyleClass().add("card");

        Label sectionTitle = new Label("Pipeline Configuration");
        sectionTitle.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #0F172A;");

        GridPane grid = new GridPane();
        grid.setHgap(16);
        grid.setVgap(12);

        nameField = new TextField();
        nameField.setPromptText("Pipeline Name (e.g. Master-Release-CI)");

        projectCombo = new ComboBox<>();
        projectCombo.setPromptText("Select Associated Project");

        modeCombo = new ComboBox<>();
        modeCombo.getItems().addAll("SEQUENTIAL", "PARALLEL_FORK");
        modeCombo.setValue("SEQUENTIAL");

        descField = new TextArea();
        descField.setPromptText("Pipeline Description and Objectives");
        descField.setPrefRowCount(2);

        grid.add(new Label("Pipeline Name:"), 0, 0);
        grid.add(nameField, 1, 0);
        grid.add(new Label("Project:"), 2, 0);
        grid.add(projectCombo, 3, 0);

        grid.add(new Label("Execution Mode:"), 0, 1);
        grid.add(modeCombo, 1, 1);
        grid.add(new Label("Description:"), 2, 1);
        grid.add(descField, 3, 1);

        card.getChildren().addAll(sectionTitle, grid);
        this.getChildren().add(card);
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
        Label stagesTitle = new Label("Stages Sequence");
        stagesTitle.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #0F172A;");
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
        stageConfigBox = new VBox(12);
        stageConfigBox.getStyleClass().add("card");
        HBox.setHgrow(stageConfigBox, Priority.ALWAYS);

        Label configTitle = new Label("Stage Configuration & Execution Command");
        configTitle.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #0F172A;");

        stageNameField = new TextField();
        stageNameField.textProperty().addListener((o, old, val) -> {
            if (selectedStage != null) {
                selectedStage.setName(val);
                stageListView.refresh();
            }
        });

        stageTypeCombo = new ComboBox<>();
        stageTypeCombo.getItems().addAll(StageType.values());
        stageTypeCombo.valueProperty().addListener((o, old, val) -> {
            if (selectedStage != null && val != null) {
                selectedStage.setStageType(val);
                stageListView.refresh();
            }
        });

        stageCmdField = new TextArea();
        stageCmdField.setPrefRowCount(4);
        stageCmdField.setStyle("-fx-font-family: Menlo, monospace; -fx-font-size: 12px;");
        stageCmdField.textProperty().addListener((o, old, val) -> {
            if (selectedStage != null) selectedStage.setCommand(val);
        });

        stageTimeoutField = new TextField();
        stageTimeoutField.textProperty().addListener((o, old, val) -> {
            if (selectedStage != null) {
                try { selectedStage.setTimeoutSeconds(Integer.parseInt(val)); } catch (Exception ignored) {}
            }
        });

        stageEnabledCheck = new CheckBox("Stage Enabled for Execution");
        stageEnabledCheck.selectedProperty().addListener((o, old, val) -> {
            if (selectedStage != null) selectedStage.setEnabled(val);
        });

        stageParallelGroupField = new TextField();
        stageParallelGroupField.setPromptText("Parallel Group Tag (e.g. FORK_TEST)");
        stageParallelGroupField.textProperty().addListener((o, old, val) -> {
            if (selectedStage != null) selectedStage.setParallelGroup(val);
        });

        stageConfigBox.getChildren().addAll(
                configTitle,
                new Label("Stage Name:"), stageNameField,
                new Label("Stage Type:"), stageTypeCombo,
                new Label("Shell Command to Execute:"), stageCmdField,
                new Label("Timeout (seconds):"), stageTimeoutField,
                new Label("Parallel Execution Group:"), stageParallelGroupField,
                stageEnabledCheck
        );

        composer.getChildren().addAll(leftBox, stageConfigBox);
        this.getChildren().add(composer);
    }

    private void buildActionButtons() {
        HBox actions = new HBox(12);
        actions.setAlignment(Pos.CENTER_RIGHT);

        Button cancelBtn = new Button("Cancel");
        cancelBtn.getStyleClass().add("btn-secondary");
        cancelBtn.setOnAction(e -> mainLayout.navigateToPipelines());

        Button validateBtn = new Button("Validate Pipeline");
        validateBtn.getStyleClass().add("btn-secondary");
        validateBtn.setGraphic(new SvgIcon(SvgIcon.IconName.CHECK_CIRCLE, 13, Color.web("#059669")));
        validateBtn.setOnAction(e -> validateCurrentPipeline());

        Button saveBtn = new Button("Save Pipeline");
        saveBtn.getStyleClass().add("btn-primary");
        saveBtn.setOnAction(e -> savePipeline());

        actions.getChildren().addAll(cancelBtn, validateBtn, saveBtn);
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
    }

    private void addNewStage() {
        Stage stage = new Stage(UUID.randomUUID().toString(),
                existingPipeline != null ? existingPipeline.getId() : UUID.randomUUID().toString(),
                "New Stage", StageType.BUILD, stageList.size() + 1,
                "echo \"Running custom task...\"", 180, true, null);
        stageList.add(stage);
        stageListView.getSelectionModel().select(stage);
    }

    private void removeSelectedStage() {
        int idx = stageListView.getSelectionModel().getSelectedIndex();
        if (idx >= 0) {
            stageList.remove(idx);
            if (!stageList.isEmpty()) {
                stageListView.getSelectionModel().select(Math.min(idx, stageList.size() - 1));
            }
        }
    }

    private void moveStage(int delta) {
        int idx = stageListView.getSelectionModel().getSelectedIndex();
        int newIdx = idx + delta;
        if (idx >= 0 && newIdx >= 0 && newIdx < stageList.size()) {
            Stage item = stageList.remove(idx);
            stageList.add(newIdx, item);
            stageListView.getSelectionModel().select(newIdx);
            // Re-index stageOrder
            for (int k = 0; k < stageList.size(); k++) {
                stageList.get(k).setStageOrder(k + 1);
            }
        }
    }

    private boolean validateCurrentPipeline() {
        try {
            Pipeline temp = buildPipelineModel();
            validator.validate(temp);
            mainLayout.showToast("Pipeline DAG is valid and ready for execution!", ToastNotification.ToastType.SUCCESS);
            return true;
        } catch (Exception ex) {
            mainLayout.showToast("Validation Error: " + ex.getMessage(), ToastNotification.ToastType.ERROR);
            return false;
        }
    }

    private Pipeline buildPipelineModel() {
        Project proj = projectCombo.getValue();
        if (proj == null) {
            throw new IllegalArgumentException("Please select an associated project.");
        }
        Pipeline p = new Pipeline();
        p.setId(existingPipeline != null ? existingPipeline.getId() : UUID.randomUUID().toString());
        p.setProjectId(proj.getId());
        p.setName(nameField.getText());
        p.setDescription(descField.getText());
        p.setExecutionMode(modeCombo.getValue());
        p.setStages(new ArrayList<>(stageList));
        return p;
    }

    private void savePipeline() {
        if (!validateCurrentPipeline()) {
            return;
        }
        try {
            Pipeline model = buildPipelineModel();
            if (existingPipeline == null) {
                pipelineService.createPipeline(model.getProjectId(), model.getName(),
                        model.getDescription(), model.getExecutionMode(), model.getStages());
                mainLayout.showToast("Pipeline created successfully!", ToastNotification.ToastType.SUCCESS);
            } else {
                pipelineService.updatePipeline(model.getId(), model.getName(), model.getDescription(),
                        model.getExecutionMode(), true, model.getStages());
                mainLayout.showToast("Pipeline updated successfully!", ToastNotification.ToastType.SUCCESS);
            }
            mainLayout.navigateToPipelines();
        } catch (Exception ex) {
            Alert alert = new Alert(Alert.AlertType.ERROR, ex.getMessage(), ButtonType.OK);
            alert.showAndWait();
        }
    }
}
