package com.devopshub.ui.component;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;

/**
 * Reusable Metric KPI Card for Dashboard and Analytics.
 */
public class MetricCard extends VBox {

    private final Label valueLabel;
    private final Label subtitleLabel;

    public MetricCard(String title, String value, String subtitle, SvgIcon.IconName iconName, Color accentColor) {
        this.getStyleClass().add("card");
        this.setSpacing(8);
        this.setPadding(new Insets(16));
        this.setPrefWidth(220);
        this.setMinWidth(180);
        VBox.setVgrow(this, Priority.ALWAYS);

        // Header row: Title + Icon Bubble
        HBox topRow = new HBox();
        topRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(topRow, Priority.ALWAYS);

        Label titleLabel = new Label(title.toUpperCase());
        titleLabel.getStyleClass().add("metric-card-title");
        HBox.setHgrow(titleLabel, Priority.ALWAYS);

        StackPane iconBubble = new StackPane();
        iconBubble.setPrefSize(32, 32);
        iconBubble.setMaxSize(32, 32);
        String hexColor = toHex(accentColor);
        iconBubble.setStyle(String.format("-fx-background-color: %s22; -fx-background-radius: 6px;", hexColor));

        SvgIcon icon = new SvgIcon(iconName, 16, accentColor);
        iconBubble.getChildren().add(icon);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        topRow.getChildren().addAll(titleLabel, spacer, iconBubble);

        // Value Label
        valueLabel = new Label(value);
        valueLabel.getStyleClass().add("metric-card-value");

        // Subtitle Label
        subtitleLabel = new Label(subtitle);
        subtitleLabel.getStyleClass().add("metric-card-subtitle");

        this.getChildren().addAll(topRow, valueLabel, subtitleLabel);
    }

    public void setValue(String value) {
        this.valueLabel.setText(value);
    }

    public void setSubtitle(String subtitle) {
        this.subtitleLabel.setText(subtitle);
    }

    private String toHex(Color color) {
        return String.format("#%02X%02X%02X",
                (int) (color.getRed() * 255),
                (int) (color.getGreen() * 255),
                (int) (color.getBlue() * 255));
    }
}
