package com.flowstate.repository;

import com.flowstate.config.AppConfig;
import com.flowstate.config.DatabaseManager;
import com.flowstate.model.AppCategory;
import com.flowstate.model.ApplicationActivity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ActivityRepositoryTest {

    @Test
    @DisplayName("mapRow correctly converts SQL ResultSet row into ApplicationActivity model")
    void testMapRowMapping() throws SQLException {
        Instant startedAt = Instant.parse("2026-10-26T11:00:00Z");
        Instant endedAt = Instant.parse("2026-10-26T11:20:00Z");

        Map<String, Object> columnValues = new HashMap<>();
        columnValues.put("id", 101L);
        columnValues.put("application_name", "code.exe");
        columnValues.put("window_title", "FlowState - VS Code");
        columnValues.put("started_at", Timestamp.from(startedAt));
        columnValues.put("ended_at", Timestamp.from(endedAt));
        columnValues.put("duration_seconds", 1200L);
        columnValues.put("session_id", 42L);
        columnValues.put("activity_category", "PRODUCTIVE");

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
                        if ("getTimestamp".equals(method.getName()) && args != null && args.length == 1) {
                            return (Timestamp) columnValues.get((String) args[0]);
                        }
                        if ("wasNull".equals(method.getName())) {
                            return false;
                        }
                        return null;
                    }
                }
        );

        ActivityRepositoryJDBC repo = new ActivityRepositoryJDBC(new DatabaseManager(new AppConfig()));
        ApplicationActivity activity = repo.mapRow(rsMock);

        assertThat(activity).isNotNull();
        assertThat(activity.getId()).isEqualTo(101L);
        assertThat(activity.getApplicationName()).isEqualTo("code.exe");
        assertThat(activity.getWindowTitle()).isEqualTo("FlowState - VS Code");
        assertThat(activity.getStartedAt()).isEqualTo(startedAt);
        assertThat(activity.getEndedAt()).isEqualTo(endedAt);
        assertThat(activity.getDurationSeconds()).isEqualTo(1200L);
        assertThat(activity.getSessionId()).isEqualTo(42L);
        assertThat(activity.getActivityCategory()).isEqualTo(AppCategory.PRODUCTIVE);
    }

    @Test
    @DisplayName("save rejects null activity with NullPointerException")
    void testSaveRejectsNullActivity() {
        ActivityRepositoryJDBC repo = new ActivityRepositoryJDBC(new DatabaseManager(new AppConfig()));
        assertThatThrownBy(() -> repo.save(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("update rejects activity without ID with IllegalArgumentException")
    void testUpdateRejectsActivityWithoutId() {
        ActivityRepositoryJDBC repo = new ActivityRepositoryJDBC(new DatabaseManager(new AppConfig()));
        ApplicationActivity activityWithoutId = new ApplicationActivity("chrome.exe", "Web", Instant.now(), null, AppCategory.NEUTRAL);
        assertThatThrownBy(() -> repo.update(activityWithoutId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cannot update activity without an ID");
    }
}
