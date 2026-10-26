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
 * Controller for the Productivity Analytics screen.
 */
public class AnalyticsController implements Refreshable {
    private static final Logger logger = LoggerFactory.getLogger(AnalyticsController.class);

    @FXML private ComboBox<String> cbTimeframe;

    @FXML private Label lblTotalFocusTime;
    @FXML private Label lblCompletedCount;
    @FXML private Label lblInterruptionCount;
    @FXML private Label lblAvgFocusRatio;

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

            // Compute aggregations
            long totalFocusSec = 0;
            int completedCount = 0;
            int totalInterruptions = 0;
            Map<SessionCategory, Integer> categoryInterruptions = new EnumMap<>(SessionCategory.class);
            for (SessionCategory cat : SessionCategory.values()) {
                categoryInterruptions.put(cat, 0);
            }

            for (FocusSession s : sessions) {
                if (s.getStatus().isCompleted()) {
                    completedCount++;
                }
                totalFocusSec += s.getActualDurationSeconds();
                totalInterruptions += s.getInterruptionCount();

                int currentCount = categoryInterruptions.getOrDefault(s.getCategory(), 0);
                categoryInterruptions.put(s.getCategory(), currentCount + s.getInterruptionCount());
            }

            // Application distribution
            long productiveSec = 0;
            long neutralSec = 0;
            long distractingSec = 0;

            for (ApplicationActivity a : activities) {
                long d = a.getDurationSeconds();
                if (a.getActivityCategory() == AppCategory.PRODUCTIVE) productiveSec += d;
                else if (a.getActivityCategory() == AppCategory.DISTRACTING) distractingSec += d;
                else neutralSec += d;
            }

            long totalAppSec = productiveSec + neutralSec + distractingSec;
            double focusRatio = totalAppSec > 0 ? (productiveSec * 100.0) / totalAppSec : 0.0;

            // Trend Series
            XYChart.Series<String, Number> trendSeries = new XYChart.Series<>();
            trendSeries.setName("Focus Minutes");

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

            // Interruption Series
            XYChart.Series<String, Number> interSeries = new XYChart.Series<>();
            interSeries.setName("Interruptions");
            for (Map.Entry<SessionCategory, Integer> entry : categoryInterruptions.entrySet()) {
                interSeries.getData().add(new XYChart.Data<>(entry.getKey().name(), entry.getValue()));
            }

            final long finalTotalFocus = totalFocusSec;
            final int finalCompleted = completedCount;
            final int finalInterruptions = totalInterruptions;
            final double finalRatio = focusRatio;
            final long finalProd = productiveSec;
            final long finalNeut = neutralSec;
            final long finalDist = distractingSec;

            Platform.runLater(() -> {
                lblTotalFocusTime.setText(formatSeconds(finalTotalFocus));
                lblCompletedCount.setText(String.valueOf(finalCompleted));
                lblInterruptionCount.setText(String.valueOf(finalInterruptions));
                lblAvgFocusRatio.setText(String.format("%.1f%%", finalRatio));

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

                // Update Category Interruptions Chart
                chartCategoryInterruptions.getData().clear();
                chartCategoryInterruptions.getData().add(interSeries);
            });

        } catch (Exception e) {
            logger.error("Error computing analytics metrics: {}", e.getMessage(), e);
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
