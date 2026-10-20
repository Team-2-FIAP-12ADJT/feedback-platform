package br.com.fiap.feedbackplatform.sendweeklyreport;

import br.com.fiap.feedbackplatform.domain.ReportWeek;
import br.com.fiap.feedbackplatform.sendweeklyreport.service.WeeklyReportService;
import com.amazonaws.services.lambda.runtime.Context;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("SendWeeklyReportHandler — Handler AWS Lambda (ADRs 0004, 0008, 0012)")
class SendWeeklyReportHandlerTest {

    @Mock
    private WeeklyReportService service;

    @Mock
    private Context context;

    @Test
    @DisplayName("Delega a execução para WeeklyReportService.process")
    void shouldDelegateToWeeklyReportService() {
        SendWeeklyReportHandler handler = new SendWeeklyReportHandler(service);
        Map<String, Object> input = Map.of("reportWeek", "2026-W46");

        when(service.process(input)).thenReturn("ReportWeek 2026-W46: 1 avaliações, enviado para 2 destinatários.");

        String response = handler.handleRequest(input, context);

        assertThat(response).isEqualTo("ReportWeek 2026-W46: 1 avaliações, enviado para 2 destinatários.");
        verify(service).process(input);
    }
}
