package com.flowstate.ui;

import com.flowstate.model.AppCategory;
import com.flowstate.model.ApplicationActivity;
import com.flowstate.model.FocusSession;
import com.flowstate.model.SessionCategory;
import com.flowstate.repository.ActivityRepository;
import com.flowstate.repository.FocusSessionRepository;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.chart.*;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Controller for the Deep Focus inspired Productivity Analytics screen.
 */
public class AnalyticsController implements Refreshable {
    private static final Logger logger = LoggerFactory.getLogger(AnalyticsController.class);

    @FXML private ComboBox<String> cbTimeframe;

    @FXML private Label lblCompletedCount;
    @FXML private Label lblTotalFocusTime;
    @FXML private Label lblLongestSession;
    @FXML private Label lblAvgSession;

    @FXML private BarChart<String, Number> chartDurationTrend;
    @FXML private CategoryAxis axisTrendDates;
    @FXML private NumberAxis axisTrendMinutes;

    @FXML private PieChart chartCategoryDistribution;

    @FXML private BarChart<String, Number> chartCategoryInterruptions;
    @FXML private CategoryAxis axisCategories;
    @FXML private NumberAxis axisInterruptionCount;

    private final FocusSessionRepository sessionRepository;
    private final ActivityRepository activityRepository;

    public AnalyticsController() {
        this(com.flowstate.storage.RepositoryFactory.getFocusSessionRepository(),
             com.flowstate.storage.RepositoryFactory.getActivityRepository());
    }

    public AnalyticsController(FocusSessionRepository sessionRepository, ActivityRepository activityRepository) {
        this.sessionRepository = sessionRepository;
        this.activityRepository = activityRepository;
    }

    @FXML
    public void initialize() {
        cbTimeframe.setItems(FXCollections.observableArrayList(
                "Today", "Last 7 Days", "Last 30 Days", "All Time"
        ));
        cbTimeframe.setValue("Last 7 Days");

        refreshData();
    }

    @FXML
    public void handleTimeframeChange(ActionEvent event) {
        refreshData();
    }

    @Override
    public void refreshData() {
        String selected = cbTimeframe.getValue() != null ? cbTimeframe.getValue() : "Last 7 Days";
        CompletableFuture.runAsync(() -> loadAnalyticsData(selected))
                .exceptionally(ex -> {
                    logger.error("Failed to load analytics: {}", ex.getMessage());
                    return null;
                });
    }

