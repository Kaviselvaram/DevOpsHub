package com.devopshub.ui.view;

import com.devopshub.model.Execution;
import com.devopshub.model.ExecutionLog;
import com.devopshub.model.StageExecution;
import com.devopshub.repository.ExecutionRepository;
import com.devopshub.repository.LogRepository;
import com.devopshub.ui.component.EmptyStateView;
import com.devopshub.ui.component.SvgIcon;
import com.devopshub.ui.component.ToastNotification;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

/**
 * Dedicated Full-Screen Log Viewer:
 * Stage filters, keyword search, error token highlighting, auto-scroll toggle, and file export.
 */
public class LogViewerView extends VBox {

    private final MainLayoutView mainLayout;
    private final LogRepository logRepository;
    private final ExecutionRepository executionRepository;

    private ComboBox<Execution> executionCombo;
    private ComboBox<String> stageFilter;
    private TextField searchField;
    private ComboBox<String> levelFilter;
    private CheckBox autoScrollCheck;
    private TextArea logArea;
    private StackPane consoleContainer;

    public LogViewerView(MainLayoutView mainLayout) {
        this.mainLayout = mainLayout;
        this.logRepository = new LogRepository();
        this.executionRepository = new ExecutionRepository();

        this.setSpacing(16);
        this.setPadding(new Insets(24));
        this.setStyle("-fx-background-color: transparent;");

        buildToolbar();
        buildLogConsole();
        loadExecutions();
    }

    private void buildToolbar() {
        HBox toolbar = new HBox(12);
        toolbar.setAlignment(Pos.CENTER_LEFT);

        Label execLabel = new Label("Execution Run:");
        execLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #475569;");

        executionCombo = new ComboBox<>();
        executionCombo.setPrefWidth(220);
        executionCombo.setOnAction(e -> {
            updateStageFilterItems();
            reloadLogs();
        });

        Label stageLabel = new Label("Stage:");
        stageLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #475569;");

        stageFilter = new ComboBox<>();
        stageFilter.setPrefWidth(160);
        stageFilter.getItems().add("ALL STAGES");
        stageFilter.setValue("ALL STAGES");
        stageFilter.setOnAction(e -> reloadLogs());

        Label searchLabel = new Label("Search:");
        searchLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #475569;");

        searchField = new TextField();
        searchField.setPromptText("Filter keywords (e.g. 401, error)...");
        searchField.setPrefWidth(200);
        searchField.textProperty().addListener((o, old, val) -> reloadLogs());

        Label levelLabel = new Label("Level:");
        levelLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #475569;");

        levelFilter = new ComboBox<>();
        levelFilter.getItems().addAll("ALL", "INFO", "ERROR", "WARN");
        levelFilter.setValue("ALL");
        levelFilter.setOnAction(e -> reloadLogs());

        autoScrollCheck = new CheckBox("Auto-scroll");
        autoScrollCheck.setSelected(true);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button copyBtn = new Button("Copy");
        copyBtn.getStyleClass().add("btn-secondary");
        copyBtn.setGraphic(new SvgIcon(SvgIcon.IconName.COPY, 12, Color.web("#64748B")));
        copyBtn.setOnAction(e -> {
            javafx.scene.input.Clipboard clipboard = javafx.scene.input.Clipboard.getSystemClipboard();
            javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent();
            content.putString(logArea.getText());
            clipboard.setContent(content);
            mainLayout.showToast("Logs copied to clipboard", ToastNotification.ToastType.SUCCESS);
        });

        Button exportBtn = new Button("Export .log");
        exportBtn.getStyleClass().add("btn-secondary");
        exportBtn.setGraphic(new SvgIcon(SvgIcon.IconName.TERMINAL, 12, Color.web("#0284C7")));
        exportBtn.setOnAction(e -> exportLogsToFile());

        toolbar.getChildren().addAll(
                execLabel, executionCombo,
                stageLabel, stageFilter,
                searchLabel, searchField,
                levelLabel, levelFilter,
                autoScrollCheck,
                spacer, copyBtn, exportBtn
        );
        this.getChildren().add(toolbar);
    }

