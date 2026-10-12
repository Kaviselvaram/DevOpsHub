package com.devopshub.ui.view;

import com.devopshub.model.Execution;
import com.devopshub.model.ExecutionLog;
import com.devopshub.repository.ExecutionRepository;
import com.devopshub.repository.LogRepository;
import com.devopshub.ui.component.SvgIcon;
import com.devopshub.ui.component.ToastNotification;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;

import java.util.List;

/**
 * Dedicated Full-Screen Log Viewer: Stage filters, keyword search, error token highlighting.
 */
public class LogViewerView extends VBox {

    private final MainLayoutView mainLayout;
    private final LogRepository logRepository;
    private final ExecutionRepository executionRepository;

    private ComboBox<Execution> executionCombo;
    private TextField searchField;
    private ComboBox<String> levelFilter;
    private TextArea logArea;

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

        Label execLabel = new Label("Build Run:");
        execLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #475569;");

        executionCombo = new ComboBox<>();
        executionCombo.setPrefWidth(220);
        executionCombo.setOnAction(e -> reloadLogs());

        Label searchLabel = new Label("Search:");
        searchLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #475569;");

        searchField = new TextField();
        searchField.setPromptText("Filter keywords (e.g. 401, error)...");
        searchField.setPrefWidth(240);
        searchField.textProperty().addListener((o, old, val) -> reloadLogs());

        Label levelLabel = new Label("Level:");
        levelLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #475569;");

        levelFilter = new ComboBox<>();
        levelFilter.getItems().addAll("ALL", "INFO", "ERROR", "WARN");
        levelFilter.setValue("ALL");
        levelFilter.setOnAction(e -> reloadLogs());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button copyBtn = new Button("Copy Output");
        copyBtn.getStyleClass().add("btn-secondary");
        copyBtn.setGraphic(new SvgIcon(SvgIcon.IconName.COPY, 12, Color.web("#64748B")));
        copyBtn.setOnAction(e -> {
            javafx.scene.input.Clipboard clipboard = javafx.scene.input.Clipboard.getSystemClipboard();
            javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent();
            content.putString(logArea.getText());
            clipboard.setContent(content);
            mainLayout.showToast("Logs copied to clipboard", ToastNotification.ToastType.SUCCESS);
        });

        toolbar.getChildren().addAll(execLabel, executionCombo, searchLabel, searchField, levelLabel, levelFilter, spacer, copyBtn);
        this.getChildren().add(toolbar);
    }

    private void buildLogConsole() {
        VBox card = new VBox();
        card.getStyleClass().add("card");
        VBox.setVgrow(card, Priority.ALWAYS);

        logArea = new TextArea();
        logArea.setEditable(false);
        logArea.setWrapText(false);
        logArea.setPrefRowCount(28);
        logArea.setStyle("-fx-font-family: Menlo, 'JetBrains Mono', monospace; -fx-font-size: 12px; " +
                "-fx-control-inner-background: #0F172A; -fx-text-fill: #E2E8F0;");

        card.getChildren().add(logArea);
        this.getChildren().add(card);
    }

    private void loadExecutions() {
        List<Execution> runs = executionRepository.findRecent(30);
        executionCombo.getItems().setAll(runs);
        if (!runs.isEmpty()) {
            executionCombo.setValue(runs.get(0));
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

        StringBuilder sb = new StringBuilder();
        for (ExecutionLog l : logs) {
            if (!"ALL".equals(lvl) && !l.getLogLevel().name().equalsIgnoreCase(lvl)) {
                continue;
            }
            if (!q.isEmpty() && !l.getMessage().toLowerCase().contains(q)) {
                continue;
            }
            sb.append(l.getMessage()).append("\n");
        }

        logArea.setText(sb.toString());
        logArea.positionCaret(0);
    }
}
