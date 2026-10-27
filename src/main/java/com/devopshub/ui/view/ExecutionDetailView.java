package com.devopshub.ui.view;

import com.devopshub.deployment.DeploymentService;
import com.devopshub.execution.ExecutionEngine;
import com.devopshub.execution.LogCollector;
import com.devopshub.execution.event.ExecutionEvent;
import com.devopshub.execution.event.ExecutionEventBus;
import com.devopshub.execution.event.ExecutionEventType;
import com.devopshub.model.*;
import com.devopshub.repository.DiagnosisRepository;
import com.devopshub.repository.ExecutionEventRepository;
import com.devopshub.repository.ExecutionRepository;
import com.devopshub.repository.LogRepository;
import com.devopshub.ui.component.StatusBadge;
import com.devopshub.ui.component.SvgIcon;
import com.devopshub.ui.component.ToastNotification;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.util.Duration;

import java.io.File;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Execution Details View:
 * Real-time pipeline monitoring, live operational transparency, fine-grained event timeline,
 * authentic stage tracker, verified artifact inspection, and evidence-based failure diagnosis.
 */
public class ExecutionDetailView extends VBox {

    private final MainLayoutView mainLayout;
    private final String executionId;
    private final ExecutionEngine executionEngine;
    private final ExecutionRepository executionRepository;
    private final LogRepository logRepository;
    private final DiagnosisRepository diagnosisRepository;
    private final ExecutionEventRepository eventRepository;
    private final ExecutionEventBus eventBus;

    private Execution currentExecution;
    private Label buildTitleLabel;
    private Label buildMetaLabel;
    private Label phaseLabel;
    private Label progressLabel;
    private ProgressBar progressBar;
    private HBox badgeContainer;
    private Button cancelBtn;

    private HBox stageCardsContainer;
    private VBox timelineContainer;
    private Label timelineCountLabel;
    private VBox diagnosisPanel;
    private VBox artifactPanel;
    private TextArea terminalArea;
    private Label logFilterLabel;
    private ComboBox<String> streamFilterCombo;
    private String selectedStageIdFilter = null;

    private Timeline liveTimer;
    private long liveStartEpochMillis = 0;
    private final AtomicBoolean isDisposed = new AtomicBoolean(false);

    private Consumer<Execution> liveUpdateListener;
    private Consumer<ExecutionLog> liveLogListener;
    private Consumer<ExecutionEvent> liveEventListener;
    private LogCollector activeCollectorRef;

    public ExecutionDetailView(MainLayoutView mainLayout, String executionId, ExecutionEngine executionEngine) {
        this.mainLayout = mainLayout;
        this.executionId = executionId;
        this.executionEngine = executionEngine;
        this.executionRepository = new ExecutionRepository();
        this.logRepository = new LogRepository();
        this.diagnosisRepository = new DiagnosisRepository();
        this.eventRepository = new ExecutionEventRepository();
        this.eventBus = ExecutionEventBus.getInstance();

        this.setSpacing(20);
        this.setPadding(new Insets(24));
        this.setStyle("-fx-background-color: transparent;");

        buildBreadcrumb();
        buildHeaderCard();
        buildStagePipelineSection();
        buildTimelineSection();
        buildDiagnosisSection();
        buildArtifactSection();
        buildLogConsoleSection();

        loadExecutionData();
        setupLiveSubscriptions();
    }

    private void buildBreadcrumb() {
        HBox breadcrumb = new HBox(6);
        breadcrumb.getStyleClass().add("breadcrumb-bar");

        Label parentLink = new Label("Executions");
        parentLink.getStyleClass().add("breadcrumb-item");
        parentLink.setOnMouseClicked(e -> mainLayout.navigateToExecutions());

        Label separator = new Label("/");
        separator.getStyleClass().add("breadcrumb-separator");

        Label current = new Label("Build Details: " + executionId.substring(0, Math.min(8, executionId.length())));
        current.getStyleClass().add("breadcrumb-current");

        breadcrumb.getChildren().addAll(parentLink, separator, current);
        this.getChildren().add(breadcrumb);
    }

