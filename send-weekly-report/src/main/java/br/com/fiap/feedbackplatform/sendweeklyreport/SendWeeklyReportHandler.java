package br.com.fiap.feedbackplatform.sendweeklyreport;

import br.com.fiap.feedbackplatform.sendweeklyreport.service.WeeklyReportService;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import java.util.Objects;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Handler AWS Lambda da função send-weekly-report, conforme ADRs 0003, 0004, 0008, 0012 e 0017.
 */
public class SendWeeklyReportHandler implements RequestHandler<Object, String> {

    private static volatile ConfigurableApplicationContext applicationContext;
    private final WeeklyReportService service;

    /**
     * Construtor padrão utilizado pelo runtime AWS Lambda com suporte a SnapStart.
     */
    public SendWeeklyReportHandler() {
        this(getOrCreateService());
    }

    /**
     * Construtor para injeção direta de dependências em testes.
     *
     * @param service serviço de relatório semanal
     */
    public SendWeeklyReportHandler(WeeklyReportService service) {
        this.service = Objects.requireNonNull(service, "service must not be null");
    }

    private static synchronized WeeklyReportService getOrCreateService() {
        if (applicationContext == null || !applicationContext.isActive()) {
            applicationContext = SpringApplication.run(SendWeeklyReportApplication.class);
        }
        return applicationContext.getBean(WeeklyReportService.class);
    }

    @Override
    public String handleRequest(Object input, Context context) {
        return service.process(input);
    }
}
