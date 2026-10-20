package br.com.fiap.feedbackplatform.receiveevaluation.config;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import java.net.URI;
import java.time.Clock;
import java.util.Optional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

/** Produz dependências de infraestrutura, conforme ADRs 0004, 0010 e 0019. */
@ApplicationScoped
public class InfrastructureConfig {
    /** Produz o relógio UTC injetável para tornar o Submission Time determinístico nos testes. */
    @Produces
    @ApplicationScoped
    public Clock clock() { return Clock.systemUTC(); }

    /** Produz o cliente DynamoDB SDK v2 com o transporte URL, sem cliente Netty ou Apache. */
    @Produces
    @ApplicationScoped
    public DynamoDbClient dynamoDbClient(
            @ConfigProperty(name = "evaluations.dynamodb.endpoint-override") Optional<String> endpoint) {
        var builder = DynamoDbClient.builder()
                .httpClientBuilder(UrlConnectionHttpClient.builder());
        endpoint.filter(value -> !value.isBlank()).ifPresent(value -> builder.endpointOverride(URI.create(value)));
        return builder.build();
    }
}
