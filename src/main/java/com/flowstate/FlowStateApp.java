package com.flowstate;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main Application Entry Point and JavaFX Stage Controller.
 */
public class FlowStateApp extends Application {
    private static final Logger logger = LoggerFactory.getLogger(FlowStateApp.class);

    @Override
    public void start(Stage primaryStage) {
        logger.info("Starting FlowState Desktop Application...");

        // Start background native Windows tracking
        ActivityTracker.getInstance().start();

        // Register clean JVM shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("JVM exiting. Stopping background ActivityTracker...");
            ActivityTracker.getInstance().shutdown();
        }));

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main-layout.fxml"));
            Parent root = loader.load();

            Scene scene = new Scene(root, 1180, 760);
            scene.getStylesheets().add(getClass().getResource("/css/flowstate-theme.css").toExternalForm());

            primaryStage.setTitle("FlowState — Smart Distraction & Productivity Analyzer");
            primaryStage.setMinWidth(980);
            primaryStage.setMinHeight(680);
            primaryStage.setScene(scene);

            primaryStage.setOnCloseRequest(event -> {
                ActivityTracker.getInstance().shutdown();
                Platform.exit();
            });

            primaryStage.show();
            logger.info("FlowState GUI successfully launched.");
        } catch (Exception e) {
            logger.error("Failed to load user interface: {}", e.getMessage(), e);
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
