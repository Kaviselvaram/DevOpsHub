package com.devopshub.ui.component;

import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.animation.SequentialTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.util.Duration;

/**
 * Non-intrusive floating toast notifications.
 */
public class ToastNotification {

    public enum ToastType {
        SUCCESS,
        ERROR,
        INFO
    }

    public static void show(StackPane container, String message, ToastType type) {
        if (container == null) return;

        HBox toast = new HBox(10);
        toast.setAlignment(Pos.CENTER_LEFT);
        toast.setPadding(new Insets(10, 16, 10, 16));
        toast.setMaxWidth(400);

        SvgIcon icon;
        String bgStyle;

        switch (type) {
            case SUCCESS:
                bgStyle = "-fx-background-color: #065F46; -fx-background-radius: 8px;";
                icon = new SvgIcon(SvgIcon.IconName.CHECK_CIRCLE, 16, Color.WHITE);
                break;
            case ERROR:
                bgStyle = "-fx-background-color: #9F1239; -fx-background-radius: 8px;";
                icon = new SvgIcon(SvgIcon.IconName.ALERT_TRIANGLE, 16, Color.WHITE);
                break;
            default:
                bgStyle = "-fx-background-color: #1E293B; -fx-background-radius: 8px;";
                icon = new SvgIcon(SvgIcon.IconName.INFO, 16, Color.WHITE);
                break;
        }

        toast.setStyle(bgStyle + "-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.25), 8, 0, 0, 4);");

        Label label = new Label(message);
        label.setStyle("-fx-text-fill: #FFFFFF; -fx-font-weight: bold; -fx-font-size: 13px;");
        toast.getChildren().addAll(icon, label);

        StackPane.setAlignment(toast, Pos.TOP_RIGHT);
        StackPane.setMargin(toast, new Insets(20, 24, 0, 0));
        toast.setOpacity(0.0);

        container.getChildren().add(toast);

        FadeTransition fadeIn = new FadeTransition(Duration.millis(250), toast);
        fadeIn.setFromValue(0.0);
        fadeIn.setToValue(1.0);

        PauseTransition pause = new PauseTransition(Duration.millis(3500));

        FadeTransition fadeOut = new FadeTransition(Duration.millis(300), toast);
        fadeOut.setFromValue(1.0);
        fadeOut.setToValue(0.0);

        SequentialTransition seq = new SequentialTransition(fadeIn, pause, fadeOut);
        seq.setOnFinished(e -> container.getChildren().remove(toast));
        seq.play();
    }
}
