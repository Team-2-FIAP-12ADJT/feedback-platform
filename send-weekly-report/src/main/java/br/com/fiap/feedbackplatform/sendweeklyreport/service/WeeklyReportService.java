package br.com.fiap.feedbackplatform.sendweeklyreport.service;

import br.com.fiap.feedbackplatform.domain.Evaluation;
import br.com.fiap.feedbackplatform.domain.ReportWeek;
import br.com.fiap.feedbackplatform.domain.WeeklyReport;
import br.com.fiap.feedbackplatform.emailsupport.EmailMessage;
import br.com.fiap.feedbackplatform.emailsupport.WeeklyReportEmail;
import br.com.fiap.feedbackplatform.sendweeklyreport.config.WeeklyReportConfig;
import br.com.fiap.feedbackplatform.sendweeklyreport.repository.EvaluationReader;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.Body;
import software.amazon.awssdk.services.ses.model.Content;
import software.amazon.awssdk.services.ses.model.Destination;
import software.amazon.awssdk.services.ses.model.Message;
import software.amazon.awssdk.services.ses.model.SendEmailRequest;

/**
 * Serviço responsável por consolidar e enviar o Weekly Report aos Administrators via Amazon SES,
 * conforme ADRs 0001, 0002, 0003, 0004, 0008, 0009, 0010, 0014, 0017, 0020 e 0023.
 */
@Service
public class WeeklyReportService {

    private static final Logger log = LoggerFactory.getLogger(WeeklyReportService.class);

    private final EvaluationReader evaluationReader;
    private final WeeklyReportConfig config;
    private final SesClient sesClient;
    private final Clock clock;

    /**
     * Construtor com injeção de dependências.
     *
     * @param evaluationReader leitor de avaliações do DynamoDB
     * @param config configuração da aplicação e da lista de administradores
     * @param sesClient cliente Amazon SES SDK v2
     * @param clock relógio para resolução temporal
     */
    public WeeklyReportService(
            EvaluationReader evaluationReader,
            WeeklyReportConfig config,
            SesClient sesClient,
            Clock clock) {
        this.evaluationReader = Objects.requireNonNull(evaluationReader, "evaluationReader must not be null");
        this.config = Objects.requireNonNull(config, "config must not be null");
        this.sesClient = Objects.requireNonNull(sesClient, "sesClient must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /**
     * Processa a entrada da função Lambda (evento agendado, mapa ou texto), consolida o WeeklyReport e envia por e-mail.
     *
     * @param input parâmetro de entrada da invocação Lambda
     * @return mensagem de confirmação do envio
     */
    public String process(Object input) {
        ReportWeek week = resolveWeek(input);
        return processWeek(week);
    }

    /**
     * Consolida e envia o WeeklyReport para a ReportWeek informada.
     *
     * @param week ReportWeek a ser processada
     * @return mensagem de confirmação do envio
     */
    public String processWeek(ReportWeek week) {
        Objects.requireNonNull(week, "week must not be null");
        log.info("Iniciando processamento do Weekly Report para a ReportWeek {}.", week.format());

        // Consulta as avaliações dos 7 dias sem alterar os dados gravados (ADR 0001, 0008, 0010)
        List<Evaluation> evaluations = evaluationReader.findByReportWeek(week);

        // Agregação pura do domínio preservando a Urgency original (ADR 0001, 0008)
        WeeklyReport report = new WeeklyReport(week, evaluations);

        // Montagem do e-mail com HTML e texto puro (ADR 0008)
        EmailMessage emailMessage = WeeklyReportEmail.render(report);

        // Envio aos Administrators via Amazon SES (ADR 0014, 0020, 0023)
        sendEmail(emailMessage);

        int recipientCount = config.administrators().size();
        // Log seguro sem conter Description nem e-mails de Administrators (ADR 0009)
        log.info("ReportWeek {}: {} avaliações encontradas, enviado para {} destinatários.",
                week.format(), evaluations.size(), recipientCount);

        return "ReportWeek %s: %d avaliações, enviado para %d destinatários."
                .formatted(week.format(), evaluations.size(), recipientCount);
    }

    /**
     * Resolve a ReportWeek a partir do input da Lambda ou do relógio do sistema, conforme ADRs 0008 e 0017.
     *
     * @param input entrada da invocação Lambda
     * @return ReportWeek calculada
     */
    public ReportWeek resolveWeek(Object input) {
        if (input == null) {
            return ReportWeek.previousClosedWeek(clock.instant());
        }

        if (input instanceof String str) {
            String trimmed = str.trim().replaceAll("^\"|\"$", "");
            if (trimmed.isEmpty() || "null".equalsIgnoreCase(trimmed)) {
                return ReportWeek.previousClosedWeek(clock.instant());
            }
            if (trimmed.contains("T") || trimmed.contains("Z")) {
                return ReportWeek.previousClosedWeek(Instant.parse(trimmed));
            }
            return ReportWeek.parse(trimmed);
        }

        if (input instanceof Map<?, ?> map) {
            Object weekVal = map.get("reportWeek");
            if (weekVal == null) {
                weekVal = map.get("week");
            }
            if (weekVal != null && !weekVal.toString().isBlank()) {
                return ReportWeek.parse(weekVal.toString().trim());
            }

            Object timeVal = map.get("time");
            if (timeVal != null && !timeVal.toString().isBlank()) {
                return ReportWeek.previousClosedWeek(Instant.parse(timeVal.toString().trim()));
            }

            return ReportWeek.previousClosedWeek(clock.instant());
        }

        return ReportWeek.previousClosedWeek(clock.instant());
    }

    private void sendEmail(EmailMessage message) {
        SendEmailRequest request = SendEmailRequest.builder()
                .source(config.formattedSender())
                .destination(Destination.builder()
                        .toAddresses(config.administrators())
                        .build())
                .message(Message.builder()
                        .subject(Content.builder()
                                .data(message.subject())
                                .charset(StandardCharsets.UTF_8.name())
                                .build())
                        .body(Body.builder()
                                .html(Content.builder()
                                        .data(message.htmlBody())
                                        .charset(StandardCharsets.UTF_8.name())
                                        .build())
                                .text(Content.builder()
                                        .data(message.textBody())
                                        .charset(StandardCharsets.UTF_8.name())
                                        .build())
                                .build())
                        .build())
                .build();

        // Falhas não são engolidas: a exceção sobe para acionar retries e DLQ (ADR 0017)
        sesClient.sendEmail(request);
    }
}
