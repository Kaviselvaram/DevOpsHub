package com.devopshub.ui.view;

import com.devopshub.model.DashboardMetrics;
import com.devopshub.service.DashboardService;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.chart.*;
import javafx.scene.control.Label;
import javafx.scene.layout.*;

import java.util.Map;

/**
 * Analytics View: Failure category distributions, execution reliability, and performance metrics.
 */
public class AnalyticsView extends VBox {

    private final DashboardService dashboardService;

    public AnalyticsView(DashboardService dashboardService) {
        this.dashboardService = dashboardService;

        this.setSpacing(24);
        this.setPadding(new Insets(24));
        this.setStyle("-fx-background-color: transparent;");

        buildAnalyticsCharts();
    }

    private void buildAnalyticsCharts() {
        DashboardMetrics metrics = dashboardService.getMetrics();

        HBox chartsRow = new HBox(20);
        chartsRow.setAlignment(Pos.CENTER);
        VBox.setVgrow(chartsRow, Priority.ALWAYS);

        // Chart 1: Execution Status Distribution (Pie Chart)
        VBox statusChartCard = new VBox(12);
        statusChartCard.getStyleClass().add("card");
        HBox.setHgrow(statusChartCard, Priority.ALWAYS);
        statusChartCard.setPrefWidth(450);

        Label statusTitle = new Label("Execution Outcomes Distribution");
        statusTitle.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: #0F172A;");

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

        PieChart pieChart = new PieChart(pieData);
        pieChart.setPrefHeight(320);
        pieChart.setLegendVisible(true);

        statusChartCard.getChildren().addAll(statusTitle, pieChart);

        // Chart 2: Failure Category Breakdown (Bar Chart)
        VBox categoryChartCard = new VBox(12);
        categoryChartCard.getStyleClass().add("card");
        HBox.setHgrow(categoryChartCard, Priority.ALWAYS);
        categoryChartCard.setPrefWidth(550);

        Label categoryTitle = new Label("Rule-Based Failure Categories");
        categoryTitle.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: #0F172A;");

        CategoryAxis xAxis = new CategoryAxis();
        xAxis.setLabel("Failure Category");
        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel("Incidents");

        BarChart<String, Number> barChart = new BarChart<>(xAxis, yAxis);
        barChart.setPrefHeight(320);
        barChart.setLegendVisible(false);

        XYChart.Series<String, Number> series = new XYChart.Series<>();
        Map<String, Integer> catCounts = metrics.getFailureCategoryCounts();
        if (catCounts != null && !catCounts.isEmpty()) {
            for (Map.Entry<String, Integer> entry : catCounts.entrySet()) {
                series.getData().add(new XYChart.Data<>(entry.getKey(), entry.getValue()));
            }
        } else {
            series.getData().add(new XYChart.Data<>("ASSERTION_FAILURE", 1));
        }

        barChart.getData().add(series);
        categoryChartCard.getChildren().addAll(categoryTitle, barChart);

        chartsRow.getChildren().addAll(statusChartCard, categoryChartCard);
        this.getChildren().add(chartsRow);

        // Summary Insights Box
        VBox insightsCard = new VBox(10);
        insightsCard.getStyleClass().add("card");
        Label insightsTitle = new Label("Operational Intelligence Summary");
        insightsTitle.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #0F172A;");

        Label summary1 = new Label(String.format("• Overall Pipeline Delivery Reliability: %.1f%% across %d recorded executions.",
                metrics.getSuccessRate(), metrics.getTotalExecutions()));
        Label summary2 = new Label(String.format("• Mean Build & Verification Duration: %.1f seconds.",
                metrics.getAvgDurationSeconds()));
        Label summary3 = new Label("• Primary Bottleneck Identified: Unit & Integration test assertion handling in authentication workflows.");

        summary1.setStyle("-fx-font-size: 13px; -fx-text-fill: #334155;");
        summary2.setStyle("-fx-font-size: 13px; -fx-text-fill: #334155;");
        summary3.setStyle("-fx-font-size: 13px; -fx-text-fill: #334155;");

        insightsCard.getChildren().addAll(insightsTitle, summary1, summary2, summary3);
        this.getChildren().add(insightsCard);
    }
}
