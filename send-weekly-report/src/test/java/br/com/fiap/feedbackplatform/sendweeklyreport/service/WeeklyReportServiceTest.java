package br.com.fiap.feedbackplatform.sendweeklyreport.service;

import br.com.fiap.feedbackplatform.domain.Evaluation;
import br.com.fiap.feedbackplatform.domain.ReportWeek;
import br.com.fiap.feedbackplatform.domain.Score;
import br.com.fiap.feedbackplatform.domain.Urgency;
import br.com.fiap.feedbackplatform.sendweeklyreport.config.WeeklyReportConfig;
import br.com.fiap.feedbackplatform.sendweeklyreport.repository.EvaluationReader;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.SendEmailRequest;
import software.amazon.awssdk.services.ses.model.SesException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("WeeklyReportService — Consolidação, envio e privacidade do Weekly Report (ADRs 0001, 0008, 0009, 0014, 0017, 0020, 0023)")
class WeeklyReportServiceTest {

    @Mock
    private EvaluationReader evaluationReader;

    @Mock
    private SesClient sesClient;

    private WeeklyReportConfig config;
    private Clock fixedClock;
    private WeeklyReportService service;

    private ListAppender<ILoggingEvent> logAppender;
    private Logger serviceLogger;

    @BeforeEach
    void setUp() {
        // Segunda-feira 16/11/2026 às 08:00 Brasília (11:00 UTC)
        Instant now = Instant.parse("2026-11-16T11:00:00Z");
        fixedClock = Clock.fixed(now, ZoneOffset.UTC);

        config = new WeeklyReportConfig(
                "admin1@fiap.com.br,admin2@fiap.com.br",
                "evaluations-table",
                "sa-east-1",
                null,
                null
        );

        service = new WeeklyReportService(evaluationReader, config, sesClient, fixedClock);

        serviceLogger = (Logger) LoggerFactory.getLogger(WeeklyReportService.class);
        logAppender = new ListAppender<>();
        logAppender.start();
        serviceLogger.addAppender(logAppender);
    }

    @AfterEach
    void tearDown() {
        if (serviceLogger != null && logAppender != null) {
            serviceLogger.detachAppender(logAppender);
        }
    }

    @Test
    @DisplayName("Envia relatório com avaliações para todos os administradores com o remetente correto (ADR 0008, 0014, 0020, 0023)")
    void shouldSendWeeklyReportWithEvaluations() {
        ReportWeek week = ReportWeek.parse("2026-W46");
        Evaluation eval1 = Evaluation.reconstruct(
                UUID.randomUUID(),
                "Professor explicou muito bem a matéria.",
                new Score(10),
                Urgency.LOW,
                Instant.parse("2026-11-10T14:00:00Z"),
                null
        );
        Evaluation eval2 = Evaluation.reconstruct(
                UUID.randomUUID(),
                "Problema sério com o projetor na sala.",
                new Score(2),
                Urgency.CRITICAL,
                Instant.parse("2026-11-11T19:30:00Z"),
                Instant.parse("2026-11-11T19:31:00Z")
        );

        when(evaluationReader.findByReportWeek(week)).thenReturn(List.of(eval1, eval2));

        String result = service.processWeek(week);

        assertThat(result).contains("ReportWeek 2026-W46: 2 avaliações, enviado para 2 destinatários.");

        ArgumentCaptor<SendEmailRequest> captor = ArgumentCaptor.forClass(SendEmailRequest.class);
        verify(sesClient).sendEmail(captor.capture());

        SendEmailRequest sent = captor.getValue();
        assertThat(sent.source()).isEqualTo("Feedback Platform <admin1@fiap.com.br>");
        assertThat(sent.destination().toAddresses()).containsExactly("admin1@fiap.com.br", "admin2@fiap.com.br");
        assertThat(sent.message().subject().data()).isEqualTo("Relatório semanal 09/11–15/11");
        assertThat(sent.message().body().html().data()).contains("Total de avaliações:</strong> 2");
        assertThat(sent.message().body().text().data()).contains("Total de avaliações: 2");
    }

    @Test
    @DisplayName("Semana sem nenhuma avaliação envia relatório com assunto e média específicos (ADR 0008)")
    void shouldSendWeeklyReportWhenWeekHasNoEvaluations() {
        ReportWeek week = ReportWeek.parse("2026-W46");
        when(evaluationReader.findByReportWeek(week)).thenReturn(List.of());

        String result = service.processWeek(week);

        assertThat(result).contains("ReportWeek 2026-W46: 0 avaliações, enviado para 2 destinatários.");

        ArgumentCaptor<SendEmailRequest> captor = ArgumentCaptor.forClass(SendEmailRequest.class);
        verify(sesClient).sendEmail(captor.capture());

        SendEmailRequest sent = captor.getValue();
        assertThat(sent.message().subject().data())
                .isEqualTo("Relatório semanal 09/11–15/11: nenhuma avaliação");
        assertThat(sent.message().body().html().data())
                .contains("Média das avaliações:</strong> sem avaliações");
        assertThat(sent.message().body().text().data())
                .contains("Média das avaliações: sem avaliações");
    }

