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
 * Controller for the streamlined sidebar navigation between Dashboard, Activity Monitor, and Analytics.
 */
public class NavigationController {
    private static final Logger logger = LoggerFactory.getLogger(NavigationController.class);
    private static NavigationController instance;

    @FXML private Button btnDashboard;
    @FXML private Button btnActivityMonitor;
    @FXML private Button btnAnalytics;

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
    public void handleToggleTracking(ActionEvent event) {
        ActivityTrackingService service = ActivityTrackingService.getInstance();
        if (service.isTrackingEnabled()) {
            service.pauseTracking();
        } else {
            service.resumeTracking();
        }
    }

    private void updateTrackingUi(boolean isTracking) {
        if (isTracking) {
            trackingStatusDot.setFill(Color.web("#10B981"));
            lblTrackingStatus.setText("Tracking: Active");
            btnToggleTracking.setText("Pause Tracking");
        } else {
            trackingStatusDot.setFill(Color.web("#EF4444"));
            lblTrackingStatus.setText("Tracking: Paused");
            btnToggleTracking.setText("Resume Tracking");
        }
    }

    private void setActiveButton(Button button) {
        if (currentActiveBtn != null) {
            currentActiveBtn.getStyleClass().remove("active");
        }
        currentActiveBtn = button;
        if (currentActiveBtn != null && !currentActiveBtn.getStyleClass().contains("active")) {
            currentActiveBtn.getStyleClass().add("active");
        }
    }

    public void navigateTo(String viewName) {
        try {
            Node viewNode;
            if (viewCache.containsKey(viewName)) {
                viewNode = viewCache.get(viewName);
                Object controller = controllerCache.get(viewName);
                if (controller instanceof Refreshable) {
                    ((Refreshable) controller).refreshData();
                }
            } else {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/" + viewName + ".fxml"));
                viewNode = loader.load();
                Object controller = loader.getController();

                viewCache.put(viewName, viewNode);
                controllerCache.put(viewName, controller);

                if (controller instanceof Refreshable) {
                    ((Refreshable) controller).refreshData();
                }
            }

            contentArea.getChildren().setAll(viewNode);
            logger.debug("Swapped view to: {}", viewName);
        } catch (IOException e) {
            logger.error("Failed to load view: /fxml/{}.fxml - {}", viewName, e.getMessage(), e);
        }
    }
}
