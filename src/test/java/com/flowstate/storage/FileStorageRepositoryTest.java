package com.flowstate.storage;

import com.flowstate.model.AppCategory;
import com.flowstate.model.ApplicationActivity;
import com.flowstate.model.ApplicationPreference;
import com.flowstate.repository.file.ActivityRepositoryFile;
import com.flowstate.repository.file.ApplicationPreferenceRepositoryFile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class FileStorageRepositoryTest {

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

        ApplicationActivity act2 = new ApplicationActivity("chrome.exe", "Stack Overflow", now.minusSeconds(600), null, AppCategory.NEUTRAL);
        act2.setEndedAt(now);
        act2.setDurationSeconds(600);
        repo.save(act2);

        List<ApplicationActivity> all = repo.findByDateRange(now.minusSeconds(3600), now.plusSeconds(3600));
        assertThat(all).hasSize(2);

        Map<String, Long> top = repo.getTopApplicationsDuration(now.minusSeconds(3600), now.plusSeconds(3600), 5);
        assertThat(top).containsEntry("idea64.exe", 1200L);
        assertThat(top).containsEntry("chrome.exe", 600L);
    }

    @Test
    @DisplayName("ApplicationPreferenceRepositoryFile persists classification rules and exclusion flags")
    void testPreferenceFilePersistence(@TempDir Path tempDir) throws Exception {
        FileStorageManager storage = new FileStorageManager(tempDir);
        ApplicationPreferenceRepositoryFile repo = new ApplicationPreferenceRepositoryFile(storage);

        repo.upsert("discord.exe", "Discord", AppCategory.DISTRACTING, false);
        repo.upsert("keepassxc.exe", "KeePassXC Password Manager", AppCategory.NEUTRAL, true);

        Optional<ApplicationPreference> foundDiscord = repo.findByIdentifier("discord.exe");
        assertThat(foundDiscord).isPresent();
        assertThat(foundDiscord.get().getCategory()).isEqualTo(AppCategory.DISTRACTING);
        assertThat(foundDiscord.get().isExcluded()).isFalse();

        Optional<ApplicationPreference> foundKeePass = repo.findByIdentifier("keepassxc.exe");
        assertThat(foundKeePass).isPresent();
        assertThat(foundKeePass.get().isExcluded()).isTrue();
    }
}