    private void buildHeaderCard() {
        VBox headerCard = new VBox(12);
        headerCard.getStyleClass().add("card");

        HBox topRow = new HBox(16);
        topRow.setAlignment(Pos.CENTER_LEFT);

        VBox infoBox = new VBox(4);
        buildTitleLabel = new Label("BUILD #... / LOADING");
        buildTitleLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: 800; -fx-text-fill: #FFFFFF;");

        buildMetaLabel = new Label("Pipeline: Loading | Project: Loading");
        buildMetaLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #8A8D9E;");
        buildMetaLabel.setWrapText(true);

        phaseLabel = new Label("Phase: Initializing");
        phaseLabel.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #38BDF8; -fx-background-color: #1E2032; -fx-padding: 2px 6px; -fx-background-radius: 4px;");

        infoBox.getChildren().addAll(buildTitleLabel, buildMetaLabel, phaseLabel);
        HBox.setHgrow(infoBox, Priority.ALWAYS);

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

        Button backBtn = new Button("Back to Runs");
        backBtn.getStyleClass().add("btn-secondary");
        backBtn.setOnAction(e -> mainLayout.navigateToExecutions());

        Button refreshBtn = new Button("Refresh");
        refreshBtn.getStyleClass().add("btn-secondary");
        refreshBtn.setGraphic(new SvgIcon(SvgIcon.IconName.REFRESH, 12, Color.web("#64748B")));
        refreshBtn.setOnAction(e -> {
            loadExecutionData();
            mainLayout.showToast("Execution data refreshed", ToastNotification.ToastType.INFO);
        });

        badgeContainer.getChildren().addAll(cancelBtn, backBtn, refreshBtn);
        topRow.getChildren().addAll(infoBox, spacer, badgeContainer);

        // Stage-level real progress bar (Section 4.2)
        VBox progressBox = new VBox(4);
        HBox progressHeader = new HBox(8);
        progressHeader.setAlignment(Pos.CENTER_LEFT);

        Label progressTitle = new Label("Stage Completion Progress:");
        progressTitle.setStyle("-fx-font-size: 11px; -fx-text-fill: #94A3B8; -fx-font-weight: bold;");

        progressLabel = new Label("0 of 0 stages complete (0%)");
        progressLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #38BDF8; -fx-font-weight: bold;");

        progressHeader.getChildren().addAll(progressTitle, progressLabel);

        progressBar = new ProgressBar(0.0);
        progressBar.setMaxWidth(Double.MAX_VALUE);
        progressBar.setPrefHeight(8);
        progressBar.getStyleClass().add("execution-progress");

        progressBox.getChildren().addAll(progressHeader, progressBar);
        headerCard.getChildren().addAll(topRow, progressBox);

        this.getChildren().add(headerCard);
    }

    private void buildStagePipelineSection() {
        VBox section = new VBox(10);
        HBox secHeader = new HBox(10);
        secHeader.setAlignment(Pos.CENTER_LEFT);
        Label sectionTitle = new Label("Pipeline Execution Stages (Click a stage to filter logs)");
        sectionTitle.getStyleClass().add("card-header");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button showAllStagesBtn = new Button("Show All Stage Logs");
        showAllStagesBtn.getStyleClass().add("btn-secondary");
        showAllStagesBtn.setStyle("-fx-padding: 3px 8px; -fx-font-size: 11px;");
        showAllStagesBtn.setOnAction(e -> {
            selectedStageIdFilter = null;
            updateLogFilterBanner();
            reloadLogs();
            loadExecutionData();
        });

        secHeader.getChildren().addAll(sectionTitle, spacer, showAllStagesBtn);

        ScrollPane scroll = new ScrollPane();
        scroll.setFitToHeight(true);
        scroll.setStyle("-fx-background-color: transparent; -fx-background: transparent; -fx-padding: 0;");

        stageCardsContainer = new HBox(14);
        stageCardsContainer.setAlignment(Pos.CENTER_LEFT);
        stageCardsContainer.setPadding(new Insets(4, 4, 8, 4));

        scroll.setContent(stageCardsContainer);
        section.getChildren().addAll(secHeader, scroll);
        this.getChildren().add(section);
    }

    private void buildTimelineSection() {
        VBox section = new VBox(10);
        section.getStyleClass().add("timeline-card");

        HBox secHeader = new HBox(10);
        secHeader.setAlignment(Pos.CENTER_LEFT);

        SvgIcon clockIcon = new SvgIcon(SvgIcon.IconName.CLOCK, 14, Color.web("#38BDF8"));
        Label sectionTitle = new Label("Operational Lifecycle Timeline");
        sectionTitle.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #FFFFFF;");

        timelineCountLabel = new Label("(0 events recorded)");
        timelineCountLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #64748B;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        secHeader.getChildren().addAll(clockIcon, sectionTitle, timelineCountLabel, spacer);

        ScrollPane scroll = new ScrollPane();
        scroll.setFitToWidth(true);
        scroll.setPrefHeight(120);
        scroll.setMaxHeight(160);
        scroll.setStyle("-fx-background-color: transparent; -fx-background: transparent; -fx-padding: 0;");

        timelineContainer = new VBox(6);
        timelineContainer.setPadding(new Insets(4));
        scroll.setContent(timelineContainer);

        section.getChildren().addAll(secHeader, scroll);
        this.getChildren().add(section);
    }

    private void buildDiagnosisSection() {
        diagnosisPanel = new VBox(12);
        diagnosisPanel.getStyleClass().add("intelligence-card");
        diagnosisPanel.setVisible(false);
        diagnosisPanel.setManaged(false);
        this.getChildren().add(diagnosisPanel);
    }

    private void buildArtifactSection() {
        artifactPanel = new VBox(10);
        artifactPanel.getStyleClass().add("card");
        artifactPanel.setVisible(false);
        artifactPanel.setManaged(false);
        this.getChildren().add(artifactPanel);
    }

