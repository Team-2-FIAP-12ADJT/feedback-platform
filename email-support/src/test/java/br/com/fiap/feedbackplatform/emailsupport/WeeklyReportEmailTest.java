package br.com.fiap.feedbackplatform.emailsupport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.fiap.feedbackplatform.domain.Evaluation;
import br.com.fiap.feedbackplatform.domain.ReportWeek;
import br.com.fiap.feedbackplatform.domain.Score;
import br.com.fiap.feedbackplatform.domain.WeeklyReport;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/**
 * Testes do Weekly Report conforme ADR 0008.
 */
class WeeklyReportEmailTest {
    @Test
    void rendersSummaryCountsDaysItemsAndEscapedDescription() {
        ReportWeek week = ReportWeek.parse("2026-W46");
        Evaluation critical = Evaluation.receive(UUID.randomUUID(), "<script>alert(\"x\")</script>&<b>'",
                new Score(2), Instant.parse("2026-11-09T12:30:00Z"));
        Evaluation secondCritical = Evaluation.receive(UUID.randomUUID(), "segunda", new Score(3),
                Instant.parse("2026-11-09T15:30:00Z"));
        Evaluation medium = Evaluation.receive(UUID.randomUUID(), "quarta", new Score(5),
                Instant.parse("2026-11-11T15:30:00Z"));
        WeeklyReport report = new WeeklyReport(week, List.of(critical, secondCritical, medium));

        EmailMessage message = WeeklyReportEmail.render(report);

        assertEquals("Relatório semanal 09/11–15/11", message.subject());
        assertTrue(message.htmlBody().contains("Semana de 09/11 a 15/11/2026"));
        assertTrue(message.htmlBody().contains("Total de avaliações:</strong> 3"));
        assertTrue(message.htmlBody().contains("Média das avaliações:</strong> 3,33"));
        assertTrue(message.htmlBody().contains("Crítica: 2"));
        assertTrue(message.htmlBody().contains("Média: 1"));
        assertTrue(message.htmlBody().contains("Baixa: 0"));
        assertTrue(message.htmlBody().contains("&lt;script&gt;alert(&quot;x&quot;)&lt;/script&gt;&amp;&lt;b&gt;&#39;"));
        assertTrue(message.htmlBody().contains("segunda-feira: 2"));
        assertTrue(message.htmlBody().contains("terça-feira: 0"));
        assertTrue(message.htmlBody().contains("quarta-feira: 1"));
        assertTrue(message.htmlBody().contains("quinta-feira: 0"));
        assertTrue(message.htmlBody().contains("sexta-feira: 0"));
        assertTrue(message.htmlBody().contains("sábado: 0"));
        assertTrue(message.htmlBody().contains("domingo: 0"));
        assertTrue(message.htmlBody().contains("Avaliações</h2>"));
        assertTrue(message.htmlBody().contains(
                "Urgência: Crítica | Data de envio: 09/11/2026 09:30</li>"));
        assertTrue(message.htmlBody().contains(
                "Descrição: quarta | Urgência: Média | Data de envio: 11/11/2026 12:30</li>"));
        assertTrue(message.textBody().contains("Semana de 09/11 a 15/11/2026"));
        assertTrue(message.textBody().contains("Total de avaliações: 3"));
        assertTrue(message.textBody().contains("Média das avaliações: 3,33"));
        assertTrue(message.textBody().contains("Crítica: 2"));
        assertTrue(message.textBody().contains("Média: 1"));
        assertTrue(message.textBody().contains("Baixa: 0"));
        assertTrue(message.textBody().contains("segunda-feira: 2"));
        assertTrue(message.textBody().contains("terça-feira: 0"));
        assertTrue(message.textBody().contains("quarta-feira: 1"));
        assertTrue(message.textBody().contains("quinta-feira: 0"));
        assertTrue(message.textBody().contains("sexta-feira: 0"));
        assertTrue(message.textBody().contains("sábado: 0"));
        assertTrue(message.textBody().contains("domingo: 0"));
        assertTrue(message.textBody().contains("Descrição: <script>alert(\"x\")</script>&<b>'"));
        assertTrue(message.textBody().contains("Urgência: Crítica"));
        assertTrue(message.textBody().contains("Data de envio: 09/11/2026 09:30"));
        assertTrue(message.textBody().contains(
                "Descrição: quarta | Urgência: Média | Data de envio: 11/11/2026 12:30"));
    }

    @Test
    void rendersEmptyReportWithSpecificSubjectAndNoNaN() {
        EmailMessage message = WeeklyReportEmail.render(
                new WeeklyReport(ReportWeek.parse("2026-W46"), List.of()));

        assertEquals("Relatório semanal 09/11–15/11: nenhuma avaliação", message.subject());
        assertTrue(message.htmlBody().contains("Média das avaliações:</strong> sem avaliações"));
        assertTrue(message.textBody().contains("Média das avaliações: sem avaliações"));
        assertTrue(message.htmlBody().contains("Crítica: 0"));
        assertTrue(message.htmlBody().contains("Média: 0"));
        assertTrue(message.htmlBody().contains("Baixa: 0"));
        assertTrue(message.textBody().contains("Crítica: 0"));
        assertTrue(message.textBody().contains("Média: 0"));
        assertTrue(message.textBody().contains("Baixa: 0"));
        for (String day : List.of("segunda-feira", "terça-feira", "quarta-feira",
                "quinta-feira", "sexta-feira", "sábado", "domingo")) {
            assertTrue(message.htmlBody().contains(day + ": 0"));
            assertTrue(message.textBody().contains(day + ": 0"));
        }
        assertTrue(message.htmlBody().contains("Total de avaliações:</strong> 0"));
        assertTrue(message.textBody().contains("Total de avaliações: 0"));
        assertTrue(message.htmlBody().contains("<h2>Avaliações</h2><ul></ul>"));
        assertFalse(message.htmlBody().contains("Descrição:"));
        assertFalse(message.textBody().contains("Descrição:"));
        assertFalse(message.htmlBody().contains("e mais"));
        assertFalse(message.textBody().contains("e mais"));
        assertFalse(message.htmlBody().contains("NaN"));
        assertFalse(message.htmlBody().contains("0,00"));
        assertFalse(message.textBody().contains("0,00"));
        assertFalse(message.textBody().contains("NaN"));
    }

    @Test
    void rendersNotListedCountWhenMoreThanFiveHundredEvaluations() {
        ReportWeek week = ReportWeek.parse("2026-W46");
        List<Evaluation> evaluations = IntStream.range(0, 501)
                .mapToObj(index -> Evaluation.receive(UUID.randomUUID(), "item " + index,
                        new Score(index % 11), Instant.parse("2026-11-09T12:00:00Z").plusSeconds(index)))
                .toList();

        EmailMessage message = WeeklyReportEmail.render(new WeeklyReport(week, evaluations));

        assertTrue(message.htmlBody().contains("e mais 1 avaliações não listadas"));
        assertTrue(message.textBody().contains("e mais 1 avaliações não listadas"));
    }
}
