package com.flowstate;

import com.flowstate.analytics.DistractionAnalyzer;
import com.flowstate.service.FocusSessionService;
import com.flowstate.service.LifecycleManager;
import com.flowstate.storage.FileStorageManager;
import com.flowstate.storage.RepositoryFactory;
import com.flowstate.tracking.ActivityTrackingService;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main JavaFX Desktop Application entry point for FlowState.
 */
public class FlowStateApp extends Application {
    private static final Logger logger = LoggerFactory.getLogger(FlowStateApp.class);

    private Stage primaryStage;

    @Override
    public void start(Stage primaryStage) {
        this.primaryStage = primaryStage;

        logger.info("Initializing FlowState Desktop Application...");

        // 1. Initialize Local File Storage Directory
        FileStorageManager.getInstance().initialize();

        // 2. Wire Domain Services with File-based Repositories
        FocusSessionService sessionService = FocusSessionService.getInstance();
        sessionService.setRepository(RepositoryFactory.getFocusSessionRepository());

        DistractionAnalyzer distractionAnalyzer = new DistractionAnalyzer(RepositoryFactory.getApplicationPreferenceRepository());

        ActivityTrackingService trackingService = ActivityTrackingService.getInstance();
        trackingService.setDistractionAnalyzer(distractionAnalyzer);
        // Start native background tracking
        trackingService.start();

        // 3. Launch Main Application Stage
        launchMainUi();
    }

    private void launchMainUi() {
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
                logger.info("Application window close requested. Shutting down background tasks...");
                shutdownServices();
                Platform.exit();
            });

            primaryStage.show();
            logger.info("FlowState Desktop Application launched successfully.");
        } catch (Exception e) {
            logger.error("Failed to load main layout: {}", e.getMessage(), e);
        }
    }

    @Override
    public void stop() {
        shutdownServices();
    }

    private void shutdownServices() {
        LifecycleManager.getInstance().shutdownAll();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
