package com.flowstate.repository;

import com.flowstate.config.AppConfig;
import com.flowstate.config.DatabaseManager;
import com.flowstate.model.AppCategory;
import com.flowstate.model.ApplicationPreference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApplicationPreferenceRepositoryTest {

    @Test
    @DisplayName("mapRow correctly converts SQL ResultSet row into ApplicationPreference model")
    void testMapRowMapping() throws SQLException {
        Map<String, Object> columnValues = new HashMap<>();
        columnValues.put("id", 12L);
        columnValues.put("application_identifier", "discord.exe");
        columnValues.put("display_name", "Discord");
        columnValues.put("category", "DISTRACTING");
        columnValues.put("is_excluded", false);

        ResultSet rsMock = (ResultSet) Proxy.newProxyInstance(
                ResultSet.class.getClassLoader(),
                new Class<?>[]{ResultSet.class},
                new InvocationHandler() {
                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) {
                        if ("getLong".equals(method.getName()) && args != null && args.length == 1) {
                            Object val = columnValues.get((String) args[0]);
                            return val != null ? ((Number) val).longValue() : 0L;
                        }
                        if ("getString".equals(method.getName()) && args != null && args.length == 1) {
                            return (String) columnValues.get((String) args[0]);
                        }
                        if ("getBoolean".equals(method.getName()) && args != null && args.length == 1) {
                            Object val = columnValues.get((String) args[0]);
                            return Boolean.TRUE.equals(val);
                        }
                        return null;
                    }
                }
        );

        ApplicationPreferenceRepositoryJDBC repo = new ApplicationPreferenceRepositoryJDBC(new DatabaseManager(new AppConfig()));
        ApplicationPreference pref = repo.mapRow(rsMock);

        assertThat(pref).isNotNull();
        assertThat(pref.getId()).isEqualTo(12L);
        assertThat(pref.getApplicationIdentifier()).isEqualTo("discord.exe");
        assertThat(pref.getDisplayName()).isEqualTo("Discord");
        assertThat(pref.getCategory()).isEqualTo(AppCategory.DISTRACTING);
        assertThat(pref.isExcluded()).isFalse();
    }

    @Test
    @DisplayName("save rejects null preference with NullPointerException")
    void testSaveRejectsNullPreference() {
        ApplicationPreferenceRepositoryJDBC repo = new ApplicationPreferenceRepositoryJDBC(new DatabaseManager(new AppConfig()));
        assertThatThrownBy(() -> repo.save(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("update rejects preference without identifier with IllegalArgumentException")
    void testUpdateRejectsPreferenceWithoutIdentifier() {
        ApplicationPreferenceRepositoryJDBC repo = new ApplicationPreferenceRepositoryJDBC(new DatabaseManager(new AppConfig()));
        ApplicationPreference prefWithoutId = new ApplicationPreference(null, "Test", AppCategory.NEUTRAL, false);
        prefWithoutId.setApplicationIdentifier(null);

        assertThatThrownBy(() -> repo.update(prefWithoutId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cannot update preference without applicationIdentifier");
    }
}
