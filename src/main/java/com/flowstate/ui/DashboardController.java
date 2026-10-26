package com.flowstate.ui;

import com.flowstate.model.AppCategory;
import com.flowstate.model.FocusSession;
import com.flowstate.repository.ActivityRepository;
import com.flowstate.repository.FocusSessionRepository;
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

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Controller for the Dashboard overview screen.
 */
public class DashboardController implements Refreshable {
    private static final Logger logger = LoggerFactory.getLogger(DashboardController.class);

    @FXML private Label lblGreeting;
    @FXML private Label lblCurrentDate;

    @FXML private ProgressBar pbDailyGoal;
    @FXML private Label lblGoalProgressText;

    @FXML private Label lblTodayFocusTime;
    @FXML private Label lblFocusSubtext;
    @FXML private Label lblCompletedSessions;
    @FXML private Label lblTotalInterruptions;
    @FXML private Label lblAvgSessionDuration;

    @FXML private BarChart<String, Number> chartWeeklyFocus;
    @FXML private CategoryAxis axisDays;
    @FXML private NumberAxis axisMinutes;

    @FXML private TableView<AppUsageSummary> tableTopApps;
    @FXML private TableColumn<AppUsageSummary, String> colAppName;
    @FXML private TableColumn<AppUsageSummary, String> colAppDuration;
    @FXML private TableColumn<AppUsageSummary, String> colAppCategory;

    @FXML private TableView<FocusSession> tableRecentSessions;
    @FXML private TableColumn<FocusSession, String> colSessionTitle;
    @FXML private TableColumn<FocusSession, String> colSessionCategory;
    @FXML private TableColumn<FocusSession, String> colSessionDuration;
    @FXML private TableColumn<FocusSession, String> colSessionInterruptions;
    @FXML private TableColumn<FocusSession, String> colSessionStatus;

    @FXML private Label lblProductivityInsight;

    private FocusSessionRepository sessionRepository;
    private ActivityRepository activityRepository;

    private final ObservableList<AppUsageSummary> topAppsData = FXCollections.observableArrayList();
    private final ObservableList<FocusSession> recentSessionsData = FXCollections.observableArrayList();

    public DashboardController() {
        this(com.flowstate.storage.RepositoryFactory.getFocusSessionRepository(),
             com.flowstate.storage.RepositoryFactory.getActivityRepository());
    }

