package com.devopshub.ui;

import com.devopshub.config.DatabaseManager;
import com.devopshub.model.Pipeline;
import com.devopshub.model.Project;
import com.devopshub.service.PipelineService;
import com.devopshub.service.ProjectService;
import com.devopshub.ui.view.ExecutionHistoryView;
import com.devopshub.ui.view.MainLayoutView;
import com.devopshub.ui.view.ProjectsView;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Method;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class UiSnapshotGenerator {

    private static void saveSnapshot(Scene scene, String filename) {
        try {
            WritableImage img = scene.snapshot(null);
            int width = (int) img.getWidth();
            int height = (int) img.getHeight();
            BufferedImage bi = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            PixelReader pr = img.getPixelReader();
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    bi.setRGB(x, y, pr.getArgb(x, y));
                }
            }
            File outDir = new File("target/ui_snapshots");
            if (!outDir.exists()) {
                outDir.mkdirs();
            }
            File outFile = new File(outDir, filename);
            ImageIO.write(bi, "png", outFile);
            System.out.println("Saved snapshot: " + outFile.getAbsolutePath() + " (" + width + "x" + height + ")");
        } catch (Exception e) {
            System.err.println("Failed to save snapshot " + filename + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void pause(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {}
    }

    @Test
    public void generateAllUiSnapshots() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);

        Runnable task = () -> {
            try {
                DatabaseManager.getInstance();

                Stage stage = new Stage();
                MainLayoutView mainLayout = new MainLayoutView(stage);
                Scene scene = new Scene(mainLayout, 1280, 800);
                scene.getStylesheets().add(UiSnapshotGenerator.class.getResource("/css/devopshub.css").toExternalForm());
                stage.setScene(scene);
                stage.show();

                ProjectService projectService = new ProjectService();
                PipelineService pipelineService = new PipelineService();

                // Select E-Commerce for rich project detail
                Project ecommerceProject = projectService.getAllProjects().stream()
                        .filter(p -> p.getId().equals("proj-003") || p.getName().equalsIgnoreCase("E-Commerce"))
                        .findFirst()
                        .orElse(projectService.getAllProjects().get(0));

                Pipeline ecommercePipeline = pipelineService.getAllPipelines().stream()
                        .filter(p -> p.getId().equals("pipe-003") || p.getName().toLowerCase().contains("commerce"))
                        .findFirst()
                        .orElse(pipelineService.getAllPipelines().get(0));

                new Thread(() -> {
                    try {
                        pause(600);

                        // 1. Home View
                        Platform.runLater(() -> mainLayout.navigateToHome());
                        pause(500);
                        Platform.runLater(() -> saveSnapshot(scene, "01_home_view.png"));
                        pause(300);

                        // 2. Dashboard View
                        Platform.runLater(() -> mainLayout.navigateToDashboard());
                        pause(500);
                        Platform.runLater(() -> saveSnapshot(scene, "02_dashboard_view.png"));
                        pause(300);

                        // 3. Projects Catalog View
                        Platform.runLater(() -> mainLayout.navigateToProjects());
                        pause(500);
                        Platform.runLater(() -> saveSnapshot(scene, "03_projects_view.png"));
                        pause(300);

                        // 4. Project Detail View (E-Commerce)
                        Platform.runLater(() -> mainLayout.navigateToProjectDetail(ecommerceProject));
                        pause(500);
                        Platform.runLater(() -> saveSnapshot(scene, "04_project_detail_view.png"));
                        pause(300);

                        // 5. Pipelines View
                        Platform.runLater(() -> mainLayout.navigateToPipelines());
                        pause(500);
                        Platform.runLater(() -> saveSnapshot(scene, "05_pipelines_view.png"));
                        pause(300);

                        // 6. Pipeline Builder View (DAG with stages & parallel visualizer)
                        Platform.runLater(() -> mainLayout.navigateToPipelineBuilder(ecommercePipeline));
                        pause(600);
                        Platform.runLater(() -> saveSnapshot(scene, "06_pipeline_builder_dag_view.png"));
                        pause(300);

                        // 7. Pipeline Builder View (New Pipeline Creation)
                        Platform.runLater(() -> mainLayout.navigateToPipelineBuilder(null));
                        pause(500);
                        Platform.runLater(() -> saveSnapshot(scene, "07_pipeline_builder_new_view.png"));
                        pause(300);

                        // 8. Pipeline Detail View
                        Platform.runLater(() -> mainLayout.navigateToPipelineDetail(ecommercePipeline));
                        pause(500);
                        Platform.runLater(() -> saveSnapshot(scene, "08_pipeline_detail_view.png"));
                        pause(300);

                        // 9. Execution Detail View (Failed Build #184 with failure diagnosis & root cause)
                        Platform.runLater(() -> mainLayout.navigateToExecutionDetail("exec-184"));
                        pause(600);
                        Platform.runLater(() -> saveSnapshot(scene, "09_execution_detail_failed_view.png"));
                        pause(300);

                        // 10. Execution Detail View (Success Build #183)
                        Platform.runLater(() -> mainLayout.navigateToExecutionDetail("exec-183"));
                        pause(600);
                        Platform.runLater(() -> saveSnapshot(scene, "10_execution_detail_success_view.png"));
                        pause(300);

                        // 11. Execution History View (Kanban Mode)
                        final ExecutionHistoryView[] execHistoryHolder = new ExecutionHistoryView[1];
                        Platform.runLater(() -> {
                            mainLayout.navigateToExecutions();
                        });
                        pause(500);
                        Platform.runLater(() -> saveSnapshot(scene, "11a_execution_history_kanban_view.png"));
                        pause(300);

                        // 11b. Execution History View (Table Mode)
                        Platform.runLater(() -> {
                            ExecutionHistoryView ehv = new ExecutionHistoryView(mainLayout, new com.devopshub.service.DashboardService());
                            try {
                                Method m = ExecutionHistoryView.class.getDeclaredMethod("setViewMode", boolean.class);
                                m.setAccessible(true);
                                m.invoke(ehv, false);
                                mainLayout.setView("executions", "Execution History", "Audit trail of pipeline runs, durations, and outcomes", ehv);
                            } catch (Exception ex) {
                                ex.printStackTrace();
                            }
                        });
                        pause(500);
                        Platform.runLater(() -> saveSnapshot(scene, "11b_execution_history_table_view.png"));
                        pause(300);

                        // 12. Log Viewer View
                        Platform.runLater(() -> mainLayout.navigateToLogs());
                        pause(500);
                        Platform.runLater(() -> saveSnapshot(scene, "12_log_viewer_view.png"));
                        pause(300);

                        // 13. Analytics View
                        Platform.runLater(() -> mainLayout.navigateToAnalytics());
                        pause(500);
                        Platform.runLater(() -> saveSnapshot(scene, "13_analytics_view.png"));
                        pause(300);

                        // 14. Settings View
                        Platform.runLater(() -> mainLayout.navigateToSettings());
                        pause(500);
                        Platform.runLater(() -> saveSnapshot(scene, "14_settings_view.png"));
                        pause(300);

                        // 15. Alerts Sidebar View with activity stream
                        Platform.runLater(() -> {
                            mainLayout.navigateToHome();
                            if (mainLayout.getRight() == null) {
                                mainLayout.setRight(mainLayout.getAlertsSidebar());
                            }
                        });
                        pause(500);
                        Platform.runLater(() -> saveSnapshot(scene, "15_alerts_sidebar_view.png"));
                        pause(400);

                    } catch (Exception ex) {
                        ex.printStackTrace();
                    } finally {
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

        boolean finished = latch.await(40, TimeUnit.SECONDS);
        assertTrue(finished, "Snapshot generation took too long");
    }
}
