package com.flowstate.ui;

import com.flowstate.model.AppCategory;
import com.flowstate.model.ApplicationActivity;
import com.flowstate.repository.ActivityRepository;
import com.flowstate.storage.RepositoryFactory;
import com.flowstate.ui.model.AppUsageSummary;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Controller for the Dashboard screen.
 */
public class DashboardController implements Refreshable {
    private static final Logger logger = LoggerFactory.getLogger(DashboardController.class);

    @FXML private Label lblGreeting;
    @FXML private Label lblCurrentDate;

    @FXML private ProgressBar pbDailyGoal;
    @FXML private Label lblGoalProgressText;

    @FXML private Label lblTodayFocusTime;
    @FXML private Label lblFocusSubtext;
    @FXML private Label lblActiveAppsCount;
    @FXML private Label lblDistractingTime;
    @FXML private Label lblFocusRatio;

    @FXML private BarChart<String, Number> chartWeeklyFocus;
    @FXML private CategoryAxis axisDays;
    @FXML private NumberAxis axisMinutes;

    @FXML private TableView<AppUsageSummary> tableTopApps;
    @FXML private TableColumn<AppUsageSummary, String> colAppName;
    @FXML private TableColumn<AppUsageSummary, String> colAppDuration;
    @FXML private TableColumn<AppUsageSummary, String> colAppCategory;

    @FXML private TableView<ApplicationActivity> tableRecentActivities;
    @FXML private TableColumn<ApplicationActivity, String> colActApp;
    @FXML private TableColumn<ApplicationActivity, String> colActTitle;
    @FXML private TableColumn<ApplicationActivity, String> colActDuration;
    @FXML private TableColumn<ApplicationActivity, String> colActCategory;

    @FXML private Label lblProductivityInsight;

    private final ActivityRepository activityRepository;

    private final ObservableList<AppUsageSummary> topAppsData = FXCollections.observableArrayList();
    private final ObservableList<ApplicationActivity> recentActivitiesData = FXCollections.observableArrayList();

    public DashboardController() {
        this(RepositoryFactory.getActivityRepository());
    }

    public DashboardController(ActivityRepository activityRepository) {
        this.activityRepository = activityRepository;
    }

    @FXML
    public void initialize() {
        setupDateAndGreeting();
        setupTables();
        refreshData();
    }

