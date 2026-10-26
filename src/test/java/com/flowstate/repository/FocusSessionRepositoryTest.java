package com.flowstate.repository;

import com.flowstate.model.FocusSession;
import com.flowstate.model.SessionCategory;
import com.flowstate.model.SessionStatus;
import com.flowstate.service.FocusSessionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FocusSessionRepositoryTest {

    @Test
    @DisplayName("mapRow correctly converts SQL ResultSet row into FocusSession model")
    void testMapRowMapping() throws SQLException {
        Instant startTime = Instant.parse("2026-10-26T10:00:00Z");
        Instant endTime = Instant.parse("2026-10-26T10:45:00Z");
        Instant createdAt = Instant.parse("2026-10-26T09:59:50Z");

        Map<String, Object> columnValues = new HashMap<>();
        columnValues.put("id", 42L);
        columnValues.put("title", "Deep Work - Algorithms");
        columnValues.put("category", "CODING");
        columnValues.put("start_time", Timestamp.from(startTime));
        columnValues.put("end_time", Timestamp.from(endTime));
        columnValues.put("target_duration_seconds", 3000L);
        columnValues.put("actual_duration_seconds", 2700L);
        columnValues.put("status", "COMPLETED");
        columnValues.put("notes", "Solved 3 dynamic programming problems");
        columnValues.put("interruption_count", 2);
        columnValues.put("created_at", Timestamp.from(createdAt));

        ResultSet rsMock = (ResultSet) Proxy.newProxyInstance(
                ResultSet.class.getClassLoader(),
                new Class<?>[]{ResultSet.class},
                new InvocationHandler() {
                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                        if ("getLong".equals(method.getName()) && args != null && args.length == 1) {
                            Object val = columnValues.get((String) args[0]);
                            return val != null ? ((Number) val).longValue() : 0L;
                        }
                        if ("getString".equals(method.getName()) && args != null && args.length == 1) {
                            return (String) columnValues.get((String) args[0]);
                        }
                        if ("getInt".equals(method.getName()) && args != null && args.length == 1) {
                            Object val = columnValues.get((String) args[0]);
                            return val != null ? ((Number) val).intValue() : 0;
                        }
                        if ("getTimestamp".equals(method.getName()) && args != null && args.length == 1) {
                            return (Timestamp) columnValues.get((String) args[0]);
                        }
                        return null;
                    }
                }
        );

        // Instantiate JDBC repository with a dummy or uninitialized manager for mapping test
        FocusSessionRepositoryJDBC repo = new FocusSessionRepositoryJDBC(new com.flowstate.config.DatabaseManager(new com.flowstate.config.AppConfig()));
        FocusSession session = repo.mapRow(rsMock);

        assertThat(session).isNotNull();
        assertThat(session.getId()).isEqualTo(42L);
        assertThat(session.getTitle()).isEqualTo("Deep Work - Algorithms");
        assertThat(session.getCategory()).isEqualTo(SessionCategory.CODING);
        assertThat(session.getStartTime()).isEqualTo(startTime);
        assertThat(session.getEndTime()).isEqualTo(endTime);
        assertThat(session.getTargetDurationSeconds()).isEqualTo(3000L);
        assertThat(session.getActualDurationSeconds()).isEqualTo(2700L);
        assertThat(session.getStatus()).isEqualTo(SessionStatus.COMPLETED);
        assertThat(session.getNotes()).isEqualTo("Solved 3 dynamic programming problems");
        assertThat(session.getInterruptionCount()).isEqualTo(2);
        assertThat(session.getCreatedAt()).isEqualTo(createdAt);
    }

    @Test
    @DisplayName("save rejects null session with NullPointerException")
    void testSaveRejectsNullSession() {
        FocusSessionRepositoryJDBC repo = new FocusSessionRepositoryJDBC(new com.flowstate.config.DatabaseManager(new com.flowstate.config.AppConfig()));
        assertThatThrownBy(() -> repo.save(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("update rejects session without ID with IllegalArgumentException")
    void testUpdateRejectsSessionWithoutId() {
        FocusSessionRepositoryJDBC repo = new FocusSessionRepositoryJDBC(new com.flowstate.config.DatabaseManager(new com.flowstate.config.AppConfig()));
        FocusSession sessionWithoutId = new FocusSession("Test", SessionCategory.OTHER, 1800);
        assertThatThrownBy(() -> repo.update(sessionWithoutId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cannot update session without an ID");
    }

    @Test
    @DisplayName("FocusSessionService invokes repository save on start and update on complete")
    void testServiceLifecycleIntegratesWithRepository() {
        AtomicInteger saveCalls = new AtomicInteger(0);
        AtomicInteger updateCalls = new AtomicInteger(0);
        AtomicBoolean notesPersisted = new AtomicBoolean(false);

        FocusSessionRepository stubRepo = new FocusSessionRepository() {
            private long idCounter = 100L;

            @Override
            public FocusSession save(FocusSession session) {
                saveCalls.incrementAndGet();
                session.setId(idCounter++);
                return session;
            }

            @Override
            public boolean update(FocusSession session) {
                updateCalls.incrementAndGet();
                if ("Great session completed".equals(session.getNotes())) {
                    notesPersisted.set(true);
                }
                return true;
            }

            @Override
            public Optional<FocusSession> findById(Long id) { return Optional.empty(); }
            @Override
            public List<FocusSession> findAll() { return Collections.emptyList(); }
            @Override
            public List<FocusSession> findByDateRange(Instant start, Instant end) { return Collections.emptyList(); }
            @Override
            public List<FocusSession> findRecent(int limit) { return Collections.emptyList(); }
            @Override
            public boolean deleteById(Long id) { return true; }
            @Override
            public long countCompletedSessions() { return 1; }
            @Override
            public long sumTotalFocusSeconds() { return 1500; }
            @Override
            public long sumTodayFocusSeconds(Instant startOfToday) { return 1500; }
        };

        FocusSessionService service = new FocusSessionService(stubRepo);
        FocusSession started = service.startSession("Service Repo Test", SessionCategory.STUDYING, 1800);

        assertThat(started.getId()).isEqualTo(100L);
        assertThat(saveCalls.get()).isEqualTo(1);

        service.pauseSession();
        assertThat(updateCalls.get()).isEqualTo(1);

        service.resumeSession();
        assertThat(updateCalls.get()).isEqualTo(2);

        FocusSession completed = service.completeSession("Great session completed");
        assertThat(completed.getStatus()).isEqualTo(SessionStatus.COMPLETED);
        assertThat(updateCalls.get()).isEqualTo(3);
        assertThat(notesPersisted.get()).isTrue();

        service.shutdown();
    }
}