    public DashboardController(FocusSessionRepository sessionRepository, ActivityRepository activityRepository) {
        this.sessionRepository = sessionRepository;
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

        // Recent Sessions Table
        colSessionTitle.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getTitle()));
        colSessionCategory.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getCategory().name()));
        colSessionDuration.setCellValueFactory(data -> new SimpleStringProperty(formatSeconds(data.getValue().getActualDurationSeconds())));
        colSessionInterruptions.setCellValueFactory(data -> new SimpleStringProperty(String.valueOf(data.getValue().getInterruptionCount())));
        colSessionStatus.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getStatus().name()));
        tableRecentSessions.setItems(recentSessionsData);
        tableRecentSessions.setPlaceholder(new Label("No focus sessions recorded yet. Start one above!"));
    }

    @Override
    public void refreshData() {
        CompletableFuture.runAsync(this::loadDashboardMetrics)
                .exceptionally(ex -> {
                    logger.error("Failed to load dashboard metrics: {}", ex.getMessage());
                    return null;
                });
    }

    private void loadDashboardMetrics() {
        try {
            LocalDate today = LocalDate.now();
            Instant startOfToday = today.atStartOfDay(ZoneId.systemDefault()).toInstant();
            Instant endOfToday = today.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant();

            // 1. Fetch metrics from repositories
            long todaySeconds = sessionRepository.sumTodayFocusSeconds(startOfToday);
            long totalSessions = sessionRepository.countCompletedSessions();
            List<FocusSession> recent = sessionRepository.findRecent(5);
            List<FocusSession> todaySessions = sessionRepository.findByDateRange(startOfToday, endOfToday);

            int totalInterruptions = 0;
            for (FocusSession s : todaySessions) {
                totalInterruptions += s.getInterruptionCount();
            }

            long avgDurationSec = 0;
            if (!todaySessions.isEmpty()) {
                long sumDuration = 0;
                for (FocusSession s : todaySessions) {
                    sumDuration += s.getActualDurationSeconds();
                }
                avgDurationSec = sumDuration / todaySessions.size();
            }

            // Top apps today
            Map<String, Long> topApps = activityRepository.getTopApplicationsDuration(startOfToday, endOfToday, 5);

            // Weekly focus time (last 7 days)
            XYChart.Series<String, Number> weeklySeries = new XYChart.Series<>();
            weeklySeries.setName("Focus Minutes");

            for (int i = 6; i >= 0; i--) {
                LocalDate day = today.minusDays(i);
                Instant dStart = day.atStartOfDay(ZoneId.systemDefault()).toInstant();
                Instant dEnd = day.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant();
                long sec = sessionRepository.sumTodayFocusSeconds(dStart);
                String dayLabel = day.format(DateTimeFormatter.ofPattern("EEE"));
                weeklySeries.getData().add(new XYChart.Data<>(dayLabel, sec / 60));
            }

            // Generate explainable insight
            String insightText = generateInsight(todaySessions, totalInterruptions, todaySeconds);

            final long finalTodaySeconds = todaySeconds;
            final long finalAvgDurationSec = avgDurationSec;
            final int finalInterruptions = totalInterruptions;
            final long finalTotalSessions = totalSessions;

            // 2. Dispatch UI updates to JavaFX Application Thread
            Platform.runLater(() -> {
                // Key Metric Cards
                lblTodayFocusTime.setText(formatSeconds(finalTodaySeconds));
                lblCompletedSessions.setText(String.valueOf(finalTotalSessions));
                lblTotalInterruptions.setText(String.valueOf(finalInterruptions));
                lblAvgSessionDuration.setText(formatSeconds(finalAvgDurationSec));

                // Goal Progress (assuming 120m default goal)
                long goalMinutes = 120;
                long todayMinutes = finalTodaySeconds / 60;
                double progress = Math.min(1.0, (double) todayMinutes / goalMinutes);
                pbDailyGoal.setProgress(progress);
                lblGoalProgressText.setText(String.format("%d / %d mins (%.0f%%)", todayMinutes, goalMinutes, progress * 100));

                // Top Apps Table
                topAppsData.clear();
                for (Map.Entry<String, Long> entry : topApps.entrySet()) {
                    topAppsData.add(new AppUsageSummary(entry.getKey(), entry.getValue(), AppCategory.NEUTRAL));
                }

                // Recent Sessions Table
                recentSessionsData.clear();
                recentSessionsData.addAll(recent);

                // Weekly Chart
                chartWeeklyFocus.getData().clear();
                chartWeeklyFocus.getData().add(weeklySeries);

                // Insight
                lblProductivityInsight.setText(insightText);
            });

        } catch (Exception e) {
            logger.warn("Could not query dashboard statistics from database: {}", e.getMessage());
        }
    }

    private String generateInsight(List<FocusSession> sessions, int interruptions, long focusSeconds) {
        if (sessions.isEmpty() || focusSeconds == 0) {
            return "Complete your first focus session to unlock deep-work analytics and interruption patterns!";
        }

        long focusMinutes = focusSeconds / 60;
        if (interruptions == 0) {
            return String.format("Outstanding discipline today! You accumulated %d minutes of deep work across %d session(s) with zero recorded interruptions.",
                    focusMinutes, sessions.size());
        } else {
            return String.format("You logged %d minutes of focused work today across %d session(s) with %d recorded distraction interruption(s). Keep up the momentum!",
                    focusMinutes, sessions.size(), interruptions);
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

    @FXML
    public void handleStartFocusSession(ActionEvent event) {
        NavigationController nav = NavigationController.getInstance();
        if (nav != null) {
            nav.navigateToFocusTimer();
        }
    }

    @FXML
    public void handleViewAllSessions(ActionEvent event) {
        NavigationController nav = NavigationController.getInstance();
        if (nav != null) {
            nav.navigateTo("analytics");
        }
    }
}
