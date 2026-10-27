package com.devopshub.ui;

import com.devopshub.config.DatabaseManager;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.*;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class ModalSnapshotTest {

    @Test
    public void testModalSnapshot() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);

        Runnable task = () -> {
            try {
                DatabaseManager.getInstance();

                StackPane overlay = new StackPane();
                overlay.setStyle("-fx-background-color: rgba(10, 11, 16, 0.85); -fx-alignment: center;");

                VBox form = new VBox(16);
                form.setMaxWidth(560);
                form.setMaxHeight(520);
                form.setPadding(new Insets(28));
                form.setStyle("-fx-background-color: #181926; -fx-border-color: #27283A; -fx-border-radius: 14px; -fx-background-radius: 14px; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.6), 24, 0, 0, 10);");

                Label titleLabel = new Label("Create New Project");
                titleLabel.setStyle("-fx-font-size: 20px; -fx-font-weight: 800; -fx-text-fill: #FFFFFF;");

                Label subtitleLabel = new Label("Initialize a new software repository and CI/CD delivery workspace.");
                subtitleLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #8A8D9E;");

                VBox fields = new VBox(12);

                VBox nameGroup = new VBox(4);
                Label nameLbl = new Label("Project Name");
                nameLbl.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #94A3B8;");
                TextField nameField = new TextField("Payment-Gateway-Core");
                nameField.setStyle("-fx-background-color: #12131D; -fx-text-fill: white; -fx-border-color: #27283A; -fx-border-radius: 6px; -fx-padding: 8px 12px;");
                nameGroup.getChildren().addAll(nameLbl, nameField);

                VBox descGroup = new VBox(4);
                Label descLbl = new Label("Description");
                descLbl.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #94A3B8;");
                TextArea descField = new TextArea("Next-generation PCI-DSS compliant checkout and settlement microservice.");
                descField.setPrefRowCount(3);
                descField.setStyle("-fx-background-color: #12131D; -fx-text-fill: white; -fx-border-color: #27283A; -fx-border-radius: 6px; -fx-padding: 6px;");
                descGroup.getChildren().addAll(descLbl, descField);

                HBox row2 = new HBox(12);
                VBox repoGroup = new VBox(4);
                HBox.setHgrow(repoGroup, Priority.ALWAYS);
                Label repoLbl = new Label("Repository Git URL / Local Path");
                repoLbl.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #94A3B8;");
                TextField repoField = new TextField("https://github.com/vit/payments-core.git");
                repoField.setStyle("-fx-background-color: #12131D; -fx-text-fill: white; -fx-border-color: #27283A; -fx-border-radius: 6px; -fx-padding: 8px 12px;");
                repoGroup.getChildren().addAll(repoLbl, repoField);

                VBox branchGroup = new VBox(4);
                branchGroup.setPrefWidth(120);
                Label branchLbl = new Label("Default Branch");
                branchLbl.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #94A3B8;");
                TextField branchField = new TextField("main");
                branchField.setStyle("-fx-background-color: #12131D; -fx-text-fill: white; -fx-border-color: #27283A; -fx-border-radius: 6px; -fx-padding: 8px 12px;");
                branchGroup.getChildren().addAll(branchLbl, branchField);

                row2.getChildren().addAll(repoGroup, branchGroup);

                VBox typeGroup = new VBox(4);
                Label typeLbl = new Label("Project Architecture");
                typeLbl.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #94A3B8;");
                ComboBox<String> typeCombo = new ComboBox<>();
                typeCombo.getItems().addAll("Java / Spring Boot", "Java 21 Core", "Python / FastAPI", "NodeJS / TypeScript", "Go Microservice");
                typeCombo.setValue("Java / Spring Boot");
                typeCombo.setMaxWidth(Double.MAX_VALUE);
                typeCombo.setStyle("-fx-background-color: #12131D; -fx-border-color: #27283A; -fx-border-radius: 6px;");
                typeGroup.getChildren().addAll(typeLbl, typeCombo);

                fields.getChildren().addAll(nameGroup, descGroup, row2, typeGroup);

                HBox btnBox = new HBox(12);
                btnBox.setAlignment(Pos.CENTER_RIGHT);
                btnBox.setPadding(new Insets(10, 0, 0, 0));

                Button cancelBtn = new Button("Cancel");
                cancelBtn.setStyle("-fx-background-color: #242637; -fx-text-fill: #94A3B8; -fx-font-weight: bold; -fx-padding: 8px 16px; -fx-background-radius: 6px;");

                Button saveBtn = new Button("Create Project");
                saveBtn.setStyle("-fx-background-color: #0284C7; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 8px 18px; -fx-background-radius: 6px;");

                btnBox.getChildren().addAll(cancelBtn, saveBtn);

                form.getChildren().addAll(titleLabel, subtitleLabel, fields, btnBox);
                overlay.getChildren().add(form);

                Scene scene = new Scene(overlay, 900, 600);
                scene.getStylesheets().add(ModalSnapshotTest.class.getResource("/css/devopshub.css").toExternalForm());

                new Thread(() -> {
                    try {
                        Thread.sleep(600);
                        Platform.runLater(() -> {
                            try {
                                WritableImage img = scene.snapshot(null);
                                int w = (int) img.getWidth();
                                int h = (int) img.getHeight();
                                BufferedImage bi = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
                                PixelReader pr = img.getPixelReader();
                                for (int y = 0; y < h; y++) {
                                    for (int x = 0; x < w; x++) {
                                        bi.setRGB(x, y, pr.getArgb(x, y));
                                    }
                                }
                                File out = new File("target/ui_snapshots/16_create_project_modal.png");
                                ImageIO.write(bi, "png", out);
                                System.out.println("Saved modal snapshot: " + out.getAbsolutePath());
                            } catch (Exception e) {
                                e.printStackTrace();
                            } finally {
                                latch.countDown();
                            }
                        });
                    } catch (Exception ex) {
                        latch.countDown();
                    }
                }).start();

            } catch (Exception e) {
                e.printStackTrace();
                latch.countDown();
            }
        };

        try {
            Platform.startup(task);
        } catch (IllegalStateException e) {
            Platform.runLater(task);
        }

        latch.await(15, TimeUnit.SECONDS);
    }
}
