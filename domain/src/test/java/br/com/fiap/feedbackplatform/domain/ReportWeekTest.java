package br.com.fiap.feedbackplatform.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Testes da ReportWeek conforme ADR 0008.
 */
class ReportWeekTest {
    @Test
    void parsesFormatsAndListsIsoWeekDays() {
        ReportWeek week = ReportWeek.parse("2026-W46");
        assertEquals("2026-W46", week.format());
        assertIterableEquals(List.of(
                LocalDate.of(2026, 11, 9), LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 11),
                LocalDate.of(2026, 11, 12), LocalDate.of(2026, 11, 13), LocalDate.of(2026, 11, 14),
                LocalDate.of(2026, 11, 15)), week.days());
        assertEquals("2026-W45", ReportWeek.previousClosedWeek(
                Instant.parse("2026-11-16T00:30:00Z")).format());
        assertEquals("2026-W46", ReportWeek.previousClosedWeek(
                Instant.parse("2026-11-16T03:00:00Z")).format());
        assertEquals("2020-W53", ReportWeek.parse("2020-W53").format());
        assertEquals("2021-W01", ReportWeek.parse("2021-W01").format());
        assertThrows(IllegalArgumentException.class, () -> ReportWeek.parse("2025-W53"));
    }
}
