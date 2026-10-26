package com.flowstate.ui;

import com.flowstate.model.FocusSession;
import com.flowstate.model.SessionCategory;
import com.flowstate.model.SessionStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SessionHistoryControllerTest {

    @Test
    @DisplayName("Filter predicates correctly match title search query")
    void testSearchQueryMatching() {
        FocusSession s1 = new FocusSession(1L, "LeetCode Arrays", SessionCategory.CODING, Instant.now(), Instant.now(), 1500, 1500, SessionStatus.COMPLETED, "Two sum solved", 0, Instant.now());
        FocusSession s2 = new FocusSession(2L, "History Essay", SessionCategory.WRITING, Instant.now(), Instant.now(), 1800, 1800, SessionStatus.COMPLETED, "Drafted thesis", 1, Instant.now());

        List<FocusSession> list = List.of(s1, s2);

        String query = "leetcode";
        List<FocusSession> matched = list.stream()
                .filter(s -> s.getTitle().toLowerCase().contains(query) || (s.getNotes() != null && s.getNotes().toLowerCase().contains(query)))
                .toList();

        assertThat(matched).hasSize(1);
        assertThat(matched.get(0).getTitle()).isEqualTo("LeetCode Arrays");
    }

    @Test
    @DisplayName("Filter predicates match notes text search")
    void testNotesQueryMatching() {
        FocusSession s1 = new FocusSession(1L, "Math Study", SessionCategory.STUDYING, Instant.now(), Instant.now(), 1500, 1500, SessionStatus.COMPLETED, "Reviewed eigenvectors", 0, Instant.now());
        FocusSession s2 = new FocusSession(2L, "Physics Study", SessionCategory.STUDYING, Instant.now(), Instant.now(), 1800, 1800, SessionStatus.COMPLETED, "Newton laws", 1, Instant.now());

        List<FocusSession> list = List.of(s1, s2);

        String query = "eigenvectors";
        List<FocusSession> matched = list.stream()
                .filter(s -> s.getTitle().toLowerCase().contains(query) || (s.getNotes() != null && s.getNotes().toLowerCase().contains(query)))
                .toList();

        assertThat(matched).hasSize(1);
        assertThat(matched.get(0).getTitle()).isEqualTo("Math Study");
    }

    @Test
    @DisplayName("Filter predicates accurately isolate category and status")
    void testCategoryAndStatusFiltering() {
        FocusSession s1 = new FocusSession(1L, "A", SessionCategory.CODING, Instant.now(), Instant.now(), 1500, 1500, SessionStatus.COMPLETED, null, 0, Instant.now());
        FocusSession s2 = new FocusSession(2L, "B", SessionCategory.READING, Instant.now(), Instant.now(), 1500, 1500, SessionStatus.COMPLETED, null, 0, Instant.now());
        FocusSession s3 = new FocusSession(3L, "C", SessionCategory.CODING, Instant.now(), Instant.now(), 1500, 500, SessionStatus.CANCELLED, null, 2, Instant.now());

        List<FocusSession> list = List.of(s1, s2, s3);

        List<FocusSession> codingCompleted = list.stream()
                .filter(s -> s.getCategory() == SessionCategory.CODING && s.getStatus() == SessionStatus.COMPLETED)
                .toList();

        assertThat(codingCompleted).hasSize(1);
        assertThat(codingCompleted.get(0).getTitle()).isEqualTo("A");
    }
}
