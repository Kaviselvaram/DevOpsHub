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
        VBox toolbarContainer = new VBox(10);
        toolbarContainer.setStyle("-fx-background-color: #141622; -fx-padding: 12px 16px; -fx-background-radius: 8px; -fx-border-color: #222436;");

        // Row 1: Target execution selector, stage filter, log level, auto-scroll, and export actions
        HBox row1 = new HBox(12);
        row1.setAlignment(Pos.CENTER_LEFT);

        Label execLabel = new Label("Execution Run:");
        execLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #E2E8F0;");
        execLabel.setMinWidth(Region.USE_PREF_SIZE);

        executionCombo = new ComboBox<>();
        executionCombo.setPrefWidth(220);
        executionCombo.setMinWidth(180);
        executionCombo.setOnAction(e -> {
            updateStageFilterItems();
            reloadLogs();
        });

        Label stageLabel = new Label("Stage:");
        stageLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #E2E8F0;");
        stageLabel.setMinWidth(Region.USE_PREF_SIZE);

        stageFilter = new ComboBox<>();
        stageFilter.setPrefWidth(160);
        stageFilter.setMinWidth(140);
        stageFilter.getItems().add("ALL STAGES");
        stageFilter.setValue("ALL STAGES");
        stageFilter.setOnAction(e -> reloadLogs());

        Label levelLabel = new Label("Level:");
        levelLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #E2E8F0;");
        levelLabel.setMinWidth(Region.USE_PREF_SIZE);

        levelFilter = new ComboBox<>();
        levelFilter.getItems().addAll("ALL", "INFO", "ERROR", "WARN");
        levelFilter.setValue("ALL");
        levelFilter.setMinWidth(90);
        levelFilter.setOnAction(e -> reloadLogs());

        autoScrollCheck = new CheckBox("Auto-scroll");
        autoScrollCheck.setSelected(true);
        autoScrollCheck.setMinWidth(Region.USE_PREF_SIZE);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button copyBtn = new Button("Copy");
        copyBtn.getStyleClass().add("btn-secondary");
        copyBtn.setGraphic(new SvgIcon(SvgIcon.IconName.COPY, 12, Color.web("#64748B")));
        copyBtn.setMinWidth(Region.USE_PREF_SIZE);
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
        exportBtn.setMinWidth(Region.USE_PREF_SIZE);
        exportBtn.setOnAction(e -> exportLogsToFile());

        row1.getChildren().addAll(
                execLabel, executionCombo,
                stageLabel, stageFilter,
                levelLabel, levelFilter,
                autoScrollCheck,
                spacer, copyBtn, exportBtn
        );

        // Row 2: Full-width keyword search bar
        HBox row2 = new HBox(12);
        row2.setAlignment(Pos.CENTER_LEFT);

        Label searchLabel = new Label("Search Filter:");
        searchLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #E2E8F0;");
        searchLabel.setMinWidth(Region.USE_PREF_SIZE);

        searchField = new TextField();
        searchField.setPromptText("Filter keywords across standard output and standard error (e.g. 401, error, BUILD FAILURE)...");
        HBox.setHgrow(searchField, Priority.ALWAYS);
        searchField.textProperty().addListener((o, old, val) -> reloadLogs());

        row2.getChildren().addAll(searchLabel, searchField);

        toolbarContainer.getChildren().addAll(row1, row2);
        this.getChildren().add(toolbarContainer);
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

    public void setSearchQuery(String query) {
        if (searchField != null) {
            searchField.setText(query != null ? query : "");
        }
    }
}
