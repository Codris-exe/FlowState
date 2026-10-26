package com.flowstate.ui;

import com.flowstate.model.ApplicationActivity;
import com.flowstate.model.FocusSession;
import com.flowstate.repository.ActivityRepository;
import com.flowstate.repository.ActivityRepositoryJDBC;
import com.flowstate.repository.FocusSessionRepository;
import com.flowstate.repository.FocusSessionRepositoryJDBC;
import com.flowstate.service.ReportExportService;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.stage.FileChooser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Controller for the Reports & Data Export view.
 */
public class ReportsController implements Refreshable {
    private static final Logger logger = LoggerFactory.getLogger(ReportsController.class);

    @FXML private Label lblExportStatus;

    private final ReportExportService exportService = new ReportExportService();
    private final FocusSessionRepository sessionRepository = new FocusSessionRepositoryJDBC();
    private final ActivityRepository activityRepository = new ActivityRepositoryJDBC();

    @Override
    public void refreshData() {
        lblExportStatus.setText("");
    }

    @FXML
    public void handleExportSessions(ActionEvent event) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save Focus Sessions CSV");
        chooser.setInitialFileName("flowstate_sessions.csv");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV Files (*.csv)", "*.csv"));

        File selectedFile = chooser.showSaveDialog(lblExportStatus.getScene().getWindow());
        if (selectedFile == null) return;

        Path targetPath = selectedFile.toPath();
        boolean overwrite = checkOverwrite(targetPath);
        if (!overwrite && Files.exists(targetPath)) {
            return;
        }

        lblExportStatus.setStyle("-fx-text-fill: #3B82F6;");
        lblExportStatus.setText("Exporting sessions to CSV...");

        CompletableFuture.runAsync(() -> {
            try {
                List<FocusSession> sessions = sessionRepository.findAll();
                exportService.exportSessionsToCsv(targetPath, sessions, true);
                Platform.runLater(() -> {
                    lblExportStatus.setStyle("-fx-text-fill: #10B981;");
                    lblExportStatus.setText("✔ Successfully exported " + sessions.size() + " focus sessions to: " + targetPath.getFileName());
                    showAlert("Export Complete", "Exported " + sessions.size() + " focus sessions to:\n" + targetPath.toAbsolutePath(), Alert.AlertType.INFORMATION);
                });
            } catch (Exception e) {
                logger.error("Failed to export sessions: {}", e.getMessage(), e);
                Platform.runLater(() -> {
                    lblExportStatus.setStyle("-fx-text-fill: #EF4444;");
                    lblExportStatus.setText("✖ Export failed: " + e.getMessage());
                    showAlert("Export Error", e.getMessage(), Alert.AlertType.ERROR);
                });
            }
        });
    }

    @FXML
    public void handleExportActivities(ActionEvent event) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save Application Activity CSV");
        chooser.setInitialFileName("flowstate_activities.csv");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV Files (*.csv)", "*.csv"));

        File selectedFile = chooser.showSaveDialog(lblExportStatus.getScene().getWindow());
        if (selectedFile == null) return;

        Path targetPath = selectedFile.toPath();
        boolean overwrite = checkOverwrite(targetPath);
        if (!overwrite && Files.exists(targetPath)) {
            return;
        }

        lblExportStatus.setStyle("-fx-text-fill: #3B82F6;");
        lblExportStatus.setText("Exporting application activity to CSV...");

        CompletableFuture.runAsync(() -> {
            try {
                List<ApplicationActivity> activities = activityRepository.findRecent(10000);
                exportService.exportActivitiesToCsv(targetPath, activities, true);
                Platform.runLater(() -> {
                    lblExportStatus.setStyle("-fx-text-fill: #10B981;");
                    lblExportStatus.setText("✔ Successfully exported " + activities.size() + " activity logs to: " + targetPath.getFileName());
                    showAlert("Export Complete", "Exported " + activities.size() + " activity records to:\n" + targetPath.toAbsolutePath(), Alert.AlertType.INFORMATION);
                });
            } catch (Exception e) {
                logger.error("Failed to export activities: {}", e.getMessage(), e);
                Platform.runLater(() -> {
                    lblExportStatus.setStyle("-fx-text-fill: #EF4444;");
                    lblExportStatus.setText("✖ Export failed: " + e.getMessage());
                    showAlert("Export Error", e.getMessage(), Alert.AlertType.ERROR);
                });
            }
        });
    }

    private boolean checkOverwrite(Path path) {
        if (!Files.exists(path)) return true;

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "The file '" + path.getFileName() + "' already exists.\nDo you want to overwrite it?",
                ButtonType.YES, ButtonType.NO);
        confirm.setTitle("Confirm File Overwrite");
        try {
            confirm.getDialogPane().getStylesheets().add(getClass().getResource("/css/flowstate-theme.css").toExternalForm());
        } catch (Exception ignored) {}

        Optional<ButtonType> res = confirm.showAndWait();
        return res.isPresent() && res.get() == ButtonType.YES;
    }

    private void showAlert(String title, String message, Alert.AlertType type) {
        Alert alert = new Alert(type, message, ButtonType.OK);
        alert.setTitle(title);
        alert.setHeaderText(null);
        try {
            alert.getDialogPane().getStylesheets().add(getClass().getResource("/css/flowstate-theme.css").toExternalForm());
        } catch (Exception ignored) {}
        alert.showAndWait();
    }
}
