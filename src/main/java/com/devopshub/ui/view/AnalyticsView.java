package com.devopshub.ui.view;

import com.devopshub.model.DashboardMetrics;
import com.devopshub.service.DashboardService;
import com.devopshub.ui.component.MetricCard;
import com.devopshub.ui.component.SvgIcon;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.chart.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;

import java.util.Map;

/**
 * Analytics View: Failure category distributions, execution reliability,
 * most frequently failing pipelines, and operational trends.
 */
public class AnalyticsView extends VBox {

    private final DashboardService dashboardService;

    private ComboBox<String> timeRangeFilter;
    private PieChart pieChart;
    private BarChart<String, Number> barChart;
    private TableView<Map.Entry<String, Integer>> failureRankingTable;

    private Label summary1;
    private Label summary2;
    private Label summary3;
    private Label summary4;

    public AnalyticsView(DashboardService dashboardService) {
        this.dashboardService = dashboardService;

        this.setSpacing(20);
        this.setPadding(new Insets(24));
        this.setStyle("-fx-background-color: transparent;");

        buildToolbar();
        buildAnalyticsCharts();
        buildFailureRankingAndInsights();
        refresh();
    }

    private void buildToolbar() {
        HBox toolbar = new HBox(12);
        toolbar.setAlignment(Pos.CENTER_LEFT);

        Label rangeLabel = new Label("Time Window:");
        rangeLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #E2E8F0;");

        timeRangeFilter = new ComboBox<>();
        timeRangeFilter.getItems().addAll("ALL TIME (LIFETIME)", "LAST 30 DAYS", "LAST 7 DAYS");
        timeRangeFilter.setValue("ALL TIME (LIFETIME)");
        timeRangeFilter.setOnAction(e -> refresh());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button refreshBtn = new Button("Refresh Analytics");
        refreshBtn.getStyleClass().add("btn-secondary");
        refreshBtn.setGraphic(new SvgIcon(SvgIcon.IconName.REFRESH, 13, Color.web("#64748B")));
        refreshBtn.setOnAction(e -> refresh());

        toolbar.getChildren().addAll(rangeLabel, timeRangeFilter, spacer, refreshBtn);
        this.getChildren().add(toolbar);
    }

    private void buildAnalyticsCharts() {
        HBox chartsRow = new HBox(20);
        chartsRow.setAlignment(Pos.CENTER);

        // Chart 1: Execution Status Distribution (Pie Chart)
        VBox statusChartCard = new VBox(12);
        statusChartCard.getStyleClass().add("card");
        HBox.setHgrow(statusChartCard, Priority.ALWAYS);
        statusChartCard.setPrefWidth(450);

        Label statusTitle = new Label("Execution Outcomes Distribution");
        statusTitle.getStyleClass().add("card-header");

        pieChart = new PieChart();
        pieChart.setPrefHeight(280);
        pieChart.setLegendVisible(true);

        statusChartCard.getChildren().addAll(statusTitle, pieChart);

        // Chart 2: Failure Category Breakdown (Bar Chart)
        VBox categoryChartCard = new VBox(12);
        categoryChartCard.getStyleClass().add("card");
        HBox.setHgrow(categoryChartCard, Priority.ALWAYS);
        categoryChartCard.setPrefWidth(550);

        Label categoryTitle = new Label("Rule-Based Failure Categories");
        categoryTitle.getStyleClass().add("card-header");

        CategoryAxis xAxis = new CategoryAxis();
        xAxis.setLabel("Failure Category");
        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel("Incidents");

        barChart = new BarChart<>(xAxis, yAxis);
        barChart.setPrefHeight(280);
        barChart.setLegendVisible(false);

        categoryChartCard.getChildren().addAll(categoryTitle, barChart);
        chartsRow.getChildren().addAll(statusChartCard, categoryChartCard);
        this.getChildren().add(chartsRow);
    }

