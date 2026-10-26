package com.focusforge;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ActivityStorageTest {

    @Test
    @DisplayName("ActivityStorage saves and loads activities using FileInputStream and FileOutputStream")
    void testFileStorageSaveAndLoad(@TempDir Path tempDir) {
        Path jsonFile = tempDir.resolve("activities.json");
        ActivityStorage storage = new ActivityStorage(jsonFile);

        Instant now = Instant.now();
        Activity a1 = new Activity("idea64.exe", "Main.java", now.minusSeconds(1200), Activity.Category.PRODUCTIVE);
        a1.setEndedAt(now);
        a1.setDurationSeconds(1200);
        storage.save(a1);

        Activity a2 = new Activity("discord.exe", "Chat", now.minusSeconds(300), Activity.Category.DISTRACTING);
        a2.setEndedAt(now);
        a2.setDurationSeconds(300);
        storage.save(a2);

        ActivityStorage reloadedStorage = new ActivityStorage(jsonFile);
        List<Activity> list = reloadedStorage.loadActivities();
        assertThat(list).hasSize(2);
        assertThat(list.get(0).getApplicationName()).isEqualTo("idea64.exe");
        assertThat(list.get(1).getApplicationName()).isEqualTo("discord.exe");

        Map<String, Long> top = reloadedStorage.getTopApplicationsDuration(now.minusSeconds(3600), now.plusSeconds(3600), 5);
        assertThat(top).containsEntry("idea64.exe", 1200L);
        assertThat(top).containsEntry("discord.exe", 300L);
    }
}

