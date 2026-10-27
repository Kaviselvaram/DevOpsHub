package com.devopshub.ui.view;

import com.devopshub.model.Execution;
import com.devopshub.service.DashboardService;
import com.devopshub.ui.component.SvgIcon;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;

import java.util.List;

/**
 * Right Activity and Notification Sidebar matching the visual reference ("New alerts:").
 * Displays real-time pipeline outcomes, failure alerts, and operational events with user avatars.
 */

public class AlertsSidebarView extends VBox {

    private final MainLayoutView mainLayout;
    private final DashboardService dashboardService;
    private final VBox alertsList;

    public AlertsSidebarView(MainLayoutView mainLayout, DashboardService dashboardService) {
        this.mainLayout = mainLayout;
        this.dashboardService = dashboardService;

        this.getStyleClass().add("alerts-sidebar");
        this.setSpacing(12);

        // Header matching reference: "New alerts:"
        HBox header = new HBox(8);
        header.getStyleClass().add("alerts-header");
        header.setAlignment(Pos.CENTER_LEFT);

        SvgIcon bellIcon = new SvgIcon(SvgIcon.IconName.BELL, 13, Color.web("#8A8D9E"));
        Label headerTitle = new Label("New alerts:");
        headerTitle.getStyleClass().add("alerts-title");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        SvgIcon gearIcon = new SvgIcon(SvgIcon.IconName.SETTINGS, 12, Color.web("#616478"));
        gearIcon.setStyle("-fx-cursor: hand;");
        gearIcon.setOnMouseClicked(e -> mainLayout.navigateToSettings());
        header.getChildren().addAll(bellIcon, headerTitle, spacer, gearIcon);

        // Alerts List Container inside ScrollPane
        alertsList = new VBox(8);
        alertsList.setStyle("-fx-background-color: transparent;");

        ScrollPane scroll = new ScrollPane(alertsList);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background-color: transparent; -fx-background: transparent; -fx-padding: 0;");
        VBox.setVgrow(scroll, Priority.ALWAYS);

        this.getChildren().addAll(header, scroll);

        refreshAlerts();
    }

    public void refreshAlerts() {
        Platform.runLater(() -> {
            alertsList.getChildren().clear();

            List<Execution> recentExecutions = dashboardService.getRecentExecutions(8);
            if (recentExecutions == null || recentExecutions.isEmpty()) {
                Label emptyAlert = new Label("No recent execution alerts.\nTrigger a pipeline run to observe live activity.");
                emptyAlert.setWrapText(true);
                emptyAlert.setStyle("-fx-font-size: 11px; -fx-text-fill: #616478; -fx-padding: 16px 8px;");
                alertsList.getChildren().add(emptyAlert);
                return;
            }

            for (Execution exec : recentExecutions) {
                String title = exec.getPipelineName() != null ? exec.getPipelineName() : "Build #" + exec.getExecutionNumber();
                String status = exec.getStatus() != null ? exec.getStatus().name() : "QUEUED";
                boolean isSuccess = "SUCCESS".equalsIgnoreCase(status);
                String color = isSuccess ? "#10B981" : ("FAILED".equalsIgnoreCase(status) ? "#F43F5E" : "#38BDF8");

                String initials = getInitials(title);
                String timeAgo = formatTimeAgo(exec.getStartedAt());

                HBox alertCard = createAlertItem(title, status + " (" + (exec.getDurationSeconds() > 0 ? exec.getDurationSeconds() + "s" : "active") + ")",
                        timeAgo, color, isSuccess);
                alertCard.setOnMouseClicked(e -> mainLayout.navigateToExecutionDetail(exec.getId()));
                alertsList.getChildren().add(alertCard);
            }
        });
    }

    private HBox createAlertItem(String name, String detail, String time, String avatarColor, boolean success) {
        HBox row = new HBox(10);
        row.getStyleClass().add("alert-row");
        row.setAlignment(Pos.CENTER_LEFT);

        // Circular avatar
        StackPane avatar = new StackPane();
        avatar.getStyleClass().add("alert-avatar");
        avatar.setStyle(String.format("-fx-background-color: %s22; -fx-border-color: %s; -fx-border-width: 1px; -fx-border-radius: 14px; -fx-background-radius: 14px;", avatarColor, avatarColor));

        Label avatarLabel = new Label(getInitials(name));
        avatarLabel.setStyle(String.format("-fx-font-size: 10px; -fx-font-weight: 800; -fx-text-fill: %s;", avatarColor));
        avatar.getChildren().add(avatarLabel);

        // Text details
        VBox textBox = new VBox(2);
        HBox.setHgrow(textBox, Priority.ALWAYS);

        Label nameLabel = new Label(name);
        nameLabel.getStyleClass().add("alert-name");

        HBox metaBox = new HBox(6);
        metaBox.setAlignment(Pos.CENTER_LEFT);

        Label detailLabel = new Label(detail);
        detailLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: #8A8D9E;");

        Label timeLabel = new Label("• " + time);
        timeLabel.getStyleClass().add("alert-time");

        metaBox.getChildren().addAll(detailLabel, timeLabel);
        textBox.getChildren().addAll(nameLabel, metaBox);

        // Status dot
        Region dot = new Region();
        dot.setStyle(String.format("-fx-background-color: %s; -fx-background-radius: 3px; -fx-min-width: 6px; -fx-min-height: 6px; -fx-max-width: 6px; -fx-max-height: 6px;",
                success ? "#10B981" : "#F43F5E"));

        row.getChildren().addAll(avatar, textBox, dot);
        return row;
    }

    private String getInitials(String str) {
        if (str == null || str.trim().isEmpty()) return "DH";
        String[] parts = str.trim().split("\\s+");
        if (parts.length >= 2) {
            return ("" + parts[0].charAt(0) + parts[1].charAt(0)).toUpperCase();
        }
        return str.length() >= 2 ? str.substring(0, 2).toUpperCase() : str.toUpperCase();
    }

    private String formatTimeAgo(java.sql.Timestamp ts) {
        if (ts == null) return "just now";
        long diff = System.currentTimeMillis() - ts.getTime();
        long mins = diff / (60 * 1000);
        if (mins < 1) return "just now";
        if (mins < 60) return mins + " min ago";
        long hours = mins / 60;
        if (hours < 24) return hours + "h ago";
        return (hours / 24) + "d ago";
    }
}
