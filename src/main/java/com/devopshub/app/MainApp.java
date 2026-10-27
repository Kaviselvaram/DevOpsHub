package com.devopshub.app;

import com.devopshub.config.DatabaseManager;
import com.devopshub.execution.ExecutionThreadPool;
import com.devopshub.ui.view.MainLayoutView;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main JavaFX Application Entry Point.
 */
public class MainApp extends Application {
    private static final Logger logger = LoggerFactory.getLogger(MainApp.class);

    @Override
    public void start(Stage primaryStage) {
        logger.info("Starting DevOpsHub Desktop Application...");

        // Initialize Database Connection Pool & Auto-migrations
        DatabaseManager.getInstance();

        // Initialize and start background HTTP Webhook Server
        try {
            com.devopshub.webhook.WebhookServerService.getInstance().startServer();
        } catch (Exception e) {
            logger.warn("Could not start WebhookServerService: {}", e.getMessage());
        }

        MainLayoutView root = new MainLayoutView(primaryStage);

        Scene scene = new Scene(root, 1280, 800);
        scene.getStylesheets().add(getClass().getResource("/css/devopshub.css").toExternalForm());

        primaryStage.setTitle("DevOpsHub — Intelligent CI/CD Management Platform");
        primaryStage.setMinWidth(1024);
        primaryStage.setMinHeight(700);
        primaryStage.setScene(scene);

        // Graceful shutdown on window close
        primaryStage.setOnCloseRequest(e -> {
            logger.info("Application closing. Performing graceful shutdown of executors and connection pools...");
            com.devopshub.webhook.WebhookServerService.getInstance().stopServer();
            com.devopshub.scheduler.PipelineSchedulerService.getInstance().shutdown();
            ExecutionThreadPool.getInstance().shutdown();
            DatabaseManager.getInstance().close();
        });

        primaryStage.show();
        logger.info("DevOpsHub Desktop Application launched successfully.");
    }

    public static void main(String[] args) {
        launch(args);
    }
}
