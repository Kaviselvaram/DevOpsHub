package com.devopshub.ui.component;

import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

/**
 * Reusable Modern Empty State Card with SVG Icon, Title, Description, and Action CTA.
 */
public class EmptyStateView extends VBox {

    public EmptyStateView(SvgIcon.IconName iconName, String title, String description, String actionButtonText, Runnable onAction) {
        this.setAlignment(Pos.CENTER);
        this.setSpacing(12);
        this.getStyleClass().add("empty-state-card");

        SvgIcon icon = new SvgIcon(iconName, 42, Color.web("#94A3B8"));

        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("empty-state-title");

        Label descLabel = new Label(description);
        descLabel.getStyleClass().add("empty-state-desc");
        descLabel.setWrapText(true);

        this.getChildren().addAll(icon, titleLabel, descLabel);

        if (actionButtonText != null && onAction != null) {
            Button actionBtn = new Button(actionButtonText);
            actionBtn.getStyleClass().add("btn-primary");
            actionBtn.setOnAction(e -> onAction.run());
            this.getChildren().add(actionBtn);
        }
    }

    public EmptyStateView(SvgIcon.IconName iconName, String title, String description) {
        this(iconName, title, description, null, null);
    }
}
