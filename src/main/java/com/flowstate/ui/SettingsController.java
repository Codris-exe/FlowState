package com.flowstate.ui;

import com.flowstate.analytics.DistractionAnalyzer;
import com.flowstate.model.AppCategory;
import com.flowstate.model.ApplicationPreference;
import com.flowstate.model.FocusSession;
import com.flowstate.repository.ActivityRepository;
import com.flowstate.repository.ApplicationPreferenceRepository;
import com.flowstate.repository.FocusSessionRepository;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Controller for application settings, category preferences, and data management.
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
        setupPreferencesTable();
        refreshData();
    }

    private void setupPreferencesTable() {
        colPrefIdentifier.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getApplicationIdentifier()));
        colPrefDisplayName.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().getDisplayName() != null ? data.getValue().getDisplayName() : data.getValue().getApplicationIdentifier()
        ));
        colPrefCategory.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getCategory().name()));
        colPrefExcluded.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().isExcluded() ? "Yes" : "No"));

        colPrefActions.setCellFactory(param -> new TableCell<>() {
            private final Button btnDelete = new Button("🗑");

            {
                btnDelete.setStyle("-fx-background-color: transparent; -fx-text-fill: #EF4444; -fx-cursor: hand; -fx-font-size: 14px;");
                btnDelete.setOnAction(e -> {
                    ApplicationPreference pref = getTableView().getItems().get(getIndex());
                    handleDeletePreference(pref);
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    HBox pane = new HBox(btnDelete);
                    pane.setAlignment(Pos.CENTER);
                    setGraphic(pane);
                }
            }
        });

        tablePrefs.setItems(preferencesList);
        tablePrefs.setPlaceholder(new Label("No custom application rules configured yet."));
    }

    private void handleDeletePreference(ApplicationPreference pref) {
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
    public void handleSaveDailyGoal(ActionEvent event) {
        String input = txtDailyGoalMinutes.getText().trim();
        try {
            int minutes = Integer.parseInt(input);
            if (minutes <= 0) throw new NumberFormatException();
            showAlert("Goal Updated", "Daily focus goal set to " + minutes + " minutes.", Alert.AlertType.INFORMATION);
        } catch (NumberFormatException e) {
            showAlert("Invalid Input", "Please enter a positive whole number for minutes.", Alert.AlertType.WARNING);
        }
    }

    @FXML
    public void handleToggleBackgroundTracking(ActionEvent event) {
        boolean enabled = chkBackgroundTracking.isSelected();
        if (enabled) {
            ActivityTrackingService.getInstance().resumeTracking();
        } else {
            ActivityTrackingService.getInstance().pauseTracking();
        }
    }

    @FXML
    public void handleAddRule(ActionEvent event) {
        Dialog<ApplicationPreference> dialog = new Dialog<>();
        dialog.setTitle("Add Application Rule");
        dialog.setHeaderText("Categorize an Application or Process");
        try {
            dialog.getDialogPane().getStylesheets().add(getClass().getResource("/css/flowstate-theme.css").toExternalForm());
        } catch (Exception ignored) {}

        ButtonType btnSaveType = new ButtonType("Save Rule", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(btnSaveType, ButtonType.CANCEL);

        javafx.scene.layout.GridPane grid = new javafx.scene.layout.GridPane();
        grid.setHgap(10);
        grid.setVgap(12);
        grid.setPadding(new javafx.geometry.Insets(20, 20, 10, 10));

        TextField txtId = new TextField();
        txtId.setPromptText("e.g. idea64.exe, discord.exe");
        TextField txtName = new TextField();
        txtName.setPromptText("e.g. IntelliJ IDEA, Discord");

        ComboBox<AppCategory> cbCat = new ComboBox<>(FXCollections.observableArrayList(AppCategory.values()));
        cbCat.setValue(AppCategory.PRODUCTIVE);

        CheckBox chkEx = new CheckBox("Exclude from Tracking (Never Saved)");

        grid.add(new Label("Process Name:"), 0, 0);
        grid.add(txtId, 1, 0);
        grid.add(new Label("Display Name:"), 0, 1);
        grid.add(txtName, 1, 1);
        grid.add(new Label("Category:"), 0, 2);
        grid.add(cbCat, 1, 2);
        grid.add(chkEx, 1, 3);

        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == btnSaveType && !txtId.getText().trim().isEmpty()) {
                return new ApplicationPreference(
                        null,
                        txtId.getText().trim().toLowerCase(),
                        txtName.getText().trim().isEmpty() ? txtId.getText().trim() : txtName.getText().trim(),
                        cbCat.getValue(),
                        chkEx.isSelected()
                );
            }
            return null;
        });

        Optional<ApplicationPreference> result = dialog.showAndWait();
        result.ifPresent(pref -> CompletableFuture.runAsync(() -> {
            try {
                preferenceRepository.upsert(pref.getApplicationIdentifier(), pref.getDisplayName(), pref.getCategory(), pref.isExcluded());
                distractionAnalyzer.refreshCache();
                Platform.runLater(this::refreshData);
            } catch (Exception e) {
                logger.error("Failed to add preference rule: {}", e.getMessage());
            }
        }));
    }

    @FXML
    public void handleClearActivities(ActionEvent event) {
        Alert step1 = new Alert(Alert.AlertType.CONFIRMATION, "Clear all recorded application activity intervals? Completed focus session records will be kept.", ButtonType.YES, ButtonType.NO);
        step1.setTitle("Step 1: Clear Activity Logs");
        try {
            step1.getDialogPane().getStylesheets().add(getClass().getResource("/css/flowstate-theme.css").toExternalForm());
        } catch (Exception ignored) {}

        Optional<ButtonType> res1 = step1.showAndWait();
        if (res1.isPresent() && res1.get() == ButtonType.YES) {
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

    @FXML
    public void handlePurgeAllData(ActionEvent event) {
        Alert step1 = new Alert(Alert.AlertType.CONFIRMATION, "Delete ALL focus sessions and activity history?", ButtonType.YES, ButtonType.NO);
        step1.setTitle("Total Data Reset");
        try {
            step1.getDialogPane().getStylesheets().add(getClass().getResource("/css/flowstate-theme.css").toExternalForm());
        } catch (Exception ignored) {}

        Optional<ButtonType> res1 = step1.showAndWait();
        if (res1.isPresent() && res1.get() == ButtonType.YES) {
            CompletableFuture.runAsync(() -> {
                try {
                    activityRepository.clearAll();
                    List<FocusSession> allSessions = sessionRepository.findAll();
                    for (FocusSession s : allSessions) {
                        sessionRepository.deleteById(s.getId());
                    }
                    Platform.runLater(() -> showAlert("Data Reset", "All sessions and activity logs have been wiped clean.", Alert.AlertType.INFORMATION));
                } catch (Exception e) {
                    logger.error("Failed to purge all data: {}", e.getMessage());
                    Platform.runLater(() -> showAlert("Reset Error", e.getMessage(), Alert.AlertType.ERROR));
                }
            });
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