    private void loadAnalyticsData(String timeframe) {
        try {
            LocalDate today = LocalDate.now();
            Instant end = today.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant();
            Instant start;
            int daysBack;

            switch (timeframe) {
                case "Today" -> {
                    start = today.atStartOfDay(ZoneId.systemDefault()).toInstant();
                    daysBack = 1;
                }
                case "Last 30 Days" -> {
                    start = today.minusDays(29).atStartOfDay(ZoneId.systemDefault()).toInstant();
                    daysBack = 30;
                }
                case "All Time" -> {
                    start = Instant.EPOCH;
                    daysBack = 30;
                }
                default -> { // Last 7 Days
                    start = today.minusDays(6).atStartOfDay(ZoneId.systemDefault()).toInstant();
                    daysBack = 7;
                }
            }

            List<FocusSession> sessions = sessionRepository.findByDateRange(start, end);
            List<ApplicationActivity> activities = activityRepository.findByDateRange(start, end);

            // 1. Compute Deep Focus 4 metrics
            long totalFocusSec = 0;
            long maxSessionSec = 0;
            int sessionCount = sessions.size();

            Map<SessionCategory, Integer> categoryInterruptions = new EnumMap<>(SessionCategory.class);
            for (SessionCategory cat : SessionCategory.values()) {
                categoryInterruptions.put(cat, 0);
            }

            for (FocusSession s : sessions) {
                long dur = s.getActualDurationSeconds();
                totalFocusSec += dur;
                if (dur > maxSessionSec) {
                    maxSessionSec = dur;
                }
                int currentCount = categoryInterruptions.getOrDefault(s.getCategory(), 0);
                categoryInterruptions.put(s.getCategory(), currentCount + s.getInterruptionCount());
            }

            long avgSessionSec = sessionCount > 0 ? (totalFocusSec / sessionCount) : 0;

            // 2. Application distribution (RIZE categories)
            long productiveSec = 0;
            long neutralSec = 0;
            long distractingSec = 0;

            for (ApplicationActivity a : activities) {
                long d = a.getDurationSeconds();
                if (a.getActivityCategory() == AppCategory.PRODUCTIVE) productiveSec += d;
                else if (a.getActivityCategory() == AppCategory.DISTRACTING) distractingSec += d;
                else neutralSec += d;
            }

            // 3. Daily Trend Series
            XYChart.Series<String, Number> trendSeries = new XYChart.Series<>();
            trendSeries.setName("Focus Time");

            for (int i = daysBack - 1; i >= 0; i--) {
                LocalDate d = today.minusDays(i);
                long daySec = 0;
                for (FocusSession s : sessions) {
                    if (s.getStartTime() != null) {
                        LocalDate sDate = s.getStartTime().atZone(ZoneId.systemDefault()).toLocalDate();
                        if (sDate.equals(d)) {
                            daySec += s.getActualDurationSeconds();
                        }
                    }
                }
                String label = daysBack <= 7
                        ? d.format(DateTimeFormatter.ofPattern("EEE"))
                        : d.format(DateTimeFormatter.ofPattern("MM/dd"));
                trendSeries.getData().add(new XYChart.Data<>(label, daySec / 60));
            }

            // 4. Interruption Series
            XYChart.Series<String, Number> interSeries = new XYChart.Series<>();
            interSeries.setName("Interruptions");
            for (Map.Entry<SessionCategory, Integer> entry : categoryInterruptions.entrySet()) {
                interSeries.getData().add(new XYChart.Data<>(entry.getKey().name(), entry.getValue()));
            }

            final long finalTotalFocus = totalFocusSec;
            final long finalMax = maxSessionSec;
            final long finalAvg = avgSessionSec;
            final int finalCount = sessionCount;
            final long finalProd = productiveSec;
            final long finalNeut = neutralSec;
            final long finalDist = distractingSec;

            Platform.runLater(() -> {
                // Image 1 cards exact format
                lblCompletedCount.setText(finalCount + (finalCount == 1 ? " session" : " sessions"));
                lblTotalFocusTime.setText(formatHoursMinutes(finalTotalFocus));
                lblLongestSession.setText(formatHoursMinutes(finalMax));
                lblAvgSession.setText(formatHoursMinutes(finalAvg));

                // Update Trend Chart
                chartDurationTrend.getData().clear();
                chartDurationTrend.getData().add(trendSeries);

                // Update Pie Chart
                chartCategoryDistribution.getData().clear();
                if (finalProd > 0) chartCategoryDistribution.getData().add(new PieChart.Data("Code & Productive", finalProd / 60));
                if (finalNeut > 0) chartCategoryDistribution.getData().add(new PieChart.Data("Neutral", finalNeut / 60));
                if (finalDist > 0) chartCategoryDistribution.getData().add(new PieChart.Data("Distracting", finalDist / 60));
                if (chartCategoryDistribution.getData().isEmpty()) {
                    chartCategoryDistribution.getData().add(new PieChart.Data("No Activity Logged", 1));
                }

                // Update Category Interruptions Chart
                chartCategoryInterruptions.getData().clear();
                chartCategoryInterruptions.getData().add(interSeries);
            });

        } catch (Exception e) {
            logger.error("Error computing analytics metrics: {}", e.getMessage(), e);
        }
    }

    private String formatHoursMinutes(long totalSeconds) {
        if (totalSeconds <= 0) return "0h 0m";
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        return String.format("%dh %dm", hours, minutes);
    }
}
