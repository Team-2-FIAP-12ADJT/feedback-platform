package br.com.fiap.feedbackplatform.emailsupport;

import br.com.fiap.feedbackplatform.domain.Evaluation;
import br.com.fiap.feedbackplatform.domain.ReportWeek;
import br.com.fiap.feedbackplatform.domain.Urgency;
import br.com.fiap.feedbackplatform.domain.WeeklyReport;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Objects;

/**
 * Monta o Weekly Report em HTML e texto puro, conforme ADRs 0002, 0004 e 0008.
 */
public final class WeeklyReportEmail {
    private static final ZoneId BRASILIA = ZoneId.of("America/Sao_Paulo");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter SHORT_DATE = DateTimeFormatter.ofPattern("dd/MM");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final String[] DAY_NAMES = {
        "segunda-feira", "terça-feira", "quarta-feira", "quinta-feira",
        "sexta-feira", "sábado", "domingo"
    };

    private WeeklyReportEmail() {
    }

    /**
     * Renderiza o Weekly Report completo.
     *
     * @param report relatório semanal agregado
     * @return mensagem pronta para envio
     */
    public static EmailMessage render(WeeklyReport report) {
        Objects.requireNonNull(report, "report must not be null");
        ReportWeek week = report.week();
        LocalDate firstDay = week.days().get(0);
        LocalDate lastDay = week.days().get(6);
        String interval = "%s a %s".formatted(SHORT_DATE.format(firstDay), DATE.format(lastDay));
        String shortInterval = "%s–%s".formatted(SHORT_DATE.format(firstDay), SHORT_DATE.format(lastDay));
        String subject = report.total() == 0
                ? "Relatório semanal %s: nenhuma avaliação".formatted(shortInterval)
                : "Relatório semanal %s".formatted(shortInterval);
        String average = report.averageScore()
                .map(value -> value.toPlainString().replace('.', ','))
                .orElse("sem avaliações");

        StringBuilder html = new StringBuilder("<html lang=\"pt-BR\"><body>");
        html.append("<h1>Semana de ").append(interval).append("</h1>");
        html.append("<p><strong>Total de avaliações:</strong> ").append(report.total()).append("</p>");
        html.append("<p><strong>Média das avaliações:</strong> ").append(escape(average)).append("</p>");
        html.append("<h2>Avaliações por urgência</h2><ul>");
        for (Urgency urgency : Urgency.values()) {
            html.append("<li>").append(label(urgency)).append(": ")
                    .append(report.countsByUrgency().get(urgency)).append("</li>");
        }
        html.append("</ul><h2>Avaliações por dia</h2><ul>");
        int dayIndex = 0;
        for (Map.Entry<LocalDate, Long> entry : report.countsByDay().entrySet()) {
            html.append("<li>").append(DAY_NAMES[dayIndex++]).append(": ")
                    .append(entry.getValue()).append("</li>");
        }
        html.append("</ul><h2>Avaliações</h2><ul>");
        for (Evaluation evaluation : report.evaluations()) {
            html.append("<li>Descrição: ").append(escape(evaluation.description()))
                    .append(" | Urgência: ").append(label(evaluation.urgency()))
                    .append(" | Data de envio: ")
                    .append(DATE_TIME.format(evaluation.submissionTime().atZone(BRASILIA)))
                    .append("</li>");
        }
        if (report.notListedCount() > 0) {
            html.append("<li>e mais ").append(report.notListedCount()).append(" avaliações não listadas</li>");
        }
        html.append("</ul></body></html>");

        StringBuilder text = new StringBuilder("Semana de ").append(interval).append('\n')
                .append("Total de avaliações: ").append(report.total()).append('\n')
                .append("Média das avaliações: ").append(average).append('\n')
                .append("Avaliações por urgência:\n");
        for (Urgency urgency : Urgency.values()) {
            text.append(label(urgency)).append(": ").append(report.countsByUrgency().get(urgency)).append('\n');
        }
        text.append("Avaliações por dia:\n");
        dayIndex = 0;
        for (Long count : report.countsByDay().values()) {
            text.append(DAY_NAMES[dayIndex++]).append(": ").append(count).append('\n');
        }
        text.append("Avaliações:\n");
        for (Evaluation evaluation : report.evaluations()) {
            text.append("Descrição: ").append(evaluation.description())
                    .append(" | Urgência: ").append(label(evaluation.urgency()))
                    .append(" | Data de envio: ")
                    .append(DATE_TIME.format(evaluation.submissionTime().atZone(BRASILIA))).append('\n');
        }
        if (report.notListedCount() > 0) {
            text.append("e mais ").append(report.notListedCount()).append(" avaliações não listadas\n");
        }
        return new EmailMessage(subject, html.toString(), text.toString());
    }

    private static String label(Urgency urgency) {
        return switch (urgency) {
            case CRITICAL -> "Crítica";
            case MEDIUM -> "Média";
            case LOW -> "Baixa";
        };
    }

    private static String escape(String value) {
        return HtmlEscaper.escape(value);
    }
}
