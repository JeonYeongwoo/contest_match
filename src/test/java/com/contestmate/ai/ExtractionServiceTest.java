package com.contestmate.ai;

import com.contestmate.collector.RawDocument;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Runs without Spring/DB: exercises only the heuristic (no-LLM) extraction fallback. */
class ExtractionServiceTest {

    @Test
    void fallsBackToHeuristicsWhenLlmNotConfigured() {
        LlmClient llmClient = mock(LlmClient.class);
        when(llmClient.isConfigured()).thenReturn(false);
        ExtractionService service = new ExtractionService(llmClient);

        RawDocument doc = new RawDocument(1L, "https://example.com/a",
                "hash1", "2026 서울 AI 아이디어 공모전",
                "서울시에서 주최하는 AI 공모전입니다. 팀 구성 참가 가능. 접수 마감 2026.11.20. 시상금 500만원.");

        ContestDraft draft = service.extract(doc);

        assertTrue(draft.categories.contains("AI"));
        assertEquals(LocalDate.of(2026, 11, 20), draft.deadline);
        assertTrue(draft.deadlineConfirmed);
        assertEquals(5_000_000L, draft.prizeAmountKrw);
        assertFalse(draft.eligibilityConfirmed);
    }

    @Test
    void leavesDeadlineUnconfirmedWhenNotFound() {
        LlmClient llmClient = mock(LlmClient.class);
        when(llmClient.isConfigured()).thenReturn(false);
        ExtractionService service = new ExtractionService(llmClient);

        RawDocument doc = new RawDocument(1L, "https://example.com/b", "hash2",
                "디자인 공모전", "자세한 일정은 추후 공지됩니다.");

        ContestDraft draft = service.extract(doc);

        assertNull(draft.deadline);
        assertFalse(draft.deadlineConfirmed);
    }
}
