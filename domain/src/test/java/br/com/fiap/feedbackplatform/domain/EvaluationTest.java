package br.com.fiap.feedbackplatform.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Testes da Evaluation conforme ADRs 0001, 0005, 0008 e 0009.
 */
class EvaluationTest {
    private static final Instant MONDAY = Instant.parse("2026-11-09T12:00:00Z");
    private static final ZoneId BRASILIA = ZoneId.of("America/Sao_Paulo");

    @Test
    void stripsAndValidatesDescriptionByCodePoint() {
        assertEquals("texto", Evaluation.receive(UUID.randomUUID(), "  texto  ", new Score(5), MONDAY).description());
        assertThrows(NullPointerException.class,
                () -> Evaluation.receive(UUID.randomUUID(), null, new Score(5), MONDAY));
        assertThrows(IllegalArgumentException.class,
                () -> Evaluation.receive(UUID.randomUUID(), "", new Score(5), MONDAY));
        assertThrows(IllegalArgumentException.class,
                () -> Evaluation.receive(UUID.randomUUID(), " \t\n ", new Score(5), MONDAY));
        assertEquals(1_000, Evaluation.receive(UUID.randomUUID(), "x".repeat(1_000),
                new Score(5), MONDAY).description().codePointCount(0, 1_000));
        assertThrows(IllegalArgumentException.class,
                () -> Evaluation.receive(UUID.randomUUID(), "x".repeat(1_001), new Score(5), MONDAY));
        String emojis = "😀".repeat(1_000);
        assertEquals(1_000, Evaluation.receive(UUID.randomUUID(), emojis, new Score(5), MONDAY)
                .description().codePointCount(0, emojis.length()));
    }

    @Test
    void validatesNoticeMarkerAndPreservesPersistedUrgency() {
        assertThrows(IllegalArgumentException.class, () -> Evaluation.reconstruct(
                UUID.randomUUID(), "texto", new Score(8), Urgency.LOW, MONDAY, MONDAY));
        Evaluation rebuilt = Evaluation.reconstruct(
                UUID.randomUUID(), "texto", new Score(8), Urgency.CRITICAL, MONDAY, MONDAY);
        assertEquals(Urgency.CRITICAL, rebuilt.urgency());
        assertTrue(rebuilt.urgencyNoticeSentAt().isPresent());
    }

    @Test
    void calculatesBrazilianSubmissionDayAndExpiration() {
        Instant sundayAtBrasilia = Instant.parse("2026-11-16T02:30:00Z");
        Evaluation evaluation = Evaluation.receive(UUID.randomUUID(), "texto", new Score(5), sundayAtBrasilia);
        assertEquals(LocalDate.of(2026, 11, 15), evaluation.submissionDay());
        assertEquals(sundayAtBrasilia.getEpochSecond() + 84L * 24 * 60 * 60, evaluation.expiresAt());
        assertEquals(Instant.parse("2026-11-16T02:30:00Z"),
                LocalDate.of(2026, 11, 15).atTime(23, 30).atZone(BRASILIA).toInstant());
    }
}