    private void buildLogConsoleSection() {
        VBox consoleCard = new VBox(8);
        consoleCard.getStyleClass().add("card");

        HBox consoleHeader = new HBox(10);
        consoleHeader.setAlignment(Pos.CENTER_LEFT);

        SvgIcon termIcon = new SvgIcon(SvgIcon.IconName.TERMINAL, 16, Color.web("#38BDF8"));
        Label termTitle = new Label("Execution Console Output");
        termTitle.setStyle("-fx-font-weight: bold; -fx-font-size: 13px; -fx-text-fill: #FFFFFF;");

        logFilterLabel = new Label("(Showing All Stages)");
        logFilterLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #38BDF8; -fx-font-weight: bold;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label streamLabel = new Label("Stream:");
        streamLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #94A3B8;");

        streamFilterCombo = new ComboBox<>();
        streamFilterCombo.getItems().addAll("All Streams", "STDOUT Only", "STDERR Only");
        streamFilterCombo.setValue("All Streams");
        streamFilterCombo.setStyle("-fx-font-size: 11px;");
        streamFilterCombo.setOnAction(e -> reloadLogs());

        Button jumpToLatestBtn = new Button("Jump to Latest");
        jumpToLatestBtn.getStyleClass().add("btn-secondary");
        jumpToLatestBtn.setStyle("-fx-padding: 3px 8px; -fx-font-size: 11px;");
        jumpToLatestBtn.setOnAction(e -> terminalArea.positionCaret(terminalArea.getText().length()));

        Button copyLogsBtn = new Button("Copy Output");
        copyLogsBtn.getStyleClass().add("btn-secondary");
        copyLogsBtn.setGraphic(new SvgIcon(SvgIcon.IconName.COPY, 12, Color.web("#64748B")));
        copyLogsBtn.setOnAction(e -> {
            javafx.scene.input.Clipboard clipboard = javafx.scene.input.Clipboard.getSystemClipboard();
            javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent();
            content.putString(terminalArea.getText());
            clipboard.setContent(content);
            mainLayout.showToast("Logs copied to clipboard", ToastNotification.ToastType.SUCCESS);
        });

        consoleHeader.getChildren().addAll(termIcon, termTitle, logFilterLabel, spacer, streamLabel, streamFilterCombo, jumpToLatestBtn, copyLogsBtn);

        terminalArea = new TextArea();
        terminalArea.setEditable(false);
        terminalArea.setWrapText(false);
        terminalArea.setPrefRowCount(14);
        terminalArea.setStyle("-fx-font-family: Menlo, 'JetBrains Mono', monospace; -fx-font-size: 11px; " +
                "-fx-control-inner-background: #0F172A; -fx-text-fill: #E2E8F0;");

        consoleCard.getChildren().addAll(consoleHeader, terminalArea);
        this.getChildren().add(consoleCard);
    }

    private void updateLogFilterBanner() {
        if (selectedStageIdFilter == null) {
            logFilterLabel.setText("(Showing All Stages)");
        } else {
            String name = "Selected Stage";
            if (currentExecution != null && currentExecution.getStageExecutions() != null) {
                for (StageExecution se : currentExecution.getStageExecutions()) {
                    if (se.getId().equals(selectedStageIdFilter)) {
                        name = se.getStageName();
                        break;
                    }
                }
            }
            logFilterLabel.setText("(Filtered to: " + name + ")");
        }
    }

    private void reloadLogs() {
        List<ExecutionLog> logs;
        LogCollector active = executionEngine.getActiveLogCollector(executionId);
        if (active != null) {
            logs = active.getBufferedLogs();
        } else {
            logs = logRepository.findByExecutionId(executionId);
        }

        String filterStream = streamFilterCombo != null ? streamFilterCombo.getValue() : "All Streams";
        StringBuilder sb = new StringBuilder();

        for (ExecutionLog l : logs) {
            if (selectedStageIdFilter != null && l.getStageExecutionId() != null
                    && !l.getStageExecutionId().equals(selectedStageIdFilter)) {
                continue;
            }
            if ("STDOUT Only".equals(filterStream) && l.getStream() == LogStream.STDERR) {
                continue;
            }
            if ("STDERR Only".equals(filterStream) && l.getStream() == LogStream.STDOUT) {
                continue;
            }
            sb.append(l.getMessage()).append("\n");
        }

        String resultText = sb.toString();
        if (resultText.isEmpty()) {
            if (currentExecution != null && currentExecution.getStatus() == ExecutionStatus.BLOCKED) {
                resultText = "[System] No stage console output was generated: Execution was blocked by isolation security policy before subprocess initialization.\n";
            } else if (currentExecution != null && currentExecution.getStatus() == ExecutionStatus.RUNNING) {
                resultText = "[System] Waiting for console stdout/stderr stream from running process...\n";
            } else {
                resultText = "[System] No console output recorded for this execution.\n";
            }
        }

        terminalArea.setText(resultText);
        terminalArea.positionCaret(terminalArea.getText().length());
    }

