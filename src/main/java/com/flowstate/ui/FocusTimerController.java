package com.flowstate.ui;

import com.flowstate.model.FocusSession;
import com.flowstate.model.SessionCategory;
import com.flowstate.model.SessionStatus;
import com.flowstate.service.FocusSessionListener;
import com.flowstate.service.FocusSessionService;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * Controller managing the Focus Timer UI, real-time ticking listeners, and session notes dialogs.
 */
public class FocusTimerController implements FocusSessionListener, Refreshable {
    private static final Logger logger = LoggerFactory.getLogger(FocusTimerController.class);

    @FXML private Label lblSessionStatusBadge;
    @FXML private Label lblActiveCategory;
    @FXML private Label lblActiveTitle;

    @FXML private Label lblTimerDisplay;
    @FXML private Label lblTimerSubtext;
    @FXML private ProgressBar pbSessionProgress;

    @FXML private HBox activeMetricsRow;
    @FXML private Label lblElapsedDetail;
    @FXML private Label lblInterruptionCount;

    @FXML private Button btnStartSession;
    @FXML private Button btnPauseSession;
    @FXML private Button btnResumeSession;
    @FXML private Button btnCompleteSession;
    @FXML private Button btnCancelSession;

    @FXML private VBox configCard;
    @FXML private TextField txtSessionTitle;
    @FXML private ComboBox<SessionCategory> cbCategory;

    private final FocusSessionService sessionService;
    private long selectedDurationSeconds = 25 * 60; // 25m default

    public FocusTimerController() {
        this(FocusSessionService.getInstance());
    }

    public FocusTimerController(FocusSessionService sessionService) {
        this.sessionService = sessionService;
    }

    @FXML
    public void initialize() {
        cbCategory.setItems(FXCollections.observableArrayList(SessionCategory.values()));
        cbCategory.setValue(SessionCategory.CODING);

        sessionService.addListener(this);

        updateUiState();
    }

    @Override
    public void refreshData() {
        updateUiState();
    }

    private void updateUiState() {
        FocusSession active = sessionService.getActiveSession();
        if (active != null && active.getStatus().isActive()) {
            boolean isRunning = active.getStatus() == SessionStatus.RUNNING;
            lblSessionStatusBadge.setText(isRunning ? "RUNNING" : "PAUSED");
            lblSessionStatusBadge.setStyle(isRunning
                    ? "-fx-background-color: rgba(16, 185, 129, 0.2); -fx-text-fill: #10B981; -fx-padding: 4px 12px; -fx-background-radius: 12px; -fx-font-weight: bold; -fx-font-size: 12px;"
                    : "-fx-background-color: rgba(245, 158, 11, 0.2); -fx-text-fill: #F59E0B; -fx-padding: 4px 12px; -fx-background-radius: 12px; -fx-font-weight: bold; -fx-font-size: 12px;");

            lblActiveCategory.setText(active.getCategory().name());
            lblActiveTitle.setText(active.getTitle());

            btnStartSession.setVisible(false);
            btnStartSession.setManaged(false);

            btnPauseSession.setVisible(isRunning);
            btnPauseSession.setManaged(isRunning);

            btnResumeSession.setVisible(!isRunning);
            btnResumeSession.setManaged(!isRunning);

            btnCompleteSession.setVisible(true);
            btnCompleteSession.setManaged(true);

            btnCancelSession.setVisible(true);
            btnCancelSession.setManaged(true);

            activeMetricsRow.setVisible(true);
            activeMetricsRow.setManaged(true);
            configCard.setDisable(true);

            lblInterruptionCount.setText(String.valueOf(active.getInterruptionCount()));
        } else {
            lblSessionStatusBadge.setText("READY");
            lblSessionStatusBadge.setStyle("-fx-background-color: rgba(139, 92, 246, 0.2); -fx-text-fill: #8B5CF6; -fx-padding: 4px 12px; -fx-background-radius: 12px; -fx-font-weight: bold; -fx-font-size: 12px;");
            lblActiveCategory.setText(cbCategory.getValue() != null ? cbCategory.getValue().name() : "CODING");
            lblActiveTitle.setText("Deep Work Session");

            lblTimerDisplay.setText(formatTimer(selectedDurationSeconds));
            lblTimerSubtext.setText("Target: " + (selectedDurationSeconds / 60) + " minutes  |  Remaining: " + formatTimer(selectedDurationSeconds));
            pbSessionProgress.setProgress(0.0);

            btnStartSession.setVisible(true);
            btnStartSession.setManaged(true);

            btnPauseSession.setVisible(false);
            btnPauseSession.setManaged(false);

            btnResumeSession.setVisible(false);
            btnResumeSession.setManaged(false);

            btnCompleteSession.setVisible(false);
            btnCompleteSession.setManaged(false);

            btnCancelSession.setVisible(false);
            btnCancelSession.setManaged(false);

            activeMetricsRow.setVisible(false);
            activeMetricsRow.setManaged(false);
            configCard.setDisable(false);
        }
    }

