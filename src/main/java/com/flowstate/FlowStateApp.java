package com.flowstate;

import com.flowstate.analytics.DistractionAnalyzer;
import com.flowstate.config.DatabaseManager;
import com.flowstate.repository.ActivityRepositoryJDBC;
import com.flowstate.repository.ApplicationPreferenceRepositoryJDBC;
import com.flowstate.repository.FocusSessionRepositoryJDBC;
import com.flowstate.service.FocusSessionService;
import com.flowstate.tracking.ActivityTrackingService;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.VBox;
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

        // 1. Initialize Database
        DatabaseManager dbManager = DatabaseManager.getInstance();
        boolean dbHealthy = dbManager.initialize();

        if (!dbHealthy) {
            logger.warn("Database connection unavailable on startup. Presenting recovery screen.");
            showDatabaseRecoveryScreen(dbManager);
            return;
        }

        // 2. Wire Domain Services with JDBC Repositories
        FocusSessionService sessionService = FocusSessionService.getInstance();
        sessionService.setRepository(new FocusSessionRepositoryJDBC(dbManager));

        DistractionAnalyzer distractionAnalyzer = new DistractionAnalyzer(new ApplicationPreferenceRepositoryJDBC(dbManager));

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

    private void showDatabaseRecoveryScreen(DatabaseManager dbManager) {
        VBox root = new VBox(16);
        root.setAlignment(Pos.CENTER);
        root.setPadding(new Insets(32));
        root.getStyleClass().add("content-area");

        Label title = new Label("⚠️  Database Connection Error");
        title.getStyleClass().add("view-title");
        title.setStyle("-fx-text-fill: #EF4444;");

        Label desc = new Label("FlowState could not establish a connection to PostgreSQL.\nPlease review the diagnostics and recommended recovery steps below:");
        desc.getStyleClass().add("empty-state-text");

        TextArea diagnosticsArea = new TextArea(dbManager.getDiagnostics());
        diagnosticsArea.setEditable(false);
        diagnosticsArea.setPrefHeight(260);
        diagnosticsArea.setStyle("-fx-font-family: monospace; -fx-font-size: 12px;");

        Button btnRetry = new Button("🔄  Retry Connection");
        btnRetry.getStyleClass().add("btn-primary");
        btnRetry.setOnAction(e -> {
            if (dbManager.initialize()) {
                start(primaryStage);
            } else {
                diagnosticsArea.setText(dbManager.getDiagnostics());
            }
        });

        Button btnExit = new Button("Exit Application");
        btnExit.getStyleClass().add("btn-secondary");
        btnExit.setOnAction(e -> Platform.exit());

        javafx.scene.layout.HBox buttons = new javafx.scene.layout.HBox(12, btnRetry, btnExit);
        buttons.setAlignment(Pos.CENTER);

        root.getChildren().addAll(title, desc, diagnosticsArea, buttons);

        Scene scene = new Scene(root, 720, 520);
        scene.getStylesheets().add(getClass().getResource("/css/flowstate-theme.css").toExternalForm());

        primaryStage.setTitle("FlowState — Database Setup Required");
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    @Override
    public void stop() {
        shutdownServices();
    }

    private void shutdownServices() {
        try {
            ActivityTrackingService.getInstance().shutdown();
            FocusSessionService.getInstance().shutdown();
            DatabaseManager.getInstance().close();
            logger.info("FlowState cleanly shut down all services.");
        } catch (Exception e) {
            logger.error("Error during application shutdown: {}", e.getMessage());
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