    public void loadExecutionData() {
        if (isDisposed.get()) return;

        Optional<Execution> opt = executionRepository.findById(executionId);
        if (opt.isEmpty()) return;

        currentExecution = opt.get();
        boolean isRunning = currentExecution.getStatus() == ExecutionStatus.RUNNING;

        // Manage Live Timer (Section 4.1)
        if (isRunning) {
            if (liveTimer == null) {
                liveStartEpochMillis = currentExecution.getStartedAt() != null ?
                        currentExecution.getStartedAt().getTime() : System.currentTimeMillis();
                startLiveTimer();
            }
        } else {
            stopLiveTimer();
            buildTitleLabel.setText(String.format("BUILD #%d  /  %s  /  %.1fS",
                    currentExecution.getExecutionNumber(),
                    currentExecution.getStatus(),
                    currentExecution.getDurationSeconds()));
        }

        StringBuilder meta = new StringBuilder();
        meta.append("Pipeline: ").append(currentExecution.getPipelineName())
            .append(" | Project: ").append(currentExecution.getProjectName());
        if (currentExecution.getProjectType() != null) {
            meta.append(" (").append(currentExecution.getProjectType()).append(")");
        }
        meta.append(" | Trigger: ").append(currentExecution.getTriggerType());
        if (currentExecution.getCommitHash() != null && !currentExecution.getCommitHash().isEmpty()) {
            String shortHash = currentExecution.getCommitHash().substring(0, Math.min(8, currentExecution.getCommitHash().length()));
            meta.append(" | Commit: ").append(shortHash);
        }
        if (currentExecution.getBackendDescription() != null) {
            meta.append(" | Backend: ").append(currentExecution.getBackendDescription());
        }
        buildMetaLabel.setText(meta.toString());

        // Update Phase Label
        String phase = currentExecution.getCurrentPhase();
        if (phase == null || phase.isEmpty()) {
            phase = isRunning ? "Running Pipeline Stages" :
                    (currentExecution.getStatus() == ExecutionStatus.SUCCESS ? "Completed Successfully" :
                            (currentExecution.getStatus() == ExecutionStatus.BLOCKED ? "Blocked by Security Policy" : "Execution Finished"));
        }
        phaseLabel.setText("Phase: " + phase);

        cancelBtn.setVisible(isRunning);

        // Update Header Badge
        if (badgeContainer.getChildren().size() > 3) {
            badgeContainer.getChildren().remove(0);
        }
        badgeContainer.getChildren().add(0, StatusBadge.of(currentExecution.getStatus()));

        // Populate Stage Cards
        stageCardsContainer.getChildren().clear();
        List<StageExecution> stages = currentExecution.getStageExecutions();
        int completedCount = 0;
        int totalStages = stages.size();

        for (int i = 0; i < stages.size(); i++) {
            StageExecution se = stages.get(i);
            if (se.getStatus() == StageExecutionStatus.SUCCESS) {
                completedCount++;
            }
            VBox card = createStageCard(se, i + 1);
            stageCardsContainer.getChildren().add(card);

            if (i < stages.size() - 1) {
                SvgIcon chevron = new SvgIcon(SvgIcon.IconName.CHEVRON_RIGHT, 14, Color.web("#CBD5E1"));
                stageCardsContainer.getChildren().add(chevron);
            }
        }

        // Update Progress Indicator (Section 4.2)
        updateProgressIndicator(completedCount, totalStages, currentExecution.getStatus());

        // Populate Timeline Events (Section 4.4)
        loadTimelineEvents();

        // Populate Diagnosis if Failed or Blocked (Section 6)
        Optional<FailureDiagnosis> diagOpt = diagnosisRepository.findByExecutionId(executionId);
        if (diagOpt.isPresent() || currentExecution.getStatus() == ExecutionStatus.FAILED || currentExecution.getStatus() == ExecutionStatus.BLOCKED) {
            renderDiagnosis(diagOpt.orElse(null), currentExecution.getStatus());
        } else {
            diagnosisPanel.setVisible(false);
            diagnosisPanel.setManaged(false);
        }

        // Populate Artifact Section (Section 4.6)
        renderArtifacts();

        if (activeCollectorRef == null && isRunning) {
            attachLogCollectorIfAvailable();
        }

        if (terminalArea.getText().isEmpty()
                || terminalArea.getText().startsWith("[System] Waiting")
                || !isRunning) {
            reloadLogs();
        }
    }

    private void updateProgressIndicator(int completed, int total, ExecutionStatus status) {
        if (total == 0) {
            progressBar.setProgress(0.0);
            progressLabel.setText("No stages configured");
            return;
        }

        double fraction = (double) completed / total;
        progressBar.setProgress(fraction);

        if (status == ExecutionStatus.BLOCKED) {
            progressLabel.setText(String.format("0 of %d stages complete (Execution Blocked by Policy)", total));
            progressBar.setStyle("-fx-accent: #D97706;");
        } else if (status == ExecutionStatus.FAILED) {
            progressLabel.setText(String.format("%d of %d stages complete (Pipeline Failed)", completed, total));
            progressBar.setStyle("-fx-accent: #E11D48;");
        } else if (status == ExecutionStatus.RUNNING) {
            progressLabel.setText(String.format("%d of %d stages complete (In Progress)", completed, total));
            progressBar.setStyle("-fx-accent: #0284C7;");
        } else if (status == ExecutionStatus.SUCCESS) {
            progressLabel.setText(String.format("%d of %d stages complete (100%% Stage Completion)", completed, total));
            progressBar.setStyle("-fx-accent: #059669;");
        } else {
            progressLabel.setText(String.format("%d of %d stages complete (%.0f%%)", completed, total, fraction * 100));
        }
    }

