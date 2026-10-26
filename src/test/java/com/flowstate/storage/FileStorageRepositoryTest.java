package com.flowstate.storage;

import com.flowstate.model.*;
import com.flowstate.repository.file.ActivityRepositoryFile;
import com.flowstate.repository.file.ApplicationPreferenceRepositoryFile;
import com.flowstate.repository.file.FocusSessionRepositoryFile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class FileStorageRepositoryTest {

    @Test
    @DisplayName("FocusSessionRepositoryFile correctly persists and retrieves sessions from JSON file")
    void testFocusSessionFilePersistence(@TempDir Path tempDir) {
        FileStorageManager storage = new FileStorageManager(tempDir);
        FocusSessionRepositoryFile repo = new FocusSessionRepositoryFile(storage);

        FocusSession s1 = new FocusSession("Algorithms Deep Work", SessionCategory.CODING, 1800);
        s1.start(Instant.now().minusSeconds(1800));
        s1.complete(Instant.now());
        repo.save(s1);

        assertThat(s1.getId()).isNotNull();
        assertThat(Files.exists(storage.getSessionsFilePath())).isTrue();

        // Create a new instance pointing to same file to verify deserialization
        FocusSessionRepositoryFile reloadedRepo = new FocusSessionRepositoryFile(storage);
        Optional<FocusSession> found = reloadedRepo.findById(s1.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getTitle()).isEqualTo("Algorithms Deep Work");
        assertThat(found.get().getCategory()).isEqualTo(SessionCategory.CODING);
        assertThat(found.get().getStatus()).isEqualTo(SessionStatus.COMPLETED);

        // Aggregations
        assertThat(reloadedRepo.countCompletedSessions()).isEqualTo(1);
        assertThat(reloadedRepo.sumTotalFocusSeconds()).isEqualTo(1800);
    }

    @Test
    @DisplayName("ActivityRepositoryFile saves activities and aggregates top applications accurately")
    void testActivityFilePersistence(@TempDir Path tempDir) {
        FileStorageManager storage = new FileStorageManager(tempDir);
        ActivityRepositoryFile repo = new ActivityRepositoryFile(storage);

        Instant now = Instant.now();
        ApplicationActivity act1 = new ApplicationActivity("idea64.exe", "Main.java", now.minusSeconds(1200), null, AppCategory.PRODUCTIVE);
        act1.setEndedAt(now);
        act1.setDurationSeconds(1200);
        repo.save(act1);

        ApplicationActivity act2 = new ApplicationActivity("chrome.exe", "Docs", now.minusSeconds(600), null, AppCategory.NEUTRAL);
        act2.setEndedAt(now);
        act2.setDurationSeconds(600);
        repo.save(act2);

        assertThat(Files.exists(storage.getActivitiesFilePath())).isTrue();

        Map<String, Long> topApps = repo.getTopApplicationsDuration(now.minusSeconds(3600), now.plusSeconds(60), 5);
        assertThat(topApps).containsKey("idea64.exe");
        assertThat(topApps.get("idea64.exe")).isEqualTo(1200L);
        assertThat(topApps.get("chrome.exe")).isEqualTo(600L);
    }

    @Test
    @DisplayName("ApplicationPreferenceRepositoryFile persists rules and handles upserts and exclusions")
    void testPreferenceFilePersistence(@TempDir Path tempDir) {
        FileStorageManager storage = new FileStorageManager(tempDir);
        ApplicationPreferenceRepositoryFile repo = new ApplicationPreferenceRepositoryFile(storage);

        repo.upsert("discord.exe", "Discord", AppCategory.DISTRACTING, false);
        repo.upsert("keepass.exe", "KeePass", AppCategory.NEUTRAL, true);

        assertThat(Files.exists(storage.getPreferencesFilePath())).isTrue();

        ApplicationPreferenceRepositoryFile reloaded = new ApplicationPreferenceRepositoryFile(storage);
        Optional<ApplicationPreference> discord = reloaded.findByIdentifier("discord.exe");
        assertThat(discord).isPresent();
        assertThat(discord.get().getCategory()).isEqualTo(AppCategory.DISTRACTING);

        List<String> excluded = reloaded.findExcludedIdentifiers();
        assertThat(excluded).contains("keepass.exe");
    }
}
