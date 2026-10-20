package br.com.fiap.feedbackplatform.emailsupport;

import br.com.fiap.feedbackplatform.domain.Evaluation;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/**
 * Monta o Urgency Notice de uma Critical Evaluation, conforme ADRs 0001, 0002, 0004 e 0014.
 */
public final class UrgencyNoticeEmail {
    private static final ZoneId BRASILIA = ZoneId.of("America/Sao_Paulo");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final String SUBJECT = "Aviso de urgência: avaliação crítica";

    private UrgencyNoticeEmail() {
    }

    /**
     * Renderiza assunto, HTML e texto puro da Critical Evaluation.
     *
     * @param evaluation Evaluation crítica
     * @return mensagem pronta para envio
     * @throws IllegalArgumentException se a Evaluation não for crítica
     */
    public static EmailMessage render(Evaluation evaluation) {
        Objects.requireNonNull(evaluation, "evaluation must not be null");
        if (!evaluation.urgency().isCritical()) {
            throw new IllegalArgumentException("urgency notice requires a critical evaluation");
        }
        String description = evaluation.description();
        String date = DATE_TIME.format(evaluation.submissionTime().atZone(BRASILIA));
        String html = """
                <html lang="pt-BR"><body>
                <h1>Aviso de urgência</h1>
                <p><strong>Descrição:</strong> %s</p>
                <p><strong>Urgência:</strong> Crítica</p>
                <p><strong>Data de envio:</strong> %s</p>
                </body></html>
                """.formatted(HtmlEscaper.escape(description), date);
        String text = """
                Aviso de urgência
                Descrição: %s
                Urgência: Crítica
                Data de envio: %s
                """.formatted(description, date);
        return new EmailMessage(SUBJECT, html, text);
    }
}
