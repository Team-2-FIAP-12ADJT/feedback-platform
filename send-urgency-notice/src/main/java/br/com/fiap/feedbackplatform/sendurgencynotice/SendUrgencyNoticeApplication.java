package br.com.fiap.feedbackplatform.sendurgencynotice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Aplicação Spring Boot sem camada web para envio de Urgency Notice a partir do DynamoDB Streams,
 * conforme ADRs 0003, 0004, 0010, 0014 e 0022.
 */
@SpringBootApplication
public class SendUrgencyNoticeApplication {

    /**
     * Inicia o contexto da aplicação Spring Boot sem servidor web, conforme ADR 0004.
     *
     * @param args argumentos de inicialização
     */
    public static void main(String[] args) {
        SpringApplication.run(SendUrgencyNoticeApplication.class, args);
    }
}
