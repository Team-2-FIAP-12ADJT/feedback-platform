package br.com.fiap.feedbackplatform.emailsupport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.fiap.feedbackplatform.domain.Evaluation;
import br.com.fiap.feedbackplatform.domain.Score;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Testes do Urgency Notice conforme ADRs 0001, 0002, 0004 e 0014.
 */
class UrgencyNoticeEmailTest {
    @Test
    void rendersCriticalEvaluationInBraziliaAndEscapesDescription() {
        Evaluation evaluation = Evaluation.receive(UUID.randomUUID(),
                "<script>alert(\"x\")</script>&<b>'", new Score(3),
                Instant.parse("2026-11-09T02:30:00Z"));

        EmailMessage message = UrgencyNoticeEmail.render(evaluation);

        assertEquals("Aviso de urgência: avaliação crítica", message.subject());
        assertTrue(message.htmlBody().contains("&lt;script&gt;alert(&quot;x&quot;)&lt;/script&gt;&amp;&lt;b&gt;&#39;"));
        assertTrue(message.htmlBody().contains("Urgência:</strong> Crítica"));
        assertTrue(message.htmlBody().contains("08/11/2026 23:30"));
        assertTrue(message.textBody().contains("<script>alert(\"x\")</script>&<b>'"));
        assertTrue(message.textBody().contains("Urgência: Crítica"));
        assertTrue(message.textBody().contains("08/11/2026 23:30"));
    }

    @Test
    void rejectsNonCriticalEvaluation() {
        Evaluation evaluation = Evaluation.receive(UUID.randomUUID(), "texto", new Score(5),
                Instant.parse("2026-11-09T12:00:00Z"));
        assertThrows(IllegalArgumentException.class, () -> UrgencyNoticeEmail.render(evaluation));
    }
}
