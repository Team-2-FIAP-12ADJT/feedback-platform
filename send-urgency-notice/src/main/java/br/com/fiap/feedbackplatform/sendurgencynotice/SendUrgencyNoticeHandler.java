package br.com.fiap.feedbackplatform.sendurgencynotice;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.DynamodbEvent;
import com.amazonaws.services.lambda.runtime.events.StreamsEventResponse;
import java.util.Objects;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Handler AWS Lambda que recebe eventos do DynamoDB Streams e delega o envio de Urgency Notices ao
 * contexto Spring Boot reaproveitado entre invocações e compatível com SnapStart, conforme
 * ADRs 0003, 0004, 0010, 0017 e 0022.
 */
public class SendUrgencyNoticeHandler implements RequestHandler<DynamodbEvent, StreamsEventResponse> {
    private static volatile ConfigurableApplicationContext sharedContext;

    private final UrgencyNoticeService service;

    /**
     * Construtor padrão usado pelo runtime da AWS Lambda, inicializando ou reaproveitando o
     * contexto Spring Boot, conforme ADR 0004.
     */
    public SendUrgencyNoticeHandler() {
        this(obtainContext().getBean(UrgencyNoticeService.class));
    }

    /**
     * Construtor para injeção direta do serviço em testes, conforme ADR 0004.
     *
     * @param service serviço de processamento de Urgency Notice
     */
    public SendUrgencyNoticeHandler(UrgencyNoticeService service) {
        this.service = Objects.requireNonNull(service, "service must not be null");
    }

    /**
     * Processa o lote do DynamoDB Streams e retorna as falhas parciais para reentrega seletiva,
     * conforme ADRs 0005, 0010, 0017 e 0022.
     *
     * @param input evento do DynamoDB Streams
     * @param context contexto de execução da Lambda
     * @return resposta com os identificadores dos itens que falharam
     */
    @Override
    public StreamsEventResponse handleRequest(DynamodbEvent input, Context context) {
        return service.process(input);
    }

    private static ConfigurableApplicationContext obtainContext() {
        ConfigurableApplicationContext context = sharedContext;
        if (context != null && context.isActive()) {
            return context;
        }
        synchronized (SendUrgencyNoticeHandler.class) {
            context = sharedContext;
            if (context == null || !context.isActive()) {
                context = SpringApplication.run(SendUrgencyNoticeApplication.class);
                sharedContext = context;
            }
            return context;
        }
    }
}
