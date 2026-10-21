package br.com.fiap.feedbackplatform.sendurgencynotice;

import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.ses.SesClient;

/**
 * Produz os beans de infraestrutura e configuração da função `send-urgency-notice`, conforme
 * ADRs 0004, 0010, 0014, 0019, 0020, 0022 e 0023.
 */
@Configuration
public class InfrastructureConfig {

    /**
     * Produz o relógio UTC injetável para registrar a marca {@code urgencyNoticeSentAt}, conforme ADR 0005 e ADR 0022.
     *
     * @return relógio em UTC
     */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    /**
     * Produz e valida as propriedades obrigatórias da função na inicialização do contexto, conforme ADRs 0020 e 0023.
     *
     * @param table nome da tabela DynamoDB vindo de {@code EVALUATIONS_TABLE}
     * @param administrators lista de e-mails vinda de {@code ADMINISTRATORS}
     * @return propriedades validadas
     */
    @Bean
    public UrgencyNoticeProperties urgencyNoticeProperties(
            @Value("${evaluations.table:${EVALUATIONS_TABLE:}}") String table,
            @Value("${administrators:${ADMINISTRATORS:}}") String administrators) {
        return UrgencyNoticeProperties.of(table, administrators);
    }

    /**
     * Produz o cliente DynamoDB SDK v2 com transporte {@code UrlConnectionHttpClient}, conforme ADRs 0004, 0010 e 0022.
     *
     * @return cliente DynamoDB configurado
     */
    @Bean
    public DynamoDbClient dynamoDbClient() {
        return DynamoDbClient.builder()
                .httpClientBuilder(UrlConnectionHttpClient.builder())
                .build();
    }

    /**
     * Produz o cliente Amazon SES v1 SDK v2 com transporte {@code UrlConnectionHttpClient}, conforme ADRs 0004 e 0014.
     *
     * @return cliente SES configurado
     */
    @Bean
    public SesClient sesClient() {
        return SesClient.builder()
                .httpClientBuilder(UrlConnectionHttpClient.builder())
                .build();
    }
}
