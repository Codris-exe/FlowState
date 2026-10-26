package com.flowstate.ui;

import com.flowstate.analytics.DistractionAnalyzer;
import com.flowstate.model.AppCategory;
import com.flowstate.model.ApplicationPreference;
import com.flowstate.model.FocusSession;
import com.flowstate.repository.*;
import com.flowstate.service.ReportExportService;
import com.flowstate.tracking.ActivityTrackingService;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.stage.FileChooser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Controller for application settings, category preferences, rule import/export, and data management.
 */
public class SettingsController implements Refreshable {
    private static final Logger logger = LoggerFactory.getLogger(SettingsController.class);

    @FXML private TextField txtDailyGoalMinutes;
    @FXML private CheckBox chkBackgroundTracking;

    @FXML private TableView<ApplicationPreference> tablePrefs;
    @FXML private TableColumn<ApplicationPreference, String> colPrefIdentifier;
    @FXML private TableColumn<ApplicationPreference, String> colPrefDisplayName;
    @FXML private TableColumn<ApplicationPreference, String> colPrefCategory;
    @FXML private TableColumn<ApplicationPreference, String> colPrefExcluded;
    @FXML private TableColumn<ApplicationPreference, Void> colPrefActions;

    private final ApplicationPreferenceRepository preferenceRepository;
    private final ActivityRepository activityRepository;
    private final FocusSessionRepository sessionRepository;
    private final ReportExportService exportService = new ReportExportService();
    private final DistractionAnalyzer distractionAnalyzer;

    private final ObservableList<ApplicationPreference> preferencesList = FXCollections.observableArrayList();

    public SettingsController() {
        this(com.flowstate.storage.RepositoryFactory.getApplicationPreferenceRepository(),
             com.flowstate.storage.RepositoryFactory.getActivityRepository(),
             com.flowstate.storage.RepositoryFactory.getFocusSessionRepository());
    }

    public SettingsController(ApplicationPreferenceRepository preferenceRepository,
                              ActivityRepository activityRepository,
                              FocusSessionRepository sessionRepository) {
        this.preferenceRepository = preferenceRepository;
        this.activityRepository = activityRepository;
        this.sessionRepository = sessionRepository;
        this.distractionAnalyzer = new DistractionAnalyzer(preferenceRepository);
    }

    @FXML
    public void initialize() {
        chkBackgroundTracking.setSelected(ActivityTrackingService.getInstance().isTrackingEnabled());
        setupPreferencesTable();
        refreshData();
    }

