package com.flowstate.ui;

import com.flowstate.tracking.ActivityChangeListener;
import com.flowstate.tracking.ActivityTrackingService;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Controller for the persistent sidebar navigation and view swapping.
 */
public class NavigationController {
    private static final Logger logger = LoggerFactory.getLogger(NavigationController.class);
    private static NavigationController instance;

    @FXML private Button btnDashboard;
    @FXML private Button btnFocusSessions;
    @FXML private Button btnActivityMonitor;
    @FXML private Button btnAnalytics;
    @FXML private Button btnSessionHistory;
    @FXML private Button btnReports;
    @FXML private Button btnSettings;

    @FXML private StackPane contentArea;
    @FXML private Circle trackingStatusDot;
    @FXML private Label lblTrackingStatus;
    @FXML private Button btnToggleTracking;

    private final Map<String, Node> viewCache = new HashMap<>();
    private final Map<String, Object> controllerCache = new HashMap<>();
    private Button currentActiveBtn;

    public static NavigationController getInstance() {
        return instance;
    }

    @FXML
    public void initialize() {
        instance = this;
        currentActiveBtn = btnDashboard;

        // Register tracking listener
        ActivityTrackingService.getInstance().addListener(new ActivityChangeListener() {
            @Override
            public void onActivityChanged(com.flowstate.model.ApplicationActivity current, com.flowstate.model.ApplicationActivity previous) {}

            @Override
            public void onTrackingStateChanged(boolean trackingEnabled) {
                Platform.runLater(() -> updateTrackingUi(trackingEnabled));
            }
        });

        updateTrackingUi(ActivityTrackingService.getInstance().isTrackingEnabled());

        // Default to Dashboard
        navigateTo("dashboard");
    }

    @FXML
    public void handleNavDashboard(ActionEvent event) {
        setActiveButton(btnDashboard);
        navigateTo("dashboard");
    }

    @FXML
    public void handleNavFocusSessions(ActionEvent event) {
        setActiveButton(btnFocusSessions);
        navigateTo("focus-timer");
    }

    @FXML
    public void handleNavActivityMonitor(ActionEvent event) {
        setActiveButton(btnActivityMonitor);
        navigateTo("activity-monitor");
    }

    @FXML
    public void handleNavAnalytics(ActionEvent event) {
        setActiveButton(btnAnalytics);
        navigateTo("analytics");
    }

    @FXML
    public void handleNavSessionHistory(ActionEvent event) {
        setActiveButton(btnSessionHistory);
        navigateTo("session-history");
    }

    @FXML
    public void handleNavReports(ActionEvent event) {
        setActiveButton(btnReports);
        navigateTo("reports");
    }

    @FXML
    public void handleNavSettings(ActionEvent event) {
        setActiveButton(btnSettings);
        navigateTo("settings");
    }

    @FXML
    public void handleToggleTracking(ActionEvent event) {
        ActivityTrackingService tracker = ActivityTrackingService.getInstance();
        if (tracker.isTrackingEnabled()) {
            tracker.pauseTracking();
        } else {
            tracker.resumeTracking();
        }
    }

    public void navigateTo(String viewName) {
        try {
            Node view = loadView(viewName);
            if (view != null) {
                contentArea.getChildren().setAll(view);

                // Notify controller if it has a refresh hook
                Object controller = controllerCache.get(viewName);
                if (controller instanceof Refreshable refreshable) {
                    refreshable.refreshData();
                }
            }
        } catch (Exception e) {
            logger.error("Error navigating to view '{}': {}", viewName, e.getMessage(), e);
        }
    }

    public void navigateToFocusTimer() {
        setActiveButton(btnFocusSessions);
        navigateTo("focus-timer");
    }

    private Node loadView(String viewName) {
        if (viewCache.containsKey(viewName)) {
            return viewCache.get(viewName);
        }

        String fxmlPath = "/fxml/" + viewName + ".fxml";
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
            Node node = loader.load();
            Object controller = loader.getController();

            viewCache.put(viewName, node);
            if (controller != null) {
                controllerCache.put(viewName, controller);
            }
            return node;
        } catch (IOException e) {
            logger.warn("View '{}' not yet available ({}). Showing placeholder.", fxmlPath, e.getMessage());
            return createPlaceholderView(viewName);
        }
    }

    private Node createPlaceholderView(String viewName) {
        Label title = new Label(viewName.replace("-", " ").toUpperCase());
        title.getStyleClass().add("view-title");
        Label desc = new Label("This module is currently being configured.");
        desc.getStyleClass().add("empty-state-text");

        javafx.scene.layout.VBox box = new javafx.scene.layout.VBox(12, title, desc);
        box.setAlignment(javafx.geometry.Pos.CENTER);
        box.getStyleClass().add("card");
        return box;
    }

    private void setActiveButton(Button btn) {
        if (currentActiveBtn != null) {
            currentActiveBtn.getStyleClass().remove("active");
        }
        if (btn != null) {
            if (!btn.getStyleClass().contains("active")) {
                btn.getStyleClass().add("active");
            }
            currentActiveBtn = btn;
        }
    }

    private void updateTrackingUi(boolean enabled) {
        if (trackingStatusDot == null || lblTrackingStatus == null || btnToggleTracking == null) return;

        if (enabled) {
            trackingStatusDot.setFill(Color.web("#10B981"));
            lblTrackingStatus.setText("Tracking: Active");
            btnToggleTracking.setText("Pause Tracking");
            btnToggleTracking.getStyleClass().removeAll("btn-primary", "btn-warning");
            if (!btnToggleTracking.getStyleClass().contains("btn-secondary")) {
                btnToggleTracking.getStyleClass().add("btn-secondary");
            }
        } else {
            trackingStatusDot.setFill(Color.web("#F59E0B"));
            lblTrackingStatus.setText("Tracking: Paused");
            btnToggleTracking.setText("Resume Tracking");
            btnToggleTracking.getStyleClass().removeAll("btn-secondary");
            if (!btnToggleTracking.getStyleClass().contains("btn-primary")) {
                btnToggleTracking.getStyleClass().add("btn-primary");
            }
        }
    }
}