    private void startLiveTimer() {
        stopLiveTimer();
        liveTimer = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            if (isDisposed.get()) return;
            long elapsedSeconds = Math.max(0, (System.currentTimeMillis() - liveStartEpochMillis) / 1000);
            long hours = elapsedSeconds / 3600;
            long minutes = (elapsedSeconds % 3600) / 60;
            long secs = elapsedSeconds % 60;
            String timeStr = String.format("%02d:%02d:%02d", hours, minutes, secs);

            buildTitleLabel.setText(String.format("BUILD #%d  /  RUNNING  /  %s",
                    currentExecution != null ? currentExecution.getExecutionNumber() : 0,
                    timeStr));

            loadExecutionData();
        }));
        liveTimer.setCycleCount(Animation.INDEFINITE);
        liveTimer.play();
    }

    private void stopLiveTimer() {
        if (liveTimer != null) {
            liveTimer.stop();
            liveTimer = null;
        }
    }

    private VBox createStageCard(StageExecution se, int stepNum) {
        VBox card = new VBox(6);
        card.getStyleClass().add("stage-card");
        card.setPrefWidth(180);
        card.setStyle("-fx-cursor: hand;");

        if (se.getStatus() == StageExecutionStatus.SUCCESS) {
            card.getStyleClass().add("stage-card-success");
        } else if (se.getStatus() == StageExecutionStatus.FAILED) {
            card.getStyleClass().add("stage-card-failed");
        } else if (se.getStatus() == StageExecutionStatus.BLOCKED) {
            card.getStyleClass().add("stage-card-blocked");
        } else if (se.getStatus() == StageExecutionStatus.RUNNING) {
            card.getStyleClass().add("stage-card-running");
        } else if (se.getStatus() == StageExecutionStatus.SKIPPED) {
            card.getStyleClass().add("stage-card-skipped");
        }

        if (se.getId().equals(selectedStageIdFilter)) {
            card.setStyle("-fx-cursor: hand; -fx-border-width: 2px; -fx-border-color: #38BDF8;");
        }

        card.setOnMouseClicked(e -> {
            if (se.getId().equals(selectedStageIdFilter)) {
                selectedStageIdFilter = null;
            } else {
                selectedStageIdFilter = se.getId();
            }
            updateLogFilterBanner();
            loadExecutionData();
            reloadLogs();
        });

        Label stepLabel = new Label("STAGE " + stepNum);
        stepLabel.setStyle("-fx-font-size: 10px; -fx-font-weight: bold; -fx-text-fill: #64748B;");

        Label nameLabel = new Label(se.getStageName());
        nameLabel.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #FFFFFF;");
        nameLabel.setWrapText(true);

        Label cmdLabel = new Label(se.getCommand() != null ? se.getCommand() : "-");
        cmdLabel.setStyle("-fx-font-family: Menlo, monospace; -fx-font-size: 10px; -fx-text-fill: #38BDF8; -fx-background-color: #0F172A; -fx-padding: 2px 4px; -fx-background-radius: 4px;");
        cmdLabel.setMaxWidth(160);

        HBox metaRow = new HBox(8);
        metaRow.setAlignment(Pos.CENTER_LEFT);
        String durStr = "-";
        if (se.getDurationSeconds() > 0) {
            durStr = String.format("%.1fs", se.getDurationSeconds());
        } else if (se.getStatus() == StageExecutionStatus.RUNNING && se.getStartedAt() != null) {
            double liveSec = Math.max(0.1, (System.currentTimeMillis() - se.getStartedAt().getTime()) / 1000.0);
            durStr = String.format("%.1fs", liveSec);
        }
        Label durationLabel = new Label(durStr);
        durationLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #8A8D9E;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // Truthful Exit Code (Section 4.3): Only display exit code if process actually executed!
        Label exitLabel = new Label();
        if (se.hasProcessExecuted()) {
            exitLabel.setText("exit " + se.getExitCode());
            exitLabel.setStyle("-fx-font-size: 10px; -fx-font-weight: bold; -fx-text-fill: " +
                    (se.getExitCode() == 0 ? "#10B981" : "#F43F5E") + ";");
        } else if (se.getStatus() == StageExecutionStatus.SKIPPED) {
            exitLabel.setText("SKIPPED");
            exitLabel.setStyle("-fx-font-size: 9px; -fx-font-weight: bold; -fx-text-fill: #64748B;");
        } else if (se.getStatus() == StageExecutionStatus.BLOCKED) {
            exitLabel.setText("BLOCKED");
            exitLabel.setStyle("-fx-font-size: 9px; -fx-font-weight: bold; -fx-text-fill: #D97706;");
        } else if (se.getStatus() == StageExecutionStatus.RUNNING) {
            exitLabel.setText("RUNNING");
            exitLabel.setStyle("-fx-font-size: 9px; -fx-font-weight: bold; -fx-text-fill: #38BDF8;");
        } else {
            exitLabel.setText("-");
            exitLabel.setStyle("-fx-font-size: 9px; -fx-text-fill: #475569;");
        }

        metaRow.getChildren().addAll(durationLabel, spacer, exitLabel);
        StatusBadge badge = StatusBadge.of(se.getStatus());

        // Skip/error reason subtext if skipped
        if (se.getStatus() == StageExecutionStatus.SKIPPED && se.getErrorMessage() != null) {
            String shortReason = se.getErrorMessage();
            if (shortReason.length() > 35) shortReason = shortReason.substring(0, 32) + "...";
            Label reasonLabel = new Label(shortReason);
            reasonLabel.setStyle("-fx-font-size: 9px; -fx-text-fill: #94A3B8; -fx-font-style: italic;");
            reasonLabel.setTooltip(new Tooltip(se.getErrorMessage()));
            card.getChildren().addAll(stepLabel, nameLabel, cmdLabel, metaRow, badge, reasonLabel);
        } else {
            card.getChildren().addAll(stepLabel, nameLabel, cmdLabel, metaRow, badge);
        }

        return card;
    }

    private void loadTimelineEvents() {
        timelineContainer.getChildren().clear();
        List<ExecutionEvent> events = eventBus.getEvents(executionId);

        // Fallback for historical runs that had no recorded events
        if (events.isEmpty() && currentExecution != null) {
            events = reconstructTimelineFromHistory();
        }

        timelineCountLabel.setText(String.format("(%d operational events recorded)", events.size()));

        for (ExecutionEvent ev : events) {
            HBox item = createTimelineItem(ev);
            timelineContainer.getChildren().add(item);
        }
    }

    private List<ExecutionEvent> reconstructTimelineFromHistory() {
        List<ExecutionEvent> list = new ArrayList<>();
        long seq = 1;
        if (currentExecution.getStartedAt() != null) {
            list.add(new ExecutionEvent(seq++, executionId, currentExecution.getPipelineId(),
                    ExecutionEventType.EXECUTION_STARTED, "Execution started at " + currentExecution.getStartedAt()));
        }
        if (currentExecution.getCommitHash() != null && !currentExecution.getCommitHash().isEmpty()) {
            list.add(new ExecutionEvent(seq++, executionId, currentExecution.getPipelineId(),
                    ExecutionEventType.COMMIT_RESOLVED, "Commit resolved: " + currentExecution.getCommitHash()));
        }
        if (currentExecution.getStageExecutions() != null) {
            for (StageExecution se : currentExecution.getStageExecutions()) {
                if (se.getStatus() == StageExecutionStatus.SUCCESS) {
                    list.add(new ExecutionEvent(seq++, executionId, currentExecution.getPipelineId(),
                            ExecutionEventType.STAGE_COMPLETED, String.format("Stage '%s' succeeded in %.1fs", se.getStageName(), se.getDurationSeconds())));
                } else if (se.getStatus() == StageExecutionStatus.FAILED) {
                    list.add(new ExecutionEvent(seq++, executionId, currentExecution.getPipelineId(),
                            ExecutionEventType.STAGE_FAILED, String.format("Stage '%s' failed with exit code %d", se.getStageName(), se.getExitCode())));
                } else if (se.getStatus() == StageExecutionStatus.SKIPPED) {
                    list.add(new ExecutionEvent(seq++, executionId, currentExecution.getPipelineId(),
                            ExecutionEventType.STAGE_SKIPPED, String.format("Stage '%s' was skipped", se.getStageName())));
                }
            }
        }
        if (currentExecution.getArtifactPath() != null) {
            list.add(new ExecutionEvent(seq++, executionId, currentExecution.getPipelineId(),
                    ExecutionEventType.ARTIFACT_VERIFIED, "Artifact verified: " + currentExecution.getArtifactPath()));
        }
        if (currentExecution.getStatus() != null && currentExecution.getStatus().isTerminal()) {
            ExecutionEventType t = currentExecution.getStatus() == ExecutionStatus.SUCCESS ? ExecutionEventType.EXECUTION_COMPLETED :
                    (currentExecution.getStatus() == ExecutionStatus.BLOCKED ? ExecutionEventType.EXECUTION_BLOCKED : ExecutionEventType.EXECUTION_FAILED);
            list.add(new ExecutionEvent(seq++, executionId, currentExecution.getPipelineId(), t,
                    String.format("Execution finalized with status: %s (Total Duration: %.1fs)", currentExecution.getStatus(), currentExecution.getDurationSeconds())));
        }
        return list;
    }

    private HBox createTimelineItem(ExecutionEvent ev) {
        HBox row = new HBox(10);
        row.getStyleClass().add("timeline-item");

        Label timeLabel = new Label(ev.getFormattedTime());
        timeLabel.getStyleClass().add("timeline-time");

        SvgIcon icon;
        if (ev.getEventType().isError()) {
            icon = new SvgIcon(SvgIcon.IconName.X_CIRCLE, 13, Color.web("#F43F5E"));
        } else if (ev.getEventType().isWarning()) {
            icon = new SvgIcon(SvgIcon.IconName.ALERT_TRIANGLE, 13, Color.web("#F59E0B"));
        } else if (ev.getEventType().isSuccess()) {
            icon = new SvgIcon(SvgIcon.IconName.CHECK_CIRCLE, 13, Color.web("#10B981"));
        } else {
            icon = new SvgIcon(SvgIcon.IconName.INFO, 13, Color.web("#38BDF8"));
        }

        Label msgLabel = new Label(ev.getMessage());
        msgLabel.getStyleClass().add("timeline-msg");
        msgLabel.setWrapText(true);

        row.getChildren().addAll(timeLabel, icon, msgLabel);
        return row;
    }

    private void renderDiagnosis(FailureDiagnosis d, ExecutionStatus executionStatus) {
        diagnosisPanel.getChildren().clear();
        diagnosisPanel.setVisible(true);
        diagnosisPanel.setManaged(true);

        boolean isBlocked = executionStatus == ExecutionStatus.BLOCKED || (d != null && d.getCategory() == FailureCategory.SECURITY_POLICY_VIOLATION);

        HBox diagHeader = new HBox(10);
        diagHeader.setAlignment(Pos.CENTER_LEFT);

        SvgIcon bulbIcon = new SvgIcon(SvgIcon.IconName.LIGHTBULB, 18, isBlocked ? Color.web("#F59E0B") : Color.web("#F43F5E"));
        Label headerTitle = new Label(isBlocked ? "SECURITY POLICY DIAGNOSIS (BLOCKED)" : "INTELLIGENT FAILURE DIAGNOSIS (RULE MATCH)");
        headerTitle.setStyle(String.format("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: %s;",
                isBlocked ? "#F59E0B" : "#F43F5E"));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label ruleIdTag = new Label(d != null ? "Rule: " + d.getRuleId() : (isBlocked ? "Rule: SEC_ISOLATION" : "Rule: UNCLASSIFIED"));
        ruleIdTag.setStyle("-fx-font-size: 11px; -fx-text-fill: #8A8D9E; -fx-background-color: #1E2030; -fx-padding: 2px 6px; -fx-background-radius: 4px;");

        diagHeader.getChildren().addAll(bulbIcon, headerTitle, spacer, ruleIdTag);

        String title;
        String likelyCause;
        String evidence;
        String action;

        if (d != null) {
            title = d.getTitle();
            likelyCause = d.getLikelyCause();
            evidence = d.getEvidenceExcerpt() != null ? d.getEvidenceExcerpt() : "No evidence excerpt available.";
            action = d.getSuggestedAction() != null ? d.getSuggestedAction() : "Inspect execution logs.";
        } else if (isBlocked) {
            title = "Execution Blocked by Security Policy (SEC-01)";
            likelyCause = "Untrusted or external repository execution requires verified container isolation (Docker), which is not available on this host. DevOpsHub fails closed to protect host workstation security.";
            evidence = "Security Policy Rejection: External repository requires Docker container isolation. Docker daemon unavailable.";
            action = "Start Docker daemon on host machine or configure container runtime options in Settings.";
        } else {
            // Truthful fallback (Section 6.4)
            title = "Unclassified Execution Failure";
            likelyCause = "The stage process terminated with a non-zero exit code without matching a specific predefined pattern.";
            evidence = "No specific failure rule matched. Review recorded console output.";
            action = "No specific diagnosis rule matched this failure. Review the recorded exception and execution logs.";
        }

        Label titleLabel = new Label(title);
        titleLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #FFFFFF;");

        Label likelyCauseLabel = new Label(likelyCause);
        likelyCauseLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: #A0A5BA;");
        likelyCauseLabel.setWrapText(true);

        // Evidence Box (Section 6.1)
        VBox evidenceBox = new VBox(4);
        evidenceBox.setStyle("-fx-background-color: #0F172A; -fx-background-radius: 6px; -fx-padding: 10px;");
        Label evidenceTitle = new Label("SUPPORTING EVIDENCE FROM EXECUTION LOGS:");
        evidenceTitle.setStyle("-fx-font-size: 10px; -fx-font-weight: bold; -fx-text-fill: #94A3B8;");
        Label evidenceText = new Label(evidence);
        evidenceText.setStyle("-fx-font-family: Menlo, monospace; -fx-font-size: 11px; -fx-text-fill: " +
                (isBlocked ? "#FBBF24" : "#F87171") + ";");
        evidenceText.setWrapText(true);
        evidenceBox.getChildren().addAll(evidenceTitle, evidenceText);

        // Suggested Action Box
        VBox actionBox = new VBox(4);
        actionBox.getStyleClass().add("recommendation-box");

        HBox actionHeader = new HBox(8);
        actionHeader.setAlignment(Pos.CENTER_LEFT);
        Label actionTitle = new Label("RECOMMENDED RECOVERY ACTION:");
        actionTitle.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #065F46;");
        Region actionSpacer = new Region();
        HBox.setHgrow(actionSpacer, Priority.ALWAYS);

        Button copyActionBtn = new Button("Copy Recommendation");
        copyActionBtn.getStyleClass().add("btn-secondary");
        copyActionBtn.setStyle("-fx-padding: 2px 8px; -fx-font-size: 10px;");
        final String copyText = action;
        copyActionBtn.setOnAction(e -> {
            javafx.scene.input.Clipboard clipboard = javafx.scene.input.Clipboard.getSystemClipboard();
            javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent();
            content.putString(copyText);
            clipboard.setContent(content);
            mainLayout.showToast("Recommendation copied to clipboard", ToastNotification.ToastType.SUCCESS);
        });
        actionHeader.getChildren().addAll(actionTitle, actionSpacer, copyActionBtn);

        Label actionText = new Label(action);
        actionText.setStyle("-fx-font-size: 13px; -fx-text-fill: #047857; -fx-font-weight: bold;");
        actionText.setWrapText(true);
        actionBox.getChildren().addAll(actionHeader, actionText);

        diagnosisPanel.getChildren().addAll(diagHeader, titleLabel, likelyCauseLabel, evidenceBox, actionBox);
    }

    private void renderArtifacts() {
        artifactPanel.getChildren().clear();

        if (currentExecution == null || currentExecution.getArtifactPath() == null || currentExecution.getArtifactPath().isEmpty()) {
            artifactPanel.setVisible(false);
            artifactPanel.setManaged(false);
            return;
        }

        File file = new File(currentExecution.getArtifactPath());
        if (!file.exists()) {
            artifactPanel.setVisible(false);
            artifactPanel.setManaged(false);
            return;
        }

        artifactPanel.setVisible(true);
        artifactPanel.setManaged(true);

        HBox header = new HBox(8);
        header.setAlignment(Pos.CENTER_LEFT);
        SvgIcon boxIcon = new SvgIcon(SvgIcon.IconName.CHECK_CIRCLE, 16, Color.web("#10B981"));
        Label title = new Label("Verified Build Artifacts & Deliverables");
        title.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #FFFFFF;");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        StatusBadge verifiedBadge = new StatusBadge("SUCCESS");
        header.getChildren().addAll(boxIcon, title, spacer, verifiedBadge);

        VBox contentBox = new VBox(6);
        contentBox.setStyle("-fx-background-color: #0F172A; -fx-padding: 12px; -fx-background-radius: 6px;");

        long sizeBytes = file.isDirectory() ? calculateDirSize(file) : file.length();
        String formattedSize = formatBytes(sizeBytes);
        String sha = "N/A (Directory Bundle)";
        if (file.isFile()) {
            try {
                sha = DeploymentService.computeSha256(file);
            } catch (Exception ignored) {}
        } else if (new File(file, "BUILD_ID").exists()) {
            try {
                sha = DeploymentService.computeSha256(new File(file, "BUILD_ID"));
            } catch (Exception ignored) {}
        }

        Label nameLabel = new Label("Artifact: " + file.getName());
        nameLabel.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #38BDF8;");

        Label pathLabel = new Label("Physical Location: " + file.getAbsolutePath());
        pathLabel.setStyle("-fx-font-family: Menlo, monospace; -fx-font-size: 11px; -fx-text-fill: #94A3B8;");

        Label sizeLabel = new Label(String.format("Verified Size: %s (%d bytes) | Checksum (SHA-256): %s", formattedSize, sizeBytes, sha));
        sizeLabel.setStyle("-fx-font-family: Menlo, monospace; -fx-font-size: 11px; -fx-text-fill: #10B981;");

        contentBox.getChildren().addAll(nameLabel, pathLabel, sizeLabel);
        artifactPanel.getChildren().addAll(header, contentBox);
    }

    private long calculateDirSize(File dir) {
        if (dir == null || !dir.exists()) return 0;
        long total = 0;
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isFile()) total += f.length();
                else if (f.isDirectory() && !f.getName().equals(".git")) {
                    total += calculateDirSize(f);
                }
            }
        }
        return total;
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.2f MB", bytes / (1024.0 * 1024.0));
    }

    private void attachLogCollectorIfAvailable() {
        if (activeCollectorRef == null && liveLogListener != null && executionEngine != null) {
            LogCollector collector = executionEngine.getActiveLogCollector(executionId);
            if (collector != null) {
                activeCollectorRef = collector;
                activeCollectorRef.addListener(liveLogListener);
                reloadLogs();
            }
        }
    }

    private void setupLiveSubscriptions() {
        // 1. Live log streaming
        liveLogListener = log -> Platform.runLater(() -> {
            if (isDisposed.get()) return;
            if (selectedStageIdFilter == null ||
                    (log.getStageExecutionId() != null && log.getStageExecutionId().equals(selectedStageIdFilter))) {
                String current = terminalArea.getText();
                if (current.startsWith("[System] Waiting for console") || current.startsWith("[System] No console output")) {
                    terminalArea.clear();
                }
                terminalArea.appendText(log.getMessage() + "\n");
                terminalArea.positionCaret(terminalArea.getText().length());
            }
        });

        attachLogCollectorIfAvailable();

        // 2. Execution update subscription
        liveUpdateListener = ex -> Platform.runLater(() -> {
            if (isDisposed.get()) return;
            loadExecutionData();
        });
        executionEngine.addExecutionListener(executionId, liveUpdateListener);

        // 3. Granular lifecycle event bus subscription
        liveEventListener = ev -> Platform.runLater(() -> {
            if (isDisposed.get()) return;
            loadTimelineEvents();
            loadExecutionData();
        });
        eventBus.subscribe(executionId, liveEventListener);
    }

    /**
     * Clean resource disposal to prevent timer and listener leaks on view change.
     */
    public void dispose() {
        if (isDisposed.compareAndSet(false, true)) {
            stopLiveTimer();
            if (activeCollectorRef != null && liveLogListener != null) {
                activeCollectorRef.removeListener(liveLogListener);
            }
            if (liveUpdateListener != null) {
                executionEngine.removeExecutionListener(executionId, liveUpdateListener);
            }
            if (liveEventListener != null) {
                eventBus.unsubscribe(executionId, liveEventListener);
            }
        }
    }
}
