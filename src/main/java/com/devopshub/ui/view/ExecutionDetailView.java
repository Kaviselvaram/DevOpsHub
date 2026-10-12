package com.devopshub.ui.view;

import com.devopshub.execution.ExecutionEngine;
import com.devopshub.execution.LogCollector;
import com.devopshub.model.*;
import com.devopshub.repository.DiagnosisRepository;
import com.devopshub.repository.ExecutionRepository;
import com.devopshub.repository.LogRepository;
import com.devopshub.ui.component.StatusBadge;
import com.devopshub.ui.component.SvgIcon;
import com.devopshub.ui.component.ToastNotification;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;

import java.util.List;
import java.util.Optional;

/**
 * Execution Details View matching Slide 8 & 12 specifications.
 * Stage visualizer, real-time log output, and intelligent rule-based failure diagnosis.
 */
public class ExecutionDetailView extends VBox {

    private final MainLayoutView mainLayout;
    private final String executionId;
    private final ExecutionEngine executionEngine;
    private final ExecutionRepository executionRepository;
    private final LogRepository logRepository;
    private final DiagnosisRepository diagnosisRepository;

    private Execution currentExecution;
    private Label buildTitleLabel;
    private Label buildMetaLabel;
    private HBox badgeContainer;
    private Button cancelBtn;

    private HBox stageCardsContainer;
    private VBox diagnosisPanel;
    private TextArea terminalArea;

    public ExecutionDetailView(MainLayoutView mainLayout, String executionId, ExecutionEngine executionEngine) {
        this.mainLayout = mainLayout;
        this.executionId = executionId;
        this.executionEngine = executionEngine;
        this.executionRepository = new ExecutionRepository();
        this.logRepository = new LogRepository();
        this.diagnosisRepository = new DiagnosisRepository();

        this.setSpacing(20);
        this.setPadding(new Insets(24));
        this.setStyle("-fx-background-color: transparent;");

        buildHeaderCard();
        buildStagePipelineSection();
        buildDiagnosisSection();
        buildLogConsoleSection();

        loadExecutionData();
        setupLiveListener();
    }

    private void buildHeaderCard() {
        HBox headerCard = new HBox(16);
        headerCard.getStyleClass().add("card");
        headerCard.setAlignment(Pos.CENTER_LEFT);

        VBox infoBox = new VBox(4);
        buildTitleLabel = new Label("BUILD #... / LOADING");
        buildTitleLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: #0F172A;");

        buildMetaLabel = new Label("Pipeline: Loading | Project: Loading");
        buildMetaLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #64748B;");

        infoBox.getChildren().addAll(buildTitleLabel, buildMetaLabel);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        badgeContainer = new HBox(10);
        badgeContainer.setAlignment(Pos.CENTER_RIGHT);

        cancelBtn = new Button("Cancel Run");
        cancelBtn.getStyleClass().add("btn-danger");
        cancelBtn.setGraphic(new SvgIcon(SvgIcon.IconName.STOP, 12, Color.WHITE));
        cancelBtn.setVisible(false);
        cancelBtn.setOnAction(e -> {
            boolean cancelled = executionEngine.cancelExecution(executionId);
            if (cancelled) {
                mainLayout.showToast("Cancellation signal sent", ToastNotification.ToastType.INFO);
            }
        });

        Button refreshBtn = new Button("Refresh");
        refreshBtn.getStyleClass().add("btn-secondary");
        refreshBtn.setGraphic(new SvgIcon(SvgIcon.IconName.REFRESH, 12, Color.web("#64748B")));
        refreshBtn.setOnAction(e -> loadExecutionData());

        badgeContainer.getChildren().addAll(cancelBtn, refreshBtn);
        headerCard.getChildren().addAll(infoBox, spacer, badgeContainer);
        this.getChildren().add(headerCard);
    }

    private void buildStagePipelineSection() {
        VBox section = new VBox(10);
        Label sectionTitle = new Label("Pipeline Execution Stages");
        sectionTitle.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #0F172A;");

        ScrollPane scroll = new ScrollPane();
        scroll.setFitToHeight(true);
        scroll.setStyle("-fx-background-color: transparent; -fx-background: transparent; -fx-padding: 0;");

        stageCardsContainer = new HBox(14);
        stageCardsContainer.setAlignment(Pos.CENTER_LEFT);
        stageCardsContainer.setPadding(new Insets(4, 4, 8, 4));

        scroll.setContent(stageCardsContainer);
        section.getChildren().addAll(sectionTitle, scroll);
        this.getChildren().add(section);
    }

