package com.flowstate;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class FlowStateApp extends Application {

    @Override
    public void start(Stage primaryStage) {
        System.out.println("[Focus Forge] Starting Desktop Application...");

        ActivityTracker.getInstance().start();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("[Focus Forge] JVM exiting. Stopping background ActivityTracker...");
            ActivityTracker.getInstance().shutdown();
        }));

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main-layout.fxml"));
            Parent root = loader.load();

            Scene scene = new Scene(root, 1180, 760);
            scene.getStylesheets().add(getClass().getResource("/css/flowstate-theme.css").toExternalForm());

            primaryStage.setTitle("Focus Forge — Smart Distraction & Productivity Analyzer");
            primaryStage.setMinWidth(980);
            primaryStage.setMinHeight(680);
            primaryStage.setScene(scene);

            primaryStage.setOnCloseRequest(event -> {
                ActivityTracker.getInstance().shutdown();
                Platform.exit();
            });

            primaryStage.show();
            System.out.println("[Focus Forge] GUI successfully launched.");
        } catch (Exception e) {
            System.err.println("[Focus Forge] Failed to load user interface: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
