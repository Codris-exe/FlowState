package com.flowstate.ui;

import com.flowstate.analytics.DistractionAnalyzer;
import com.flowstate.config.DatabaseManager;
import com.flowstate.model.AppCategory;
import com.flowstate.model.ApplicationActivity;
import com.flowstate.repository.ActivityRepository;
import com.flowstate.repository.ActivityRepositoryJDBC;
import com.flowstate.repository.ApplicationPreferenceRepositoryJDBC;
import com.flowstate.tracking.ActivityChangeListener;
import com.flowstate.tracking.ActivityTrackingService;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.shape.Circle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Controller for the Activity Monitor screen showing live foreground application and activity stream.
 */
public class ActivityMonitorController implements ActivityChangeListener, Refreshable {
    private static final Logger logger = LoggerFactory.getLogger(ActivityMonitorController.class);

    @FXML private Circle liveIndicatorDot;
    @FXML private Label lblCurrentCategoryBadge;
    @FXML private Label lblActiveProcessName;
    @FXML private Label lblActiveWindowTitle;
    @FXML private Label lblActiveDuration;

    @FXML private TextField txtSearchActivity;
    @FXML private TableView<ApplicationActivity> tableActivities;
    @FXML private TableColumn<ApplicationActivity, String> colApp;
    @FXML private TableColumn<ApplicationActivity, String> colTitle;
    @FXML private TableColumn<ApplicationActivity, String> colStartedAt;
    @FXML private TableColumn<ApplicationActivity, String> colDuration;
    @FXML private TableColumn<ApplicationActivity, String> colCategory;

    private final ActivityTrackingService trackingService;
    private final ActivityRepository activityRepository;
    private final DistractionAnalyzer distractionAnalyzer;

    private final ObservableList<ApplicationActivity> masterList = FXCollections.observableArrayList();
    private FilteredList<ApplicationActivity> filteredList;

    public ActivityMonitorController() {
        this(ActivityTrackingService.getInstance(),
                com.flowstate.storage.RepositoryFactory.getActivityRepository(),
                new DistractionAnalyzer(com.flowstate.storage.RepositoryFactory.getApplicationPreferenceRepository()));
    }

    public ActivityMonitorController(ActivityTrackingService trackingService,
                                     ActivityRepository activityRepository,
                                     DistractionAnalyzer distractionAnalyzer) {
        this.trackingService = trackingService;
        this.activityRepository = activityRepository;
        this.distractionAnalyzer = distractionAnalyzer;
    }

    @FXML
    public void initialize() {
        setupTableColumns();
        trackingService.addListener(this);

        filteredList = new FilteredList<>(masterList, p -> true);
        tableActivities.setItems(filteredList);

        txtSearchActivity.textProperty().addListener((obs, oldVal, newVal) -> {
            String query = newVal != null ? newVal.trim().toLowerCase() : "";
            filteredList.setPredicate(act -> {
                if (query.isEmpty()) return true;
                return act.getApplicationName().toLowerCase().contains(query)
                        || (act.getWindowTitle() != null && act.getWindowTitle().toLowerCase().contains(query));
            });
        });

        refreshData();
        updateActiveDisplay(trackingService.getCurrentActivity());
    }