    private void buildDiagnosisSection() {
        diagnosisPanel = new VBox(12);
        diagnosisPanel.getStyleClass().add("intelligence-card");
        diagnosisPanel.setVisible(false);
        diagnosisPanel.setManaged(false);
        this.getChildren().add(diagnosisPanel);
    }

    private void buildLogConsoleSection() {
        VBox consoleCard = new VBox(8);
        consoleCard.getStyleClass().add("card");

        HBox consoleHeader = new HBox(10);
        consoleHeader.setAlignment(Pos.CENTER_LEFT);

        SvgIcon termIcon = new SvgIcon(SvgIcon.IconName.TERMINAL, 16, Color.web("#0284C7"));
        Label termTitle = new Label("Execution Output Log (Standard Out / Standard Error)");
        termTitle.setStyle("-fx-font-weight: bold; -fx-font-size: 13px; -fx-text-fill: #0F172A;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button copyLogsBtn = new Button("Copy Logs");
        copyLogsBtn.getStyleClass().add("btn-secondary");
        copyLogsBtn.setGraphic(new SvgIcon(SvgIcon.IconName.COPY, 12, Color.web("#64748B")));
        copyLogsBtn.setOnAction(e -> {
            javafx.scene.input.Clipboard clipboard = javafx.scene.input.Clipboard.getSystemClipboard();
            javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent();
            content.putString(terminalArea.getText());
            clipboard.setContent(content);
            mainLayout.showToast("Logs copied to clipboard", ToastNotification.ToastType.SUCCESS);
        });

        consoleHeader.getChildren().addAll(termIcon, termTitle, spacer, copyLogsBtn);

        terminalArea = new TextArea();
        terminalArea.setEditable(false);
        terminalArea.setWrapText(false);
        terminalArea.setPrefRowCount(14);
        terminalArea.setStyle("-fx-font-family: Menlo, 'JetBrains Mono', monospace; -fx-font-size: 11px; " +
                "-fx-control-inner-background: #0F172A; -fx-text-fill: #E2E8F0;");

        consoleCard.getChildren().addAll(consoleHeader, terminalArea);
        this.getChildren().add(consoleCard);
    }

    private void loadExecutionData() {
        Optional<Execution> opt = executionRepository.findById(executionId);
        if (opt.isEmpty()) return;

        currentExecution = opt.get();

        buildTitleLabel.setText(String.format("BUILD #%d  /  %s  /  %.1fS",
                currentExecution.getExecutionNumber(),
                currentExecution.getStatus(),
                currentExecution.getDurationSeconds()));

        buildMetaLabel.setText(String.format("Pipeline: %s | Project: %s | Trigger: %s",
                currentExecution.getPipelineName(),
                currentExecution.getProjectName(),
                currentExecution.getTriggerType()));

        cancelBtn.setVisible(currentExecution.getStatus() == ExecutionStatus.RUNNING);

        // Update Header Badge
        if (badgeContainer.getChildren().size() > 2) {
            badgeContainer.getChildren().remove(0);
        }
        badgeContainer.getChildren().add(0, StatusBadge.of(currentExecution.getStatus()));

        // Populate Stage Cards
        stageCardsContainer.getChildren().clear();
        List<StageExecution> stages = currentExecution.getStageExecutions();
        for (int i = 0; i < stages.size(); i++) {
            StageExecution se = stages.get(i);
            VBox card = createStageCard(se, i + 1);
            stageCardsContainer.getChildren().add(card);

            if (i < stages.size() - 1) {
                SvgIcon chevron = new SvgIcon(SvgIcon.IconName.CHEVRON_RIGHT, 14, Color.web("#CBD5E1"));
                stageCardsContainer.getChildren().add(chevron);
            }
        }

        // Populate Diagnosis if Failed
        Optional<FailureDiagnosis> diagOpt = diagnosisRepository.findByExecutionId(executionId);
        if (diagOpt.isPresent() || currentExecution.getStatus() == ExecutionStatus.FAILED) {
            renderDiagnosis(diagOpt.orElse(null));
        } else {
            diagnosisPanel.setVisible(false);
            diagnosisPanel.setManaged(false);
        }

        // Populate Logs
        List<ExecutionLog> logs = logRepository.findByExecutionId(executionId);
        StringBuilder sb = new StringBuilder();
        for (ExecutionLog l : logs) {
            sb.append(l.getMessage()).append("\n");
        }
        terminalArea.setText(sb.toString());
        terminalArea.positionCaret(terminalArea.getText().length());
    }

    private VBox createStageCard(StageExecution se, int stepNum) {
        VBox card = new VBox(6);
        card.getStyleClass().add("stage-card");
        card.setPrefWidth(160);

        if (se.getStatus() == StageExecutionStatus.SUCCESS) {
            card.getStyleClass().add("stage-card-success");
        } else if (se.getStatus() == StageExecutionStatus.FAILED) {
            card.getStyleClass().add("stage-card-failed");
        } else if (se.getStatus() == StageExecutionStatus.RUNNING) {
            card.getStyleClass().add("stage-card-running");
        }

        Label stepLabel = new Label("STAGE " + stepNum);
        stepLabel.setStyle("-fx-font-size: 10px; -fx-font-weight: bold; -fx-text-fill: #64748B;");

        Label nameLabel = new Label(se.getStageName());
        nameLabel.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #0F172A;");
        nameLabel.setWrapText(true);

        Label durationLabel = new Label(se.getDurationSeconds() > 0 ? se.getDurationSeconds() + "s" : "-");
        durationLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #64748B;");

        StatusBadge badge = StatusBadge.of(se.getStatus());

        card.getChildren().addAll(stepLabel, nameLabel, durationLabel, badge);
        return card;
    }

    private void renderDiagnosis(FailureDiagnosis d) {
        diagnosisPanel.getChildren().clear();
        diagnosisPanel.setVisible(true);
        diagnosisPanel.setManaged(true);

        HBox diagHeader = new HBox(10);
        diagHeader.setAlignment(Pos.CENTER_LEFT);

        SvgIcon bulbIcon = new SvgIcon(SvgIcon.IconName.LIGHTBULB, 18, Color.web("#E11D48"));
        Label headerTitle = new Label("INTELLIGENT FAILURE DIAGNOSIS (RULE MATCH)");
        headerTitle.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #E11D48;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label ruleIdTag = new Label(d != null ? "Rule: " + d.getRuleId() : "Rule: DEFAULT");
        ruleIdTag.setStyle("-fx-font-size: 11px; -fx-text-fill: #64748B; -fx-background-color: #F1F5F9; " +
                "-fx-padding: 2px 6px; -fx-background-radius: 4px;");

        diagHeader.getChildren().addAll(bulbIcon, headerTitle, spacer, ruleIdTag);

        Label titleLabel = new Label(d != null ? d.getTitle() : "Stage Execution Failure");
        titleLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #0F172A;");

        Label likelyCause = new Label(d != null ? d.getLikelyCause() : "Non-zero exit code encountered during stage execution.");
        likelyCause.setStyle("-fx-font-size: 13px; -fx-text-fill: #334155;");
        likelyCause.setWrapText(true);

        // Evidence Box (Slide 8 style)
        VBox evidenceBox = new VBox(4);
        evidenceBox.setStyle("-fx-background-color: #0F172A; -fx-background-radius: 6px; -fx-padding: 10px;");
        Label evidenceTitle = new Label("SUPPORTING EVIDENCE FROM EXECUTION LOGS:");
        evidenceTitle.setStyle("-fx-font-size: 10px; -fx-font-weight: bold; -fx-text-fill: #94A3B8;");
        Label evidenceText = new Label(d != null ? d.getEvidenceExcerpt() : "No evidence excerpt available.");
        evidenceText.setStyle("-fx-font-family: Menlo, monospace; -fx-font-size: 11px; -fx-text-fill: #F87171;");
        evidenceText.setWrapText(true);
        evidenceBox.getChildren().addAll(evidenceTitle, evidenceText);

        // Suggested Action Box (Slide 8 style)
        VBox actionBox = new VBox(4);
        actionBox.getStyleClass().add("recommendation-box");
        Label actionTitle = new Label("RECOMMENDED RECOVERY ACTION:");
        actionTitle.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #065F46;");
        Label actionText = new Label(d != null ? d.getSuggestedAction() : "Inspect logs and re-run.");
        actionText.setStyle("-fx-font-size: 13px; -fx-text-fill: #047857; -fx-font-weight: bold;");
        actionText.setWrapText(true);
        actionBox.getChildren().addAll(actionTitle, actionText);

        diagnosisPanel.getChildren().addAll(diagHeader, titleLabel, likelyCause, evidenceBox, actionBox);
    }

    private void setupLiveListener() {
        LogCollector activeCollector = executionEngine.getActiveLogCollector(executionId);
        if (activeCollector != null) {
            activeCollector.addListener(log -> Platform.runLater(() -> {
                terminalArea.appendText(log.getMessage() + "\n");
                terminalArea.positionCaret(terminalArea.getText().length());
            }));
        }
    }
}
