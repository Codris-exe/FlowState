package com.focusforge;

import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.chart.*;
import javafx.scene.control.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public class MainController {

    @FXML private Button btnNavDashboard;
    @FXML private Button btnNavTimeline;
    @FXML private Button btnNavAnalytics;
    @FXML private Circle trackingStatusDot;
    @FXML private Label lblTrackingStatus;
    @FXML private Button btnToggleTracking;

    @FXML private ScrollPane viewDashboard;
    @FXML private ScrollPane viewTimeline;
    @FXML private ScrollPane viewAnalytics;

    @FXML private Label lblGreeting;
    @FXML private Label lblCurrentDate;
    @FXML private ProgressBar pbDailyGoal;
    @FXML private Label lblGoalProgressText;
    @FXML private Label lblTodayProductive;
    @FXML private Label lblActiveAppsCount;
    @FXML private Label lblTodayDistracting;
    @FXML private Label lblTodayFocusRatio;
    @FXML private BarChart<String, Number> chartWeeklyFocus;
    @FXML private TableView<Map.Entry<String, Long>> tableTopApps;
    @FXML private TableColumn<Map.Entry<String, Long>, String> colAppName;
    @FXML private TableColumn<Map.Entry<String, Long>, String> colAppDuration;
    @FXML private TableView<Activity> tableDashboardRecent;
    @FXML private TableColumn<Activity, String> colDashApp;
    @FXML private TableColumn<Activity, String> colDashTitle;
    @FXML private TableColumn<Activity, String> colDashDuration;
    @FXML private TableColumn<Activity, String> colDashCategory;

    @FXML private Label lblSpotlightProcess;
    @FXML private Label lblSpotlightTitle;
    @FXML private Label lblSpotlightDuration;
    @FXML private Label lblSpotlightBadge;
    @FXML private TableView<Activity> tableTimelineStream;
    @FXML private TableColumn<Activity, String> colTimeApp;
    @FXML private TableColumn<Activity, String> colTimeTitle;
    @FXML private TableColumn<Activity, String> colTimeStarted;
    @FXML private TableColumn<Activity, String> colTimeDuration;
    @FXML private TableColumn<Activity, String> colTimeCategory;

    @FXML private Label lblAnalyticLogs;
    @FXML private Label lblAnalyticTotalTime;
    @FXML private Label lblAnalyticProductiveTime;
    @FXML private Label lblAnalyticFocusRatio;
    @FXML private BarChart<String, Number> chartAnalyticsTrend;
    @FXML private PieChart chartAnalyticsPie;

    private final ActivityStorage storage = ActivityStorage.getInstance();
    private final ActivityTracker tracker = ActivityTracker.getInstance();

    private final ObservableList<Map.Entry<String, Long>> topAppsData = FXCollections.observableArrayList();
    private final ObservableList<Activity> dashboardRecentData = FXCollections.observableArrayList();
    private final ObservableList<Activity> timelineData = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        setupDateAndGreeting();
        setupTableColumns();

        tracker.addChangeListener(() -> Platform.runLater(this::refreshAllViews));

        updateTrackingUi(tracker.isTrackingEnabled());

        showDashboard();
        refreshAllViews();
    }

    private void setupDateAndGreeting() {
        LocalTime now = LocalTime.now();
        String greeting = (now.isBefore(LocalTime.NOON)) ? "Good morning!" : (now.isBefore(LocalTime.of(17, 0))) ? "Good afternoon!" : "Good evening!";
        lblGreeting.setText(greeting);
        lblCurrentDate.setText("Today is " + LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy")));
    }

    private void setupTableColumns() {
        colAppName.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getKey()));
        colAppDuration.setCellValueFactory(data -> new SimpleStringProperty(formatSeconds(data.getValue().getValue())));
        tableTopApps.setItems(topAppsData);

        colDashApp.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getApplicationName()));
        colDashTitle.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getWindowTitle()));
        colDashDuration.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getFormattedDuration()));
        colDashCategory.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getCategory().name()));
        tableDashboardRecent.setItems(dashboardRecentData);

        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("HH:mm:ss");
        colTimeApp.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getApplicationName()));
        colTimeTitle.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getWindowTitle()));
        colTimeStarted.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().getStartedAt() != null ? data.getValue().getStartedAt().atZone(ZoneId.systemDefault()).format(dtf) : "-"));
        colTimeDuration.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getFormattedDuration()));
        colTimeCategory.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getCategory().name()));
        tableTimelineStream.setItems(timelineData);
    }

    @FXML
    public void handleNavDashboard() {
        showDashboard();
    }

    @FXML
    public void handleNavTimeline() {
        showTimeline();
    }

    @FXML
    public void handleNavAnalytics() {
        showAnalytics();
    }

    private void showDashboard() {
        viewDashboard.setVisible(true);
        viewTimeline.setVisible(false);
        viewAnalytics.setVisible(false);
        setNavActive(btnNavDashboard);
        refreshAllViews();
    }

    private void showTimeline() {
        viewDashboard.setVisible(false);
        viewTimeline.setVisible(true);
        viewAnalytics.setVisible(false);
        setNavActive(btnNavTimeline);
        refreshAllViews();
    }

    private void showAnalytics() {
        viewDashboard.setVisible(false);
        viewTimeline.setVisible(false);
        viewAnalytics.setVisible(true);
        setNavActive(btnNavAnalytics);
        refreshAllViews();
    }

    private void setNavActive(Button button) {
        btnNavDashboard.getStyleClass().remove("active");
        btnNavTimeline.getStyleClass().remove("active");
        btnNavAnalytics.getStyleClass().remove("active");
        if (!button.getStyleClass().contains("active")) {
            button.getStyleClass().add("active");
        }
    }

    @FXML
    public void handleToggleTracking() {
        if (tracker.isTrackingEnabled()) {
            tracker.pauseTracking();
            updateTrackingUi(false);
        } else {
            tracker.resumeTracking();
            updateTrackingUi(true);
        }
        refreshAllViews();
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

    public void refreshAllViews() {
        CompletableFuture.runAsync(() -> {
            LocalDate today = LocalDate.now();
            Instant startToday = today.atStartOfDay(ZoneId.systemDefault()).toInstant();
            Instant endToday = today.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant();

            List<Activity> todayList = new ArrayList<>(storage.findByDateRange(startToday, endToday));

            Activity live = tracker.getCurrentActivity();
            if (live != null && live.getStartedAt() != null && !live.getStartedAt().isBefore(startToday)) {
                Activity liveCopy = new Activity(live.getApplicationName(), live.getWindowTitle(), live.getStartedAt(), live.getCategory());
                liveCopy.close(Instant.now());
                todayList.add(liveCopy);
            }

            long prodSec = 0;
            long neutSec = 0;
            long distSec = 0;

            for (Activity a : todayList) {
                long d = a.getDurationSeconds();
                if (a.getCategory() == Activity.Category.PRODUCTIVE) prodSec += d;
                else if (a.getCategory() == Activity.Category.DISTRACTING) distSec += d;
                else neutSec += d;
            }

            long totalSec = prodSec + neutSec + distSec;
            double ratio = totalSec > 0 ? (prodSec * 100.0) / totalSec : 0.0;
            double goalProgress = Math.min(1.0, (double) prodSec / (120 * 60));

            Map<String, Long> topApps = storage.getTopApplicationsDuration(startToday, endToday, 6);
            List<Map.Entry<String, Long>> topEntries = new ArrayList<>(topApps.entrySet());

            List<Activity> recentActs = storage.findRecent(15);

            XYChart.Series<String, Number> weeklySeries = new XYChart.Series<>();
            for (int i = 6; i >= 0; i--) {
                LocalDate d = today.minusDays(i);
                Instant s = d.atStartOfDay(ZoneId.systemDefault()).toInstant();
                Instant e = d.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant();
                long dSec = storage.findByDateRange(s, e).stream()
                        .filter(a -> a.getCategory() == Activity.Category.PRODUCTIVE)
                        .mapToLong(Activity::getDurationSeconds).sum();
                weeklySeries.getData().add(new XYChart.Data<>(d.format(DateTimeFormatter.ofPattern("EEE")), dSec / 60));
            }

            XYChart.Series<String, Number> analTrend = new XYChart.Series<>();
            for (int i = 6; i >= 0; i--) {
                LocalDate d = today.minusDays(i);
                Instant s = d.atStartOfDay(ZoneId.systemDefault()).toInstant();
                Instant e = d.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant();
                long dSec = storage.findByDateRange(s, e).stream().mapToLong(Activity::getDurationSeconds).sum();
                analTrend.getData().add(new XYChart.Data<>(d.format(DateTimeFormatter.ofPattern("EEE")), dSec / 60));
            }

            final long fProd = prodSec;
            final long fNeut = neutSec;
            final long fDist = distSec;
            final long fTotal = totalSec;
            final double fRatio = ratio;
            final double fGoal = goalProgress;
            final Activity fLive = live;

            Platform.runLater(() -> {
                lblTodayProductive.setText(formatSeconds(fProd));
                lblTodayDistracting.setText(formatSeconds(fDist));
                lblActiveAppsCount.setText(String.valueOf(topEntries.size()));
                lblTodayFocusRatio.setText(String.format("%.1f%%", fRatio));
                pbDailyGoal.setProgress(fGoal);
                lblGoalProgressText.setText(String.format("%d / 120 mins (%d%%)", fProd / 60, (int) (fGoal * 100)));

                chartWeeklyFocus.getData().clear();
                chartWeeklyFocus.getData().add(weeklySeries);

                topAppsData.clear();
                topAppsData.addAll(topEntries);

                dashboardRecentData.clear();
                dashboardRecentData.addAll(recentActs);

                if (fLive != null) {
                    lblSpotlightProcess.setText(fLive.getApplicationName());
                    lblSpotlightTitle.setText(fLive.getWindowTitle());
                    lblSpotlightBadge.setText(fLive.getCategory().name());
                    lblSpotlightDuration.setText(fLive.getFormattedDuration());
                }

                timelineData.clear();
                timelineData.addAll(recentActs);

                lblAnalyticLogs.setText(recentActs.size() + " logs");
                lblAnalyticTotalTime.setText(formatSeconds(fTotal));
                lblAnalyticProductiveTime.setText(formatSeconds(fProd));
                lblAnalyticFocusRatio.setText(String.format("%.1f%%", fRatio));

                chartAnalyticsTrend.getData().clear();
                chartAnalyticsTrend.getData().add(analTrend);

                chartAnalyticsPie.getData().clear();
                if (fProd > 0) chartAnalyticsPie.getData().add(new PieChart.Data("Productive", (double) fProd / 60));
                if (fNeut > 0) chartAnalyticsPie.getData().add(new PieChart.Data("Neutral", (double) fNeut / 60));
                if (fDist > 0) chartAnalyticsPie.getData().add(new PieChart.Data("Distracting", (double) fDist / 60));
                if (chartAnalyticsPie.getData().isEmpty()) {
                    chartAnalyticsPie.getData().add(new PieChart.Data("No Activity Logged", 1));
                }
            });
        });
    }

    private String formatSeconds(long totalSeconds) {
        if (totalSeconds <= 0) return "0m";
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        if (hours > 0) return String.format("%dh %dm", hours, minutes);
        return String.format("%dm", minutes);
    }
}