    private void setupPreferencesTable() {
        colPrefIdentifier.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getApplicationIdentifier()));
        colPrefDisplayName.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getDisplayName()));
        colPrefCategory.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getCategory().name()));
        colPrefExcluded.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().isExcluded() ? "YES" : "NO"));

        colPrefActions.setCellFactory(param -> new TableCell<>() {
            private final Button btnToggleCat = new Button("Cycle Category");
            private final Button btnDelete = new Button("Delete");
            private final HBox pane = new HBox(6, btnToggleCat, btnDelete);

            {
                pane.setAlignment(Pos.CENTER);
                btnToggleCat.getStyleClass().add("btn-secondary");
                btnToggleCat.setStyle("-fx-font-size: 11px; -fx-padding: 3px 8px;");

                btnDelete.getStyleClass().add("btn-danger");
                btnDelete.setStyle("-fx-font-size: 11px; -fx-padding: 3px 8px;");

                btnToggleCat.setOnAction(e -> {
                    ApplicationPreference pref = getTableView().getItems().get(getIndex());
                    cycleCategory(pref);
                });

                btnDelete.setOnAction(e -> {
                    ApplicationPreference pref = getTableView().getItems().get(getIndex());
                    deletePreference(pref);
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : pane);
            }
        });

        tablePrefs.setItems(preferencesList);
        tablePrefs.setPlaceholder(new Label("No custom application rules configured yet."));
    }

    private void cycleCategory(ApplicationPreference pref) {
        AppCategory next;
        switch (pref.getCategory()) {
            case PRODUCTIVE -> next = AppCategory.NEUTRAL;
            case NEUTRAL -> next = AppCategory.DISTRACTING;
            case DISTRACTING -> next = AppCategory.PRODUCTIVE;
            default -> next = AppCategory.PRODUCTIVE;
        }
        pref.setCategory(next);
        CompletableFuture.runAsync(() -> {
            try {
                preferenceRepository.update(pref);
                distractionAnalyzer.refreshCache();
                Platform.runLater(tablePrefs::refresh);
            } catch (Exception e) {
                logger.error("Failed to cycle category: {}", e.getMessage());
            }
        });
    }

    private void deletePreference(ApplicationPreference pref) {
        CompletableFuture.runAsync(() -> {
            try {
                preferenceRepository.deleteByIdentifier(pref.getApplicationIdentifier());
                distractionAnalyzer.refreshCache();
                Platform.runLater(() -> preferencesList.remove(pref));
            } catch (Exception e) {
                logger.error("Failed to delete preference: {}", e.getMessage());
            }
        });
    }

    @Override
    public void refreshData() {
        CompletableFuture.runAsync(() -> {
            try {
                List<ApplicationPreference> all = preferenceRepository.findAll();
                Platform.runLater(() -> {
                    preferencesList.clear();
                    preferencesList.addAll(all);
                });
            } catch (Exception e) {
                logger.error("Failed to load preferences: {}", e.getMessage());
            }
        });
    }

    @FXML
    public void handleSaveGoal(ActionEvent event) {
        try {
            int minutes = Integer.parseInt(txtDailyGoalMinutes.getText().trim());
            if (minutes <= 0 || minutes > 1440) {
                showAlert("Invalid Goal", "Please enter a value between 1 and 1440 minutes.", Alert.AlertType.WARNING);
                return;
            }
            showAlert("Goal Saved", "Daily focus goal set to " + minutes + " minutes.", Alert.AlertType.INFORMATION);
        } catch (NumberFormatException e) {
            showAlert("Invalid Input", "Please enter a valid numeric integer.", Alert.AlertType.ERROR);
        }
    }

    @FXML
    public void handleToggleTrackingPreference(ActionEvent event) {
        boolean enabled = chkBackgroundTracking.isSelected();
        ActivityTrackingService tracker = ActivityTrackingService.getInstance();
        if (enabled) {
            tracker.resumeTracking();
        } else {
            tracker.pauseTracking();
        }
    }

    @FXML
    public void handleExportPrefs(ActionEvent event) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export Application Rules CSV");
        chooser.setInitialFileName("application_rules.csv");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV Files (*.csv)", "*.csv"));

        File file = chooser.showSaveDialog(tablePrefs.getScene().getWindow());
        if (file == null) return;

        CompletableFuture.runAsync(() -> {
            try {
                exportService.exportPreferencesToCsv(file.toPath(), preferencesList, true);
                Platform.runLater(() -> showAlert("Rules Exported", "Exported " + preferencesList.size() + " rules to:\n" + file.getAbsolutePath(), Alert.AlertType.INFORMATION));
            } catch (Exception e) {
                logger.error("Failed to export preferences: {}", e.getMessage());
                Platform.runLater(() -> showAlert("Export Error", e.getMessage(), Alert.AlertType.ERROR));
            }
        });
    }

    @FXML
    public void handleImportPrefs(ActionEvent event) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Import Application Rules CSV");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV Files (*.csv)", "*.csv"));

        File file = chooser.showOpenDialog(tablePrefs.getScene().getWindow());
        if (file == null) return;

        CompletableFuture.runAsync(() -> {
            try {
                List<ApplicationPreference> imported = exportService.importPreferencesFromCsv(file.toPath());
                for (ApplicationPreference p : imported) {
                    preferenceRepository.upsert(p.getApplicationIdentifier(), p.getDisplayName(), p.getCategory(), p.isExcluded());
                }
                distractionAnalyzer.refreshCache();
                Platform.runLater(() -> {
                    refreshData();
                    showAlert("Rules Imported", "Successfully imported " + imported.size() + " application rules.", Alert.AlertType.INFORMATION);
                });
            } catch (Exception e) {
                logger.error("Failed to import preferences: {}", e.getMessage());
                Platform.runLater(() -> showAlert("Import Error", e.getMessage(), Alert.AlertType.ERROR));
            }
        });
    }

    @FXML
    public void handleClearActivities(ActionEvent event) {
        Alert step1 = new Alert(Alert.AlertType.CONFIRMATION, "Clear all recorded application activity intervals? Completed focus session records will be kept.", ButtonType.YES, ButtonType.NO);
        step1.setTitle("Step 1: Clear Activity Logs");
        step1.getDialogPane().getStylesheets().add(getClass().getResource("/css/flowstate-theme.css").toExternalForm());

        Optional<ButtonType> res1 = step1.showAndWait();
        if (res1.isPresent() && res1.get() == ButtonType.YES) {
            Alert step2 = new Alert(Alert.AlertType.CONFIRMATION, "Confirm permanent purge of all activity intervals? This cannot be undone.", ButtonType.YES, ButtonType.NO);
            step2.setTitle("Step 2: Final Confirmation");
            step2.getDialogPane().getStylesheets().add(getClass().getResource("/css/flowstate-theme.css").toExternalForm());

            Optional<ButtonType> res2 = step2.showAndWait();
            if (res2.isPresent() && res2.get() == ButtonType.YES) {
                CompletableFuture.runAsync(() -> {
                    try {
                        long deleted = activityRepository.clearAll();
                        Platform.runLater(() -> showAlert("Activities Cleared", "Successfully deleted " + deleted + " activity intervals.", Alert.AlertType.INFORMATION));
                    } catch (Exception e) {
                        logger.error("Failed to clear activities: {}", e.getMessage());
                        Platform.runLater(() -> showAlert("Purge Error", e.getMessage(), Alert.AlertType.ERROR));
                    }
                });
            }
        }
    }

    @FXML
    public void handlePurgeAllData(ActionEvent event) {
        Alert step1 = new Alert(Alert.AlertType.CONFIRMATION, "DANGER: Delete ALL focus sessions and activity history?", ButtonType.YES, ButtonType.NO);
        step1.setTitle("Step 1: Total Data Reset");
        step1.getDialogPane().getStylesheets().add(getClass().getResource("/css/flowstate-theme.css").toExternalForm());

        Optional<ButtonType> res1 = step1.showAndWait();
        if (res1.isPresent() && res1.get() == ButtonType.YES) {
            Alert step2 = new Alert(Alert.AlertType.CONFIRMATION, "Are you absolutely certain? Every focus session and activity interval will be deleted permanently.", ButtonType.YES, ButtonType.NO);
            step2.setTitle("Step 2: Irreversible Purge Confirmation");
            step2.getDialogPane().getStylesheets().add(getClass().getResource("/css/flowstate-theme.css").toExternalForm());

            Optional<ButtonType> res2 = step2.showAndWait();
            if (res2.isPresent() && res2.get() == ButtonType.YES) {
                CompletableFuture.runAsync(() -> {
                    try {
                        activityRepository.clearAll();
                        List<FocusSession> allSessions = sessionRepository.findAll();
                        for (FocusSession s : allSessions) {
                            sessionRepository.deleteById(s.getId());
                        }
                        Platform.runLater(() -> showAlert("Database Reset", "All sessions and activity logs have been wiped clean.", Alert.AlertType.INFORMATION));
                    } catch (Exception e) {
                        logger.error("Failed to purge all data: {}", e.getMessage());
                        Platform.runLater(() -> showAlert("Reset Error", e.getMessage(), Alert.AlertType.ERROR));
                    }
                });
            }
        }
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
