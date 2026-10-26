package com.flowstate.ui;

import com.flowstate.config.DatabaseManager;
import com.flowstate.model.FocusSession;
import com.flowstate.model.SessionCategory;
import com.flowstate.model.SessionStatus;
import com.flowstate.repository.ActivityRepository;
import com.flowstate.repository.ActivityRepositoryJDBC;
import com.flowstate.repository.FocusSessionRepository;
import com.flowstate.repository.FocusSessionRepositoryJDBC;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Controller for the Session History screen with search, filtering, notes editor, and deletion.
 */
public class SessionHistoryController implements Refreshable {
    private static final Logger logger = LoggerFactory.getLogger(SessionHistoryController.class);

    @FXML private TextField txtSearch;
    @FXML private ComboBox<String> cbCategoryFilter;
    @FXML private ComboBox<String> cbStatusFilter;

    @FXML private TableView<FocusSession> tableSessions;
    @FXML private TableColumn<FocusSession, String> colId;
    @FXML private TableColumn<FocusSession, String> colTitle;
    @FXML private TableColumn<FocusSession, String> colCategory;
    @FXML private TableColumn<FocusSession, String> colStartTime;
    @FXML private TableColumn<FocusSession, String> colTargetDuration;
    @FXML private TableColumn<FocusSession, String> colActualDuration;
    @FXML private TableColumn<FocusSession, String> colInterruptions;
    @FXML private TableColumn<FocusSession, String> colStatus;
    @FXML private TableColumn<FocusSession, Void> colActions;

    private final FocusSessionRepository sessionRepository;
    private final ActivityRepository activityRepository;

    private final ObservableList<FocusSession> masterList = FXCollections.observableArrayList();
    private FilteredList<FocusSession> filteredList;

    public SessionHistoryController() {
        this(com.flowstate.storage.RepositoryFactory.getFocusSessionRepository(),
             com.flowstate.storage.RepositoryFactory.getActivityRepository());
    }

    public SessionHistoryController(FocusSessionRepository sessionRepository, ActivityRepository activityRepository) {
        this.sessionRepository = sessionRepository;
        this.activityRepository = activityRepository;
    }

    @FXML
    public void initialize() {
        setupFilters();
        setupTableColumns();
        refreshData();
    }

    private void setupFilters() {
        cbCategoryFilter.getItems().add("ALL CATEGORIES");
        for (SessionCategory cat : SessionCategory.values()) {
            cbCategoryFilter.getItems().add(cat.name());
        }
        cbCategoryFilter.setValue("ALL CATEGORIES");

        cbStatusFilter.getItems().addAll("ALL STATUSES", "COMPLETED", "CANCELLED", "PENDING");
        cbStatusFilter.setValue("ALL STATUSES");

        filteredList = new FilteredList<>(masterList, p -> true);
        tableSessions.setItems(filteredList);

        txtSearch.textProperty().addListener((obs, oldVal, newVal) -> applyFilter());
        cbCategoryFilter.valueProperty().addListener((obs, oldVal, newVal) -> applyFilter());
        cbStatusFilter.valueProperty().addListener((obs, oldVal, newVal) -> applyFilter());
    }

    public void applyFilter() {
        String query = txtSearch.getText() != null ? txtSearch.getText().trim().toLowerCase() : "";
        String catFilter = cbCategoryFilter.getValue();
        String statFilter = cbStatusFilter.getValue();

        filteredList.setPredicate(session -> {
            // Search query filter
            if (!query.isEmpty()) {
                boolean matchTitle = session.getTitle() != null && session.getTitle().toLowerCase().contains(query);
                boolean matchNotes = session.getNotes() != null && session.getNotes().toLowerCase().contains(query);
                if (!matchTitle && !matchNotes) {
                    return false;
                }
            }

            // Category filter
            if (catFilter != null && !catFilter.equals("ALL CATEGORIES")) {
                if (session.getCategory() == null || !session.getCategory().name().equalsIgnoreCase(catFilter)) {
                    return false;
                }
            }

            // Status filter
            if (statFilter != null && !statFilter.equals("ALL STATUSES")) {
                if (session.getStatus() == null || !session.getStatus().name().equalsIgnoreCase(statFilter)) {
                    return false;
                }
            }

            return true;
        });
    }

