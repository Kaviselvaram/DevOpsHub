package com.devopshub.ui.component;

import com.devopshub.model.ExecutionStatus;
import com.devopshub.model.StageExecutionStatus;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;

/**
 * Reusable Status Badge with SVG Icon.
 * Supports all execution outcomes including BLOCKED and TIMED_OUT.
 */
public class StatusBadge extends HBox {

    public StatusBadge(String status) {
        this.setAlignment(Pos.CENTER);
        this.setSpacing(5);
        this.setStyle("-fx-padding: 3px 8px; -fx-background-radius: 12px;");

        Label label = new Label(status != null ? status : "UNKNOWN");
        label.setStyle("-fx-font-size: 11px; -fx-font-weight: bold;");

        SvgIcon icon;
        String upper = status != null ? status.toUpperCase() : "";

        switch (upper) {
            case "SUCCESS":
            case "ACTIVE":
                this.setStyle(this.getStyle() + "-fx-background-color: #D1FAE5;");
                label.setStyle(label.getStyle() + "-fx-text-fill: #065F46;");
                icon = new SvgIcon(SvgIcon.IconName.CHECK_CIRCLE, 13, Color.web("#059669"));
                break;
            case "FAILED":
                this.setStyle(this.getStyle() + "-fx-background-color: #FFE4E6;");
                label.setStyle(label.getStyle() + "-fx-text-fill: #9F1239;");
                icon = new SvgIcon(SvgIcon.IconName.X_CIRCLE, 13, Color.web("#E11D48"));
                break;
            case "BLOCKED":
                this.setStyle(this.getStyle() + "-fx-background-color: #FEF3C7;");
                label.setStyle(label.getStyle() + "-fx-text-fill: #B45309;");
                icon = new SvgIcon(SvgIcon.IconName.ALERT_TRIANGLE, 13, Color.web("#D97706"));
                break;
            case "TIMED_OUT":
                this.setStyle(this.getStyle() + "-fx-background-color: #FEE2E2;");
                label.setStyle(label.getStyle() + "-fx-text-fill: #991B1B;");
                icon = new SvgIcon(SvgIcon.IconName.CLOCK, 13, Color.web("#DC2626"));
                break;
            case "RUNNING":
                this.setStyle(this.getStyle() + "-fx-background-color: #FEF3C7;");
                label.setStyle(label.getStyle() + "-fx-text-fill: #92400E;");
                icon = new SvgIcon(SvgIcon.IconName.REFRESH, 13, Color.web("#D97706"));
                break;
            case "CANCELLED":
                this.setStyle(this.getStyle() + "-fx-background-color: #F1F5F9;");
                label.setStyle(label.getStyle() + "-fx-text-fill: #64748B;");
                icon = new SvgIcon(SvgIcon.IconName.STOP, 13, Color.web("#64748B"));
                break;
            case "SKIPPED":
                this.setStyle(this.getStyle() + "-fx-background-color: #F3F4F6;");
                label.setStyle(label.getStyle() + "-fx-text-fill: #6B7280;");
                icon = new SvgIcon(SvgIcon.IconName.INFO, 13, Color.web("#6B7280"));
                break;
            default: // QUEUED / INACTIVE
                this.setStyle(this.getStyle() + "-fx-background-color: #F1F5F9;");
                label.setStyle(label.getStyle() + "-fx-text-fill: #475569;");
                icon = new SvgIcon(SvgIcon.IconName.CLOCK, 13, Color.web("#475569"));
                break;
        }

        this.getChildren().addAll(icon, label);
    }

    public static StatusBadge of(ExecutionStatus status) {
        return new StatusBadge(status != null ? status.name() : "QUEUED");
    }

    public static StatusBadge of(StageExecutionStatus status) {
        return new StatusBadge(status != null ? status.name() : "QUEUED");
    }
}