    private void setupDateAndGreeting() {
        LocalTime nowTime = LocalTime.now();
        String greeting;
        if (nowTime.isBefore(LocalTime.NOON)) {
            greeting = "Good morning!";
        } else if (nowTime.isBefore(LocalTime.of(17, 0))) {
            greeting = "Good afternoon!";
        } else {
            greeting = "Good evening!";
        }
        lblGreeting.setText(greeting);

        LocalDate today = LocalDate.now();
        String dateStr = today.format(DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy"));
        lblCurrentDate.setText("Today is " + dateStr);
    }

    private void setupTables() {
        // Top Apps Table
        colAppName.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getApplicationName()));
        colAppDuration.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getFormattedDuration()));
        colAppCategory.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getCategoryName()));
        tableTopApps.setItems(topAppsData);
        tableTopApps.setPlaceholder(new Label("No application activity recorded today."));

        // Recent Activities Table
        colActApp.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getApplicationName()));
        colActTitle.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getWindowTitle()));
        colActDuration.setCellValueFactory(data -> new SimpleStringProperty(formatSeconds(data.getValue().getDurationSeconds())));
        colActCategory.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getActivityCategory().name()));
        tableRecentActivities.setItems(recentActivitiesData);
        tableRecentActivities.setPlaceholder(new Label("No recent activities logged yet."));
    }

    @Override
    public void refreshData() {
        CompletableFuture.runAsync(this::loadDashboardMetrics)
                .exceptionally(ex -> {
                    logger.error("Error loading dashboard data: {}", ex.getMessage());
                    return null;
                });
    }

    private void loadDashboardMetrics() {
        try {
            LocalDate today = LocalDate.now();
            Instant startOfToday = today.atStartOfDay(ZoneId.systemDefault()).toInstant();
            Instant endOfToday = today.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant();

            List<ApplicationActivity> todayActivities = activityRepository.findByDateRange(startOfToday, endOfToday);

            long productiveSec = 0;
            long neutralSec = 0;
            long distractingSec = 0;

            for (ApplicationActivity act : todayActivities) {
                long d = act.getDurationSeconds();
                if (act.getActivityCategory() == AppCategory.PRODUCTIVE) productiveSec += d;
                else if (act.getActivityCategory() == AppCategory.DISTRACTING) distractingSec += d;
                else neutralSec += d;
            }

            long totalSec = productiveSec + neutralSec + distractingSec;
            double focusRatio = totalSec > 0 ? (productiveSec * 100.0) / totalSec : 0.0;

            // Goal calculation (target 120 minutes of productive work)
            double goalProgress = Math.min(1.0, (double) productiveSec / (120 * 60));
            String goalText = String.format("%d / 120 mins (%d%%)", productiveSec / 60, (int) (goalProgress * 100));

            // Top Apps
            Map<String, Long> topAppsMap = activityRepository.getTopApplicationsDuration(startOfToday, endOfToday, 5);
            ObservableList<AppUsageSummary> newTopApps = FXCollections.observableArrayList();
            topAppsMap.forEach((app, dur) -> {
                AppCategory cat = AppCategory.NEUTRAL;
                for (ApplicationActivity a : todayActivities) {
                    if (a.getApplicationName().equalsIgnoreCase(app)) {
                        cat = a.getActivityCategory();
                        break;
                    }
                }
                newTopApps.add(new AppUsageSummary(app, dur, cat));
            });

            // Weekly Chart Data
            XYChart.Series<String, Number> weeklySeries = new XYChart.Series<>();
            weeklySeries.setName("Productive Minutes");

            for (int i = 6; i >= 0; i--) {
                LocalDate day = today.minusDays(i);
                Instant dStart = day.atStartOfDay(ZoneId.systemDefault()).toInstant();
                Instant dEnd = day.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant();
                List<ApplicationActivity> dayActs = activityRepository.findByDateRange(dStart, dEnd);

                long dayProdSec = dayActs.stream()
                        .filter(a -> a.getActivityCategory() == AppCategory.PRODUCTIVE)
                        .mapToLong(ApplicationActivity::getDurationSeconds)
                        .sum();

                String dayName = day.format(DateTimeFormatter.ofPattern("EEE"));
                weeklySeries.getData().add(new XYChart.Data<>(dayName, dayProdSec / 60));
            }

            // Recent 10 activities
            List<ApplicationActivity> recentActs = activityRepository.findRecent(10);

            // Insight generator
            String insight;
            if (totalSec == 0) {
                insight = "✨ Active window tracking running. Switch between apps to generate live productivity insights!";
            } else if (focusRatio >= 70.0) {
                insight = String.format("🔥 Outstanding flow state! %.0f%% of your computer time today was in productive applications.", focusRatio);
            } else if (focusRatio >= 45.0) {
                insight = String.format("⚡ Solid balance: %.0f%% productive work, with %s in secondary tools.", focusRatio, formatSeconds(neutralSec));
            } else {
                insight = String.format("⚠️ Distraction alert: %s spent in distracting apps today. Try closing background distractions.", formatSeconds(distractingSec));
            }

            final long finalProd = productiveSec;
            final long finalDist = distractingSec;
            final double finalRatio = focusRatio;
            final int finalAppCount = topAppsMap.size();
            final String finalInsight = insight;

            Platform.runLater(() -> {
                lblTodayFocusTime.setText(formatSeconds(finalProd));
                lblGoalProgressText.setText(goalText);
                pbDailyGoal.setProgress(goalProgress);

                lblActiveAppsCount.setText(String.valueOf(finalAppCount));
                lblDistractingTime.setText(formatSeconds(finalDist));
                lblFocusRatio.setText(String.format("%.1f%%", finalRatio));

                chartWeeklyFocus.getData().clear();
                chartWeeklyFocus.getData().add(weeklySeries);

                topAppsData.clear();
                topAppsData.addAll(newTopApps);

                recentActivitiesData.clear();
                recentActivitiesData.addAll(recentActs);

                lblProductivityInsight.setText(finalInsight);
            });

        } catch (Exception e) {
            logger.error("Failed to load dashboard metrics: {}", e.getMessage(), e);
        }
    }

    private String formatSeconds(long totalSeconds) {
        if (totalSeconds <= 0) return "0m";
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        if (hours > 0) {
            return String.format("%dh %dm", hours, minutes);
        }
        return String.format("%dm", minutes);
    }
}
