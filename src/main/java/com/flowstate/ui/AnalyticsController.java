package com.flowstate.ui;

import com.flowstate.model.AppCategory;
import com.flowstate.model.ApplicationActivity;
import com.flowstate.repository.ActivityRepository;
import com.flowstate.storage.RepositoryFactory;
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

    private final ActivityRepository activityRepository;

    public AnalyticsController() {
        this(RepositoryFactory.getActivityRepository());
    }

    public AnalyticsController(ActivityRepository activityRepository) {
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

            List<ApplicationActivity> activities = activityRepository.findByDateRange(start, end);

            long productiveSec = 0;
            long neutralSec = 0;
            long distractingSec = 0;
            long longestIntervalSec = 0;

            for (ApplicationActivity a : activities) {
                long d = a.getDurationSeconds();
                if (a.getActivityCategory() == AppCategory.PRODUCTIVE) productiveSec += d;
                else if (a.getActivityCategory() == AppCategory.DISTRACTING) distractingSec += d;
                else neutralSec += d;

                if (d > longestIntervalSec) {
                    longestIntervalSec = d;
                }
            }

            long totalSec = productiveSec + neutralSec + distractingSec;
            double focusRatio = totalSec > 0 ? (productiveSec * 100.0) / totalSec : 0.0;
            int totalActivitiesCount = activities.size();

            // Daily Trend Series
            XYChart.Series<String, Number> trendSeries = new XYChart.Series<>();
            trendSeries.setName("Tracked Minutes");

            for (int i = daysBack - 1; i >= 0; i--) {
                LocalDate d = today.minusDays(i);
                Instant dStart = d.atStartOfDay(ZoneId.systemDefault()).toInstant();
                Instant dEnd = d.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant();

                long daySec = activities.stream()
                        .filter(a -> a.getStartedAt() != null && !a.getStartedAt().isBefore(dStart) && a.getStartedAt().isBefore(dEnd))
                        .mapToLong(ApplicationActivity::getDurationSeconds)
                        .sum();

                String label = daysBack <= 7
                        ? d.format(DateTimeFormatter.ofPattern("EEE"))
                        : d.format(DateTimeFormatter.ofPattern("MM/dd"));
                trendSeries.getData().add(new XYChart.Data<>(label, daySec / 60));
            }

            // Top categories series
            XYChart.Series<String, Number> catSeries = new XYChart.Series<>();
            catSeries.setName("Minutes");
            catSeries.getData().add(new XYChart.Data<>("Productive", productiveSec / 60));
            catSeries.getData().add(new XYChart.Data<>("Neutral", neutralSec / 60));
            catSeries.getData().add(new XYChart.Data<>("Distracting", distractingSec / 60));

            final long finalTotal = totalSec;
            final long finalProd = productiveSec;
            final long finalDist = distractingSec;
            final long finalLongest = longestIntervalSec;
            final double finalRatio = focusRatio;
            final int finalCount = totalActivitiesCount;
            final long finalNeut = neutralSec;

            Platform.runLater(() -> {
                // Image 1 cards exact format
                lblCompletedCount.setText(finalCount + " logs");
                lblTotalFocusTime.setText(formatHoursMinutes(finalTotal));
                lblLongestSession.setText(formatHoursMinutes(finalProd));
                lblAvgSession.setText(String.format("%.1f%%", finalRatio));

                // Update Trend Chart
                chartDurationTrend.getData().clear();
                chartDurationTrend.getData().add(trendSeries);

                // Update Pie Chart
                chartCategoryDistribution.getData().clear();
                if (finalProd > 0) chartCategoryDistribution.getData().add(new PieChart.Data("Productive", finalProd / 60));
                if (finalNeut > 0) chartCategoryDistribution.getData().add(new PieChart.Data("Neutral", finalNeut / 60));
                if (finalDist > 0) chartCategoryDistribution.getData().add(new PieChart.Data("Distracting", finalDist / 60));
                if (chartCategoryDistribution.getData().isEmpty()) {
                    chartCategoryDistribution.getData().add(new PieChart.Data("No Activity Logged", 1));
                }

                // Update Category Chart
                chartCategoryInterruptions.getData().clear();
                chartCategoryInterruptions.getData().add(catSeries);
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