    private void setupTableColumns() {
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

        colId.setCellValueFactory(data -> new SimpleStringProperty(String.valueOf(data.getValue().getId())));
        colTitle.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getTitle()));
        colCategory.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getCategory().name()));
        colStartTime.setCellValueFactory(data -> {
            if (data.getValue().getStartTime() != null) {
                return new SimpleStringProperty(data.getValue().getStartTime().atZone(ZoneId.systemDefault()).format(dtf));
            }
            return new SimpleStringProperty("-");
        });
        colTargetDuration.setCellValueFactory(data -> new SimpleStringProperty(formatMinutes(data.getValue().getTargetDurationSeconds())));
        colActualDuration.setCellValueFactory(data -> new SimpleStringProperty(formatSeconds(data.getValue().getActualDurationSeconds())));
        colInterruptions.setCellValueFactory(data -> new SimpleStringProperty(String.valueOf(data.getValue().getInterruptionCount())));
        colStatus.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getStatus().name()));

        // Actions Column
        colActions.setCellFactory(param -> new TableCell<>() {
            private final Button btnNotes = new Button("📝 Notes");
            private final Button btnDelete = new Button("🗑 Delete");
            private final HBox pane = new HBox(8, btnNotes, btnDelete);

            {
                pane.setAlignment(Pos.CENTER);
                btnNotes.getStyleClass().add("btn-secondary");
                btnNotes.setStyle("-fx-font-size: 11px; -fx-padding: 4px 8px;");

                btnDelete.getStyleClass().add("btn-danger");
                btnDelete.setStyle("-fx-font-size: 11px; -fx-padding: 4px 8px;");

                btnNotes.setOnAction(event -> {
                    FocusSession session = getTableView().getItems().get(getIndex());
                    handleEditNotes(session);
                });

                btnDelete.setOnAction(event -> {
                    FocusSession session = getTableView().getItems().get(getIndex());
                    handleDeleteSession(session);
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : pane);
            }
        });

        tableSessions.setPlaceholder(new Label("No focus sessions match your search criteria."));
    }

    @Override
    public void refreshData() {
        if (!com.flowstate.storage.RepositoryFactory.isFileStorageMode() && !DatabaseManager.getInstance().isHealthy()) return;

        CompletableFuture.runAsync(() -> {
            try {
                List<FocusSession> all = sessionRepository.findAll();
                Platform.runLater(() -> {
                    masterList.clear();
                    masterList.addAll(all);
                });
            } catch (Exception e) {
                logger.error("Failed to load session history: {}", e.getMessage());
            }
        });
    }

    @FXML
    public void handleRefresh(ActionEvent event) {
        refreshData();
    }

    private void handleEditNotes(FocusSession session) {
        TextInputDialog dialog = new TextInputDialog(session.getNotes() != null ? session.getNotes() : "");
        dialog.setTitle("Session Notes");
        dialog.setHeaderText("Focus Session: " + session.getTitle());
        dialog.setContentText("Edit session reflections & notes:");
        dialog.getDialogPane().getStylesheets().add(getClass().getResource("/css/flowstate-theme.css").toExternalForm());

        Optional<String> result = dialog.showAndWait();
        if (result.isPresent()) {
            String updatedNotes = result.get().trim();
            session.setNotes(updatedNotes);
            CompletableFuture.runAsync(() -> {
                try {
                    sessionRepository.update(session);
                    Platform.runLater(tableSessions::refresh);
                    logger.info("Updated notes for session id {}", session.getId());
                } catch (Exception e) {
                    logger.error("Failed to update session notes: {}", e.getMessage());
                }
            });
        }
    }

    private void handleDeleteSession(FocusSession session) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Confirm Deletion");
        alert.setHeaderText("Delete session '" + session.getTitle() + "'?");
        alert.setContentText("This will permanently delete this session and all its associated activity intervals. This cannot be undone.");
        alert.getDialogPane().getStylesheets().add(getClass().getResource("/css/flowstate-theme.css").toExternalForm());

        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            CompletableFuture.runAsync(() -> {
                try {
                    // Cascading delete
                    activityRepository.deleteBySessionId(session.getId());
                    sessionRepository.deleteById(session.getId());
                    Platform.runLater(() -> masterList.remove(session));
                    logger.info("Permanently deleted session id {}", session.getId());
                } catch (Exception e) {
                    logger.error("Failed to delete session: {}", e.getMessage());
                }
            });
        }
    }

    private String formatMinutes(long totalSeconds) {
        return (totalSeconds / 60) + "m";
    }

    private String formatSeconds(long totalSeconds) {
        if (totalSeconds <= 0) return "0m";
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        if (hours > 0) return String.format("%dh %dm", hours, minutes);
        if (minutes > 0) return String.format("%dm %ds", minutes, seconds);
        return String.format("%ds", seconds);
    }
}