    @Test
    @DisplayName("Executar duas vezes para a mesma semana produz exatamente o mesmo relatório (ADR 0008, README §Testes item 3)")
    void shouldBeDeterministicWhenExecutedTwiceForSameWeek() {
        ReportWeek week = ReportWeek.parse("2026-W46");
        Evaluation eval = Evaluation.reconstruct(
                UUID.randomUUID(),
                "Aula excelente",
                new Score(9),
                Urgency.LOW,
                Instant.parse("2026-11-12T10:00:00Z"),
                null
        );
        when(evaluationReader.findByReportWeek(week)).thenReturn(List.of(eval));

        service.processWeek(week);
        service.processWeek(week);

        ArgumentCaptor<SendEmailRequest> captor = ArgumentCaptor.forClass(SendEmailRequest.class);
        verify(sesClient, times(2)).sendEmail(captor.capture());

        List<SendEmailRequest> requests = captor.getAllValues();
        SendEmailRequest first = requests.get(0);
        SendEmailRequest second = requests.get(1);

        assertThat(first.source()).isEqualTo(second.source());
        assertThat(first.destination().toAddresses()).isEqualTo(second.destination().toAddresses());
        assertThat(first.message().subject().data()).isEqualTo(second.message().subject().data());
        assertThat(first.message().body().html().data()).isEqualTo(second.message().body().html().data());
        assertThat(first.message().body().text().data()).isEqualTo(second.message().body().text().data());
    }

    @Test
    @DisplayName("Erro na leitura do DynamoDB não é engolido (ADR 0017)")
    void shouldNotSwallowDynamoDbException() {
        ReportWeek week = ReportWeek.parse("2026-W46");
        when(evaluationReader.findByReportWeek(week)).thenThrow(new RuntimeException("DynamoDB indisponível"));

        assertThatThrownBy(() -> service.processWeek(week))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("DynamoDB indisponível");
    }

    @Test
    @DisplayName("Erro no envio do SES não é engolido (ADR 0017)")
    void shouldNotSwallowSesException() {
        ReportWeek week = ReportWeek.parse("2026-W46");
        when(evaluationReader.findByReportWeek(week)).thenReturn(List.of());
        when(sesClient.sendEmail(any(SendEmailRequest.class)))
                .thenThrow(SesException.builder().message("SES throttling").build());

        assertThatThrownBy(() -> service.processWeek(week))
                .isInstanceOf(SesException.class)
                .hasMessageContaining("SES throttling");
    }

    @Test
    @DisplayName("Logs nunca contêm Description nem e-mails de Administrators, registrando enviado para N destinatários (ADR 0009)")
    void shouldRespectPrivacyInLogs() {
        ReportWeek week = ReportWeek.parse("2026-W46");
        String secretDescription = "Informação ultra secreta e sensível do estudante";
        Evaluation eval = Evaluation.reconstruct(
                UUID.randomUUID(),
                secretDescription,
                new Score(5),
                Urgency.MEDIUM,
                Instant.parse("2026-11-13T15:00:00Z"),
                null
        );

        when(evaluationReader.findByReportWeek(week)).thenReturn(List.of(eval));

        service.processWeek(week);

        List<ILoggingEvent> logEvents = logAppender.list;
        assertThat(logEvents).isNotEmpty();

        for (ILoggingEvent event : logEvents) {
            String formattedMessage = event.getFormattedMessage();
            assertThat(formattedMessage)
                    .doesNotContain(secretDescription)
                    .doesNotContain("admin1@fiap.com.br")
                    .doesNotContain("admin2@fiap.com.br");
        }

        boolean foundSuccessLog = logEvents.stream()
                .anyMatch(e -> e.getLevel() == Level.INFO
                        && e.getFormattedMessage().contains("ReportWeek 2026-W46")
                        && e.getFormattedMessage().contains("1 avaliações encontradas")
                        && e.getFormattedMessage().contains("enviado para 2 destinatários"));

        assertThat(foundSuccessLog).isTrue();
    }