    private void buildFailureRankingAndInsights() {
        HBox row = new HBox(20);

        // Left Table: Most Frequently Failing Pipelines
        VBox rankCard = new VBox(10);
        rankCard.getStyleClass().add("card");
        HBox.setHgrow(rankCard, Priority.ALWAYS);
        rankCard.setPrefWidth(500);

        Label rankTitle = new Label("Frequently Failing Pipelines (Top Hotspots)");
        rankTitle.getStyleClass().add("card-header");

        failureRankingTable = new TableView<>();
        failureRankingTable.setPrefHeight(200);

        TableColumn<Map.Entry<String, Integer>, String> colName = new TableColumn<>("Pipeline Name");
        colName.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getKey()));
        colName.setPrefWidth(320);

        TableColumn<Map.Entry<String, Integer>, Number> colIncidents = new TableColumn<>("Failed Runs");
        colIncidents.setCellValueFactory(d -> new SimpleIntegerProperty(d.getValue().getValue()));
        colIncidents.setPrefWidth(120);

        failureRankingTable.getColumns().addAll(colName, colIncidents);
        rankCard.getChildren().addAll(rankTitle, failureRankingTable);

        // Right Card: Operational Intelligence Summary
        VBox insightsCard = new VBox(10);
        insightsCard.getStyleClass().add("card");
        HBox.setHgrow(insightsCard, Priority.ALWAYS);
        insightsCard.setPrefWidth(500);

        Label insightsTitle = new Label("Operational Intelligence Summary");
        insightsTitle.getStyleClass().add("card-header");

        summary1 = new Label("• Delivery Reliability: calculating...");
        summary2 = new Label("• Mean Build & Verification Duration: calculating...");
        summary3 = new Label("• Primary Root Cause: analyzing failure history...");
        summary4 = new Label("• Optimization Recommendation: evaluating run patterns...");

        summary1.setStyle("-fx-font-size: 13px; -fx-text-fill: #94A3B8;");
        summary2.setStyle("-fx-font-size: 13px; -fx-text-fill: #94A3B8;");
        summary3.setStyle("-fx-font-size: 13px; -fx-text-fill: #94A3B8;");
        summary4.setStyle("-fx-font-size: 13px; -fx-text-fill: #34D399; -fx-font-weight: bold;");

        insightsCard.getChildren().addAll(insightsTitle, summary1, summary2, summary3, summary4);
        row.getChildren().addAll(rankCard, insightsCard);
        this.getChildren().add(row);
    }

    public void refresh() {
        String selectedWindow = timeRangeFilter != null ? timeRangeFilter.getValue() : "ALL TIME (LIFETIME)";
        DashboardMetrics metrics = dashboardService.getMetrics(selectedWindow);

        // Update PieChart
        ObservableList<PieChart.Data> pieData = FXCollections.observableArrayList();
        if (metrics.getSuccessfulExecutions() > 0) {
            pieData.add(new PieChart.Data("Success (" + metrics.getSuccessfulExecutions() + ")", metrics.getSuccessfulExecutions()));
        }
        if (metrics.getFailedExecutions() > 0) {
            pieData.add(new PieChart.Data("Failed (" + metrics.getFailedExecutions() + ")", metrics.getFailedExecutions()));
        }
        if (metrics.getRunningExecutions() > 0) {
            pieData.add(new PieChart.Data("Running (" + metrics.getRunningExecutions() + ")", metrics.getRunningExecutions()));
        }
        pieChart.setData(pieData);

        // Update BarChart
        barChart.getData().clear();
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        Map<String, Integer> catCounts = metrics.getFailureCategoryCounts();
        if (catCounts != null && !catCounts.isEmpty()) {
            for (Map.Entry<String, Integer> entry : catCounts.entrySet()) {
                series.getData().add(new XYChart.Data<>(entry.getKey(), entry.getValue()));
            }
            barChart.getData().add(series);
        }

        // Update Ranking Table
        Map<String, Integer> failMap = dashboardService.getFrequentlyFailingPipelines();
        ObservableList<Map.Entry<String, Integer>> rankList = FXCollections.observableArrayList(failMap.entrySet());
        failureRankingTable.setItems(rankList);

        // Update Dynamic Operational Intelligence Summary
        summary1.setText(String.format("• Delivery Reliability: %.1f%% across %d recorded executions.",
                metrics.getSuccessRate(), metrics.getTotalExecutions()));
        summary2.setText(String.format("• Mean Build & Verification Duration: %.1f seconds.",
                metrics.getAvgDurationSeconds()));

        String topCategory = null;
        int maxCount = 0;
        if (catCounts != null && !catCounts.isEmpty()) {
            for (Map.Entry<String, Integer> entry : catCounts.entrySet()) {
                if (entry.getValue() > maxCount) {
                    maxCount = entry.getValue();
                    topCategory = entry.getKey();
                }
            }
        }

        if (metrics.getFailedExecutions() == 0) {
            summary3.setText("• Primary Root Cause: No pipeline failures detected in recorded executions.");
            summary4.setText("• Optimization Recommendation: Build pipelines are stable. Maintain automated verification coverage.");
        } else if (topCategory != null) {
            switch (topCategory.toUpperCase()) {
                case "COMPILATION_ERROR" -> {
                    summary3.setText("• Primary Root Cause: Java compiler error or symbol resolution failure.");
                    summary4.setText("• Optimization Recommendation: Check source syntax errors, classpath imports, and Java release version.");
                }
                case "ASSERTION_FAILURE" -> {
                    summary3.setText("• Primary Root Cause: Unit or integration test assertion expectations mismatch.");
                    summary4.setText("• Optimization Recommendation: Inspect failed test cases, verify mock responses, and ensure error handling maps correctly.");
                }
                case "TIMEOUT" -> {
                    summary3.setText("• Primary Root Cause: Stage execution exceeded allocated timeout limit.");
                    summary4.setText("• Optimization Recommendation: Increase stage timeout configuration or optimize heavy compilation/testing tasks.");
                }
                case "DATABASE_CONNECTION" -> {
                    summary3.setText("• Primary Root Cause: Database connectivity or authentication credentials failure.");
                    summary4.setText("• Optimization Recommendation: Verify database host/port availability and check connection pool credentials.");
                }
                case "DEPENDENCY_RESOLUTION" -> {
                    summary3.setText("• Primary Root Cause: Missing dependencies or unavailable repository artifacts.");
                    summary4.setText("• Optimization Recommendation: Verify pom.xml dependency versions and ensure repository network access.");
                }
                default -> {
                    summary3.setText("• Primary Root Cause: " + topCategory + " (" + maxCount + " occurrences).");
                    summary4.setText("• Optimization Recommendation: Review recent stderr logs in Log Viewer and inspect failed stage exit codes.");
                }
            }
        } else {
            summary3.setText("• Primary Root Cause: Unclassified failure in pipeline execution stages.");
            summary4.setText("• Optimization Recommendation: Review recent stderr logs in Log Viewer and verify stage commands.");
        }
    }
}
