package com.devopshub.ui;

import com.devopshub.config.DatabaseManager;
import com.devopshub.ui.view.MainLayoutView;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

public class VisualAuditAutomationRunner extends Application {
    private static final Logger logger = LoggerFactory.getLogger(VisualAuditAutomationRunner.class);

    private static final String SCREENSHOTS_DIR = "docs/audit/screenshots";
    private static final String ARTIFACTS_DIR = "/Users/kaviselvaramkathirvel/.gemini/antigravity-ide/brain/e79984ad-ab04-44b1-bd7e-b651636876bd";

    @Override
    public void start(Stage primaryStage) {
        logger.info("Starting VisualAuditAutomationRunner...");

        DatabaseManager.getInstance();

        MainLayoutView root = new MainLayoutView(primaryStage);
        Scene scene = new Scene(root, 1280, 800);
        scene.getStylesheets().add(getClass().getResource("/css/devopshub.css").toExternalForm());

        primaryStage.setTitle("DevOpsHub — Live Visual Audit & Functional Walkthrough");
        primaryStage.setScene(scene);
        primaryStage.show();

        new Thread(() -> {
            try {
                Thread.sleep(1500); // Wait for initial window layout

                // 1. Home View
                captureScreen(primaryStage, "01_home_view.png");

                // 2. Dashboard View
                Platform.runLater(root::navigateToDashboard);
                Thread.sleep(800);
                captureScreen(primaryStage, "02_dashboard_view.png");

                // 3. Projects View
                Platform.runLater(root::navigateToProjects);
                Thread.sleep(800);
                captureScreen(primaryStage, "03_projects_view.png");

                // 4. Pipelines View
                Platform.runLater(root::navigateToPipelines);
                Thread.sleep(800);
                captureScreen(primaryStage, "04_pipelines_view.png");

                // 5. Pipeline Builder View
                Platform.runLater(() -> root.navigateToPipelineBuilder(null));
                Thread.sleep(800);
                captureScreen(primaryStage, "05_pipeline_builder_view.png");

                // 6. Execution History View
                Platform.runLater(root::navigateToExecutions);
                Thread.sleep(800);
                captureScreen(primaryStage, "06_execution_history_view.png");

                // 7. Log Viewer View
                Platform.runLater(root::navigateToLogs);
                Thread.sleep(800);
                captureScreen(primaryStage, "07_log_viewer_view.png");

                // 8. Analytics View
                Platform.runLater(root::navigateToAnalytics);
                Thread.sleep(800);
                captureScreen(primaryStage, "08_analytics_view.png");

                // 9. Settings View
                Platform.runLater(root::navigateToSettings);
                Thread.sleep(800);
                captureScreen(primaryStage, "09_settings_view.png");

                logger.info("Visual Audit Tour completed successfully. Closing application.");
                Platform.runLater(primaryStage::close);
            } catch (Exception e) {
                logger.error("Error during visual audit automation: {}", e.getMessage(), e);
                Platform.runLater(primaryStage::close);
            }
        }).start();
    }

    private void captureScreen(Stage stage, String fileName) {
        try {
            java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
            Platform.runLater(() -> {
                try {
                    javafx.scene.image.WritableImage img = stage.getScene().snapshot(null);
                    int w = (int) img.getWidth();
                    int h = (int) img.getHeight();
                    java.awt.image.BufferedImage bi = new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_ARGB);
                    javafx.scene.image.PixelReader pr = img.getPixelReader();
                    for (int y = 0; y < h; y++) {
                        for (int x = 0; x < w; x++) {
                            bi.setRGB(x, y, pr.getArgb(x, y));
                        }
                    }
                    File targetFile = new File(SCREENSHOTS_DIR, fileName);
                    targetFile.getParentFile().mkdirs();
                    javax.imageio.ImageIO.write(bi, "png", targetFile);

                    File artifactFile = new File(ARTIFACTS_DIR, fileName);
                    if (artifactFile.getParentFile().exists()) {
                        javax.imageio.ImageIO.write(bi, "png", artifactFile);
                    }
                    logger.info("Captured un-occluded JavaFX snapshot: {}", targetFile.getAbsolutePath());
                } catch (Exception e) {
                    logger.error("Failed to snapshot scene for {}: {}", fileName, e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
            latch.await(5, java.util.concurrent.TimeUnit.SECONDS);
        } catch (Exception e) {
            logger.error("Error during snapshot {}: {}", fileName, e.getMessage());
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
