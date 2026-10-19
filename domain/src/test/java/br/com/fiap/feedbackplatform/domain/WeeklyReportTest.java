package br.com.fiap.feedbackplatform.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/**
 * Testes do WeeklyReport conforme ADR 0008 e ADR 0010.
 */
class WeeklyReportTest {
    private static final Instant MONDAY = Instant.parse("2026-11-09T12:00:00Z");

    @Test
    void emptyReportHasNoAverageAndNoNaN() {
        WeeklyReport report = new WeeklyReport(ReportWeek.parse("2026-W46"), List.of());
        assertEquals(0, report.total());
        assertTrue(report.averageScore().isEmpty());
        assertEquals(0, report.notListedCount());
        assertFalse(report.averageScore().toString().contains("NaN"));
    }

    @Test
    void keepsSevenDaysAndThreeUrgenciesWithZeroCounts() {
        ReportWeek week = ReportWeek.parse("2026-W46");
        WeeklyReport report = new WeeklyReport(week, List.of());
        assertIterableEquals(week.days(), report.countsByDay().keySet());
        assertEquals(Map.of(Urgency.CRITICAL, 0L, Urgency.MEDIUM, 0L, Urgency.LOW, 0L),
                report.countsByUrgency());
        assertIterableEquals(List.of(Urgency.CRITICAL, Urgency.MEDIUM, Urgency.LOW),
                report.countsByUrgency().keySet());
        assertTrue(report.countsByDay().values().stream().allMatch(value -> value == 0L));
    }

    @Test
    void countsSundayLateBrasiliaOnSunday() {
        ReportWeek week = ReportWeek.parse("2026-W46");
        Evaluation sunday = Evaluation.receive(UUID.randomUUID(), "domingo", new Score(5),
                Instant.parse("2026-11-16T02:30:00Z"));
        WeeklyReport report = new WeeklyReport(week, List.of(sunday));
        assertEquals(1L, report.countsByDay().get(LocalDate.of(2026, 11, 15)));
        assertEquals(0L, report.countsByDay().get(LocalDate.of(2026, 11, 14)));
        assertIterableEquals(week.days(), report.countsByDay().keySet());
    }

    @Test
    void groupsByPersistedUrgency() {
        Evaluation persisted = Evaluation.reconstruct(UUID.randomUUID(), "gravada",
                new Score(10), Urgency.CRITICAL, MONDAY, null);
        WeeklyReport report = new WeeklyReport(ReportWeek.parse("2026-W46"), List.of(persisted));
        assertEquals(1L, report.countsByUrgency().get(Urgency.CRITICAL));
        assertEquals(0L, report.countsByUrgency().get(Urgency.LOW));
    }

    @Test
    void limitsSortedListButCalculatesAverageAndCountsOverAll505() {
        ReportWeek week = ReportWeek.parse("2026-W46");
        List<Evaluation> evaluations = IntStream.range(0, 505)
                .mapToObj(index -> Evaluation.reconstruct(
                        UUID.nameUUIDFromBytes(("id-" + index).getBytes()),
                        "evaluation", new Score(index % 11),
                        index % 3 == 0 ? Urgency.CRITICAL : index % 3 == 1 ? Urgency.MEDIUM : Urgency.LOW,
                        MONDAY.plusSeconds(index), null))
                .toList();
        WeeklyReport report = new WeeklyReport(week, evaluations);
        assertEquals(505, report.total());
        assertEquals(500, report.evaluations().size());
        assertEquals(5, report.notListedCount());
        assertEquals(new BigDecimal("4.99"), report.averageScore().orElseThrow());
        assertEquals(Map.of(Urgency.CRITICAL, 169L, Urgency.MEDIUM, 168L, Urgency.LOW, 168L),
                report.countsByUrgency());
        assertIterableEquals(List.of(Urgency.CRITICAL, Urgency.MEDIUM, Urgency.LOW),
                report.countsByUrgency().keySet());
        assertTrue(evaluations.stream().filter(evaluation -> !report.evaluations().contains(evaluation))
                .allMatch(evaluation -> evaluation.urgency() == Urgency.LOW));
        Instant latestListedLow = report.evaluations().stream()
                .filter(evaluation -> evaluation.urgency() == Urgency.LOW)
                .map(Evaluation::submissionTime)
                .max(Instant::compareTo)
                .orElseThrow();
        Instant earliestUnlisted = evaluations.stream()
                .filter(evaluation -> !report.evaluations().contains(evaluation))
                .map(Evaluation::submissionTime)
                .min(Instant::compareTo)
                .orElseThrow();
        assertTrue(latestListedLow.isBefore(earliestUnlisted));
        assertTrue(IntStream.range(1, report.evaluations().size())
                .allMatch(index -> compare(report.evaluations().get(index - 1), report.evaluations().get(index)) <= 0));
    }

    @Test
    void rejectsEvaluationOutsideWeek() {
        Evaluation nextMonday = Evaluation.receive(UUID.randomUUID(), "segunda", new Score(5),
                Instant.parse("2026-11-16T03:00:00Z"));
        assertThrows(IllegalArgumentException.class,
                () -> new WeeklyReport(ReportWeek.parse("2026-W46"), List.of(nextMonday)));
    }

    private static int compare(Evaluation first, Evaluation second) {
        int urgency = first.urgency().compareTo(second.urgency());
        if (urgency != 0) {
            return urgency;
        }
        int time = first.submissionTime().compareTo(second.submissionTime());
        return time != 0 ? time : first.id().compareTo(second.id());
    }
}