    @FXML
    public void handleStartSession(ActionEvent event) {
        String title = txtSessionTitle.getText();
        if (title == null || title.isBlank()) {
            title = "Focus Session";
        }
        SessionCategory category = cbCategory.getValue() != null ? cbCategory.getValue() : SessionCategory.CODING;

        try {
            sessionService.startSession(title, category, selectedDurationSeconds);
            updateUiState();
        } catch (Exception e) {
            showAlert("Could not start session", e.getMessage(), Alert.AlertType.ERROR);
        }
    }

    @FXML
    public void handlePauseSession(ActionEvent event) {
        try {
            sessionService.pauseSession();
            updateUiState();
        } catch (Exception e) {
            showAlert("Pause Error", e.getMessage(), Alert.AlertType.WARNING);
        }
    }

    @FXML
    public void handleResumeSession(ActionEvent event) {
        try {
            sessionService.resumeSession();
            updateUiState();
        } catch (Exception e) {
            showAlert("Resume Error", e.getMessage(), Alert.AlertType.WARNING);
        }
    }

    @FXML
    public void handleCompleteSession(ActionEvent event) {
        TextInputDialog dialog = new TextInputDialog("");
        dialog.setTitle("Complete Focus Session");
        dialog.setHeaderText("Great work! Add optional reflections or notes:");
        dialog.setContentText("Notes:");
        dialog.getDialogPane().getStylesheets().add(getClass().getResource("/css/flowstate-theme.css").toExternalForm());

        Optional<String> result = dialog.showAndWait();
        String notes = result.orElse("");

        try {
            FocusSession completed = sessionService.completeSession(notes);
            updateUiState();
            showAlert("Session Finished!",
                    String.format("Recorded %dm %ds of deep work with %d interruptions.",
                            completed.getActualDurationSeconds() / 60,
                            completed.getActualDurationSeconds() % 60,
                            completed.getInterruptionCount()),
                    Alert.AlertType.INFORMATION);
        } catch (Exception e) {
            showAlert("Completion Error", e.getMessage(), Alert.AlertType.ERROR);
        }
    }

    @FXML
    public void handleCancelSession(ActionEvent event) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, "Discard this active focus session?", ButtonType.YES, ButtonType.NO);
        confirm.setTitle("Cancel Session");
        confirm.getDialogPane().getStylesheets().add(getClass().getResource("/css/flowstate-theme.css").toExternalForm());

        Optional<ButtonType> res = confirm.showAndWait();
        if (res.isPresent() && res.get() == ButtonType.YES) {
            try {
                sessionService.cancelSession();
                updateUiState();
            } catch (Exception e) {
                showAlert("Cancel Error", e.getMessage(), Alert.AlertType.WARNING);
            }
        }
    }

    @FXML public void handlePreset15(ActionEvent e) { setPresetDuration(15); }
    @FXML public void handlePreset25(ActionEvent e) { setPresetDuration(25); }
    @FXML public void handlePreset45(ActionEvent e) { setPresetDuration(45); }
    @FXML public void handlePreset60(ActionEvent e) { setPresetDuration(60); }
    @FXML public void handlePreset90(ActionEvent e) { setPresetDuration(90); }

    private void setPresetDuration(int minutes) {
        this.selectedDurationSeconds = (long) minutes * 60;
        updateUiState();
    }

    private String formatTimer(long totalSeconds) {
        long m = totalSeconds / 60;
        long s = totalSeconds % 60;
        return String.format("%02d:%02d", m, s);
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

    // FocusSessionListener callbacks
    @Override
    public void onSessionStarted(FocusSession session) {
        Platform.runLater(this::updateUiState);
    }

    @Override
    public void onSessionPaused(FocusSession session) {
        Platform.runLater(this::updateUiState);
    }

    @Override
    public void onSessionResumed(FocusSession session) {
        Platform.runLater(this::updateUiState);
    }

    @Override
    public void onSessionCompleted(FocusSession session) {
        Platform.runLater(this::updateUiState);
    }

    @Override
    public void onSessionCancelled(FocusSession session) {
        Platform.runLater(this::updateUiState);
    }

    @Override
    public void onTimerTick(FocusSession session, long elapsedSeconds, long remainingSeconds) {
        Platform.runLater(() -> {
            lblTimerDisplay.setText(formatTimer(remainingSeconds));
            lblTimerSubtext.setText("Target: " + (session.getTargetDurationSeconds() / 60) + "m  |  Remaining: " + formatTimer(remainingSeconds));
            lblElapsedDetail.setText(formatTimer(elapsedSeconds));
            lblInterruptionCount.setText(String.valueOf(session.getInterruptionCount()));

            double progress = session.getTargetDurationSeconds() > 0
                    ? Math.min(1.0, (double) elapsedSeconds / session.getTargetDurationSeconds())
                    : 0.0;
            pbSessionProgress.setProgress(progress);
        });
    }
}
