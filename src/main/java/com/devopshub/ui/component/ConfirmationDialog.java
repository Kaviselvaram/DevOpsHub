package com.devopshub.ui.component;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Standard confirmation dialog for destructive actions.
 */
public class ConfirmationDialog {

    public static boolean show(Stage owner, String title, String message, String confirmButtonText) {
        AtomicBoolean result = new AtomicBoolean(false);

        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        if (owner != null) {
            dialog.initOwner(owner);
        }
        dialog.setTitle(title);
        dialog.setResizable(false);

        VBox root = new VBox(16);
        root.setPadding(new Insets(24));
        root.setStyle("-fx-background-color: #FFFFFF;");

        HBox header = new HBox(12);
        header.setAlignment(Pos.CENTER_LEFT);

        SvgIcon warningIcon = new SvgIcon(SvgIcon.IconName.ALERT_TRIANGLE, 24, Color.web("#E11D48"));
        Label titleLabel = new Label(title);
        titleLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #0F172A;");
        header.getChildren().addAll(warningIcon, titleLabel);

        Label msgLabel = new Label(message);
        msgLabel.setWrapText(true);
        msgLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: #475569;");

        HBox buttons = new HBox(12);
        buttons.setAlignment(Pos.CENTER_RIGHT);

        Button cancelBtn = new Button("Cancel");
        cancelBtn.getStyleClass().add("btn-secondary");
        cancelBtn.setOnAction(e -> dialog.close());

        Button confirmBtn = new Button(confirmButtonText != null ? confirmButtonText : "Confirm");
        confirmBtn.getStyleClass().add("btn-danger");
        confirmBtn.setOnAction(e -> {
            result.set(true);
            dialog.close();
        });

        buttons.getChildren().addAll(cancelBtn, confirmBtn);
        root.getChildren().addAll(header, msgLabel, buttons);

        Scene scene = new Scene(root, 440, 180);
        scene.getStylesheets().add(ConfirmationDialog.class.getResource("/css/devopshub.css").toExternalForm());
        dialog.setScene(scene);
        dialog.showAndWait();

        return result.get();
    }
}