    private void setupTableColumns() {
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("HH:mm:ss");

        colApp.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getApplicationName()));
        colTitle.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getWindowTitle()));
        colStartedAt.setCellValueFactory(data -> {
            if (data.getValue().getStartedAt() != null) {
                return new SimpleStringProperty(data.getValue().getStartedAt().atZone(ZoneId.systemDefault()).format(dtf));
            }
            return new SimpleStringProperty("-");
        });
        colDuration.setCellValueFactory(data -> new SimpleStringProperty(formatSeconds(data.getValue().getDurationSeconds())));
        colCategory.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getActivityCategory().name()));

        tableActivities.setPlaceholder(new Label("No recent application activity recorded."));
    }

    @Override
    public void refreshData() {
        updateActiveDisplay(trackingService.getCurrentActivity());

        if (!com.flowstate.storage.RepositoryFactory.isFileStorageMode() && !DatabaseManager.getInstance().isHealthy()) return;

        CompletableFuture.runAsync(() -> {
            try {
                List<ApplicationActivity> recent = activityRepository.findRecent(200);
                Platform.runLater(() -> {
                    masterList.clear();
                    masterList.addAll(recent);
                });
            } catch (Exception e) {
                logger.error("Failed to load activity stream: {}", e.getMessage());
            }
        });
    }

    private void updateActiveDisplay(ApplicationActivity current) {
        if (current != null) {
            lblActiveProcessName.setText(current.getApplicationName());
            lblActiveWindowTitle.setText(current.getWindowTitle().isEmpty() ? "(No Window Title)" : current.getWindowTitle());

            AppCategory cat = current.getActivityCategory();
            lblCurrentCategoryBadge.setText(cat.name());
            setCategoryBadgeStyle(cat);

            long duration = 0;
            if (current.getStartedAt() != null) {
                duration = Duration.between(current.getStartedAt(), Instant.now()).toSeconds();
            }
            lblActiveDuration.setText(formatSeconds(duration));
        } else {
            lblActiveProcessName.setText("System Idle / No App Focused");
            lblActiveWindowTitle.setText("Waiting for active foreground window...");
            lblCurrentCategoryBadge.setText("NEUTRAL");
            setCategoryBadgeStyle(AppCategory.NEUTRAL);
            lblActiveDuration.setText("00:00");
        }
    }

    private void setCategoryBadgeStyle(AppCategory cat) {
        switch (cat) {
            case PRODUCTIVE -> lblCurrentCategoryBadge.setStyle("-fx-background-color: rgba(16, 185, 129, 0.2); -fx-text-fill: #10B981; -fx-padding: 3px 10px; -fx-background-radius: 10px; -fx-font-weight: bold; -fx-font-size: 11px;");
            case DISTRACTING -> lblCurrentCategoryBadge.setStyle("-fx-background-color: rgba(239, 68, 68, 0.2); -fx-text-fill: #EF4444; -fx-padding: 3px 10px; -fx-background-radius: 10px; -fx-font-weight: bold; -fx-font-size: 11px;");
            default -> lblCurrentCategoryBadge.setStyle("-fx-background-color: rgba(139, 92, 246, 0.2); -fx-text-fill: #8B5CF6; -fx-padding: 3px 10px; -fx-background-radius: 10px; -fx-font-weight: bold; -fx-font-size: 11px;");
        }
    }

    @Override
    public void onActivityChanged(ApplicationActivity current, ApplicationActivity previous) {
        Platform.runLater(() -> {
            updateActiveDisplay(current);
            if (previous != null && previous.getDurationSeconds() >= 1) {
                masterList.add(0, previous);
            }
        });
    }

    @FXML public void handleRefresh(ActionEvent e) { refreshData(); }

    @FXML
    public void handleMarkProductive(ActionEvent e) {
        classifyCurrentApp(AppCategory.PRODUCTIVE, false);
    }

    @FXML
    public void handleMarkNeutral(ActionEvent e) {
        classifyCurrentApp(AppCategory.NEUTRAL, false);
    }

    @FXML
    public void handleMarkDistracting(ActionEvent e) {
        classifyCurrentApp(AppCategory.DISTRACTING, false);
    }

    @FXML
    public void handleExcludeApp(ActionEvent e) {
        ApplicationActivity current = trackingService.getCurrentActivity();
        if (current == null) return;
        classifyCurrentApp(AppCategory.NEUTRAL, true);
        showAlert("Application Excluded", "Application '" + current.getApplicationName() + "' is now excluded from all tracking and logging.", Alert.AlertType.INFORMATION);
    }

    private void classifyCurrentApp(AppCategory category, boolean isExcluded) {
        ApplicationActivity current = trackingService.getCurrentActivity();
        if (current == null) return;

        String app = current.getApplicationName();
        distractionAnalyzer.setPreference(app, app, category, isExcluded);
        current.setActivityCategory(category);
        updateActiveDisplay(current);
        logger.info("Updated classification for app '{}': category={}, excluded={}", app, category, isExcluded);
    }

    private String formatSeconds(long totalSeconds) {
        long m = totalSeconds / 60;
        long s = totalSeconds % 60;
        return String.format("%02dm %02ds", m, s);
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