    @Test
    @DisplayName("Resolução de ReportWeek aceita parâmetro string explícito, mapas e evento agendado (ADR 0008, 0017)")
    void shouldResolveReportWeekFromVariousInputs() {
        // String ISO explícita
        assertThat(service.resolveWeek("2026-W46"))
                .isEqualTo(new ReportWeek(2026, 46));
        assertThat(service.resolveWeek("\"2026-W46\""))
                .isEqualTo(new ReportWeek(2026, 46));
        assertThat(service.resolveWeek(" \"2026-W46\" "))
                .isEqualTo(new ReportWeek(2026, 46));

        // Mapa com 'reportWeek'
        assertThat(service.resolveWeek(Map.of("reportWeek", "2026-W46")))
                .isEqualTo(new ReportWeek(2026, 46));

        // Mapa com 'week'
        assertThat(service.resolveWeek(Map.of("week", "2026-W45")))
                .isEqualTo(new ReportWeek(2026, 45));

        // Mapa com 'time' (formato EventBridge ScheduledEvent: 16/11/2026 11:00 UTC -> semana fechada anterior é W46)
        assertThat(service.resolveWeek(Map.of("time", "2026-11-16T11:00:00Z")))
                .isEqualTo(new ReportWeek(2026, 46));

        // Envelope completo do EventBridge ScheduledEvent contendo 'time' e demais metadados
        Map<String, Object> scheduledEventEnvelope = Map.of(
                "version", "0",
                "id", "53dc4d37-cffa-4f76-80c9-8b7d4a4d2eaa",
                "detail-type", "Scheduled Event",
                "source", "aws.scheduler",
                "account", "123456789012",
                "time", "2026-11-16T11:00:00Z",
                "region", "sa-east-1",
                "resources", List.of("arn:aws:scheduler:sa-east-1:123456789012:schedule/default/weekly-report"),
                "detail", Map.of()
        );
        assertThat(service.resolveWeek(scheduledEventEnvelope))
                .isEqualTo(new ReportWeek(2026, 46));

        // Mapa vazio ou nulo recorre ao clock (referência 16/11/2026 11:00 UTC -> semana anterior W46)
        assertThat(service.resolveWeek(Map.of()))
                .isEqualTo(new ReportWeek(2026, 46));
        assertThat(service.resolveWeek(null))
                .isEqualTo(new ReportWeek(2026, 46));

        // String ISO-8601 de Instant calcula a semana fechada anterior
        assertThat(service.resolveWeek("2026-11-16T11:00:00Z"))
                .isEqualTo(new ReportWeek(2026, 46));
    }

    @Test
    @DisplayName("Resolução de ReportWeek rejeita entradas inválidas com IllegalArgumentException sem expor payload (ADR 0008, 0017)")
    void shouldRejectInvalidInputsWhenResolvingReportWeek() {
        List<Object> invalidInputs = List.of(
                42,
                "",
                "   ",
                "null",
                "abc",
                Map.of("reportweek", "2026-W46"),
                Map.of("version", "0", "id", "x"),
                Map.of("reportWeek", "2026-W46", "week", "2026-W45"),
                Map.of("reportWeek", "2026-W46", "time", "2026-11-16T11:00:00Z"),
                Map.of("reportWeek", 46),
                Map.of("reportWeek", " ")
        );

        for (Object invalidInput : invalidInputs) {
            assertThatThrownBy(() -> service.resolveWeek(invalidInput))
                    .as("Entrada inválida deveria lançar IllegalArgumentException: %s", invalidInput)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Aceitos:");
        }

        // Chaves desconhecidas ou múltiplas chaves nunca são ecoadas na mensagem da exceção
        assertThatThrownBy(() -> service.resolveWeek(Map.of("segredo@exemplo.com", "x")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Aceitos:")
                .hasMessageNotContaining("segredo@exemplo.com");

        assertThatThrownBy(() -> service.resolveWeek(Map.of("segredo@exemplo.com", "x", "reportWeek", "2026-W46")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Aceitos:")
                .hasMessageNotContaining("segredo@exemplo.com")
                .hasMessageNotContaining("2026-W46");

        // Valores inválidos em String ou em Map/envelope com "time" não vazam o texto na exceção nem na cadeia de causas
        List<Object> secretInputs = List.of(
                "segredo@exemplo.com",
                Map.of("time", "segredo@exemplo.com"),
                Map.of("version", "0", "id", "x", "time", "segredo@exemplo.com")
        );
        for (Object secretInput : secretInputs) {
            Throwable thrown = catchThrowable(() -> service.resolveWeek(secretInput));
            assertThat(thrown)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Aceitos:");
            for (Throwable current = thrown; current != null; current = current.getCause()) {
                assertThat(current.getMessage()).doesNotContain("segredo@exemplo.com");
            }
        }
    }

    @Test
    @DisplayName("Entrada inválida em process(...) propaga IllegalArgumentException e não consulta reader nem envia e-mail (ADR 0017)")
    void shouldPropagateExceptionAndNotCallReaderOrSesWhenInputIsInvalid() {
        Map<String, Object> invalidInput = Map.of("reportweek", "2026-W46");

        assertThatThrownBy(() -> service.process(invalidInput))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageNotContaining("reportweek")
                .hasMessageNotContaining("2026-W46");

        verifyNoInteractions(evaluationReader, sesClient);
    }
}