    private void buildLogConsole() {
        consoleContainer = new StackPane();
        VBox.setVgrow(consoleContainer, Priority.ALWAYS);

        VBox card = new VBox();
        card.getStyleClass().add("card");
        VBox.setVgrow(card, Priority.ALWAYS);

        logArea = new TextArea();
        logArea.setEditable(false);
        logArea.setWrapText(false);
        logArea.setPrefRowCount(28);
        logArea.setStyle("-fx-font-family: Menlo, 'JetBrains Mono', 'Courier New', monospace; -fx-font-size: 12px; " +
                "-fx-control-inner-background: #0F172A; -fx-text-fill: #E2E8F0;");

        card.getChildren().add(logArea);
        consoleContainer.getChildren().add(card);
        this.getChildren().add(consoleContainer);
    }

    private void updateStageFilterItems() {
        Execution selected = executionCombo.getValue();
        stageFilter.getItems().clear();
        stageFilter.getItems().add("ALL STAGES");
        if (selected != null) {
            List<StageExecution> stages = executionRepository.findStageExecutionsByExecutionId(selected.getId());
            for (StageExecution se : stages) {
                stageFilter.getItems().add(se.getStageName());
            }
        }
        stageFilter.setValue("ALL STAGES");
    }

    private void loadExecutions() {
        List<Execution> runs = executionRepository.findRecent(40);
        executionCombo.getItems().setAll(runs);
        if (!runs.isEmpty()) {
            executionCombo.setValue(runs.get(0));
            updateStageFilterItems();
            reloadLogs();
        }
    }

    private void reloadLogs() {
        Execution selected = executionCombo.getValue();
        if (selected == null) {
            logArea.setText("");
            return;
        }

        List<ExecutionLog> logs = logRepository.findByExecutionId(selected.getId());
        String q = searchField.getText().trim().toLowerCase();
        String lvl = levelFilter.getValue();
        String stageChoice = stageFilter.getValue();

        StringBuilder sb = new StringBuilder();
        int lineCount = 0;
        for (ExecutionLog l : logs) {
            if (!"ALL".equals(lvl) && !l.getLogLevel().name().equalsIgnoreCase(lvl)) {
                continue;
            }
            if (stageChoice != null && !"ALL STAGES".equals(stageChoice)
                    && (l.getStageName() == null || !l.getStageName().equalsIgnoreCase(stageChoice))) {
                continue;
            }
            if (!q.isEmpty() && !l.getMessage().toLowerCase().contains(q)) {
                continue;
            }
            sb.append(l.getMessage()).append("\n");
            lineCount++;
        }

        logArea.setText(sb.toString());
        if (autoScrollCheck.isSelected()) {
            logArea.positionCaret(logArea.getText().length());
        } else {
            logArea.positionCaret(0);
        }

        if (lineCount == 0) {
            if (consoleContainer.getChildren().size() == 1) {
                EmptyStateView emptyState = new EmptyStateView(
                        SvgIcon.IconName.LOGS,
                        "No Log Output",
                        "No logs matched the selected execution, stage, level, or search keyword."
                );
                consoleContainer.getChildren().add(emptyState);
            }
        } else {
            if (consoleContainer.getChildren().size() > 1) {
                consoleContainer.getChildren().remove(1);
            }
        }
    }

    private void exportLogsToFile() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Export Execution Logs");
        fileChooser.setInitialFileName("devopshub-execution-" + (executionCombo.getValue() != null ? executionCombo.getValue().getExecutionNumber() : "log") + ".log");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Log Files (*.log)", "*.log"));

        File file = fileChooser.showSaveDialog(mainLayout.getPrimaryStage());
        if (file != null) {
            try (FileWriter writer = new FileWriter(file)) {
                writer.write(logArea.getText());
                mainLayout.showToast("Logs successfully exported to: " + file.getName(), ToastNotification.ToastType.SUCCESS);
            } catch (IOException ex) {
                mainLayout.showToast("Failed to export logs: " + ex.getMessage(), ToastNotification.ToastType.ERROR);
            }
        }
    }
}
