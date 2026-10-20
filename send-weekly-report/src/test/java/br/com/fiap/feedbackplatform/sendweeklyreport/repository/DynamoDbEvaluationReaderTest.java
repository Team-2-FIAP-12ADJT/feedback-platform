package br.com.fiap.feedbackplatform.sendweeklyreport.repository;

import br.com.fiap.feedbackplatform.domain.Evaluation;
import br.com.fiap.feedbackplatform.domain.ReportWeek;
import br.com.fiap.feedbackplatform.domain.Urgency;
import br.com.fiap.feedbackplatform.sendweeklyreport.config.WeeklyReportConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("DynamoDbEvaluationReader — Leitura particionada por dia no DynamoDB (ADRs 0001, 0003, 0010, 0019)")
class DynamoDbEvaluationReaderTest {

    @Mock
    private DynamoDbClient dynamoDb;

    private WeeklyReportConfig config;
    private DynamoDbEvaluationReader reader;

    @BeforeEach
    void setUp() {
        config = new WeeklyReportConfig(
                "admin1@fiap.com.br,admin2@fiap.com.br",
                "evaluations-table",
                "sa-east-1",
                null,
                null
        );
        reader = new DynamoDbEvaluationReader(dynamoDb, config);
    }

    @Test
    @DisplayName("Executa 7 consultas por submissionDay e pagina até o fim de cada consulta (ADR 0010)")
    void shouldExecuteSevenQueriesAndPaginateUntilEnd() {
        ReportWeek week = ReportWeek.parse("2026-W46");
        List<LocalDate> days = week.days();

        // Para o primeiro dia, simula paginação com 2 páginas
        Map<String, AttributeValue> itemPage1 = createItem("Aula boa", 10, "LOW", "2026-11-09T12:00:00Z");
        Map<String, AttributeValue> itemPage2 = createItem("Aula excelente", 10, "LOW", "2026-11-09T15:00:00Z");

        Map<String, AttributeValue> lastKey = Map.of("submissionKey", AttributeValue.fromS("key-page-1"));

        QueryResponse page1Response = QueryResponse.builder()
                .items(List.of(itemPage1))
                .lastEvaluatedKey(lastKey)
                .build();

        QueryResponse page2Response = QueryResponse.builder()
                .items(List.of(itemPage2))
                .lastEvaluatedKey(null)
                .build();

        QueryResponse emptyResponse = QueryResponse.builder()
                .items(List.of())
                .lastEvaluatedKey(null)
                .build();

        // 1ª chamada: page1, 2ª chamada: page2, restantes (6 dias): emptyResponse
        when(dynamoDb.query(any(QueryRequest.class)))
                .thenReturn(page1Response, page2Response,
                        emptyResponse, emptyResponse, emptyResponse, emptyResponse, emptyResponse, emptyResponse);

        List<Evaluation> results = reader.findByReportWeek(week);

        // 7 dias, sendo que o primeiro teve 2 páginas = 8 invocações de query
        verify(dynamoDb, times(8)).query(any(QueryRequest.class));

        assertThat(results).hasSize(2);
        assertThat(results.get(0).description()).isEqualTo("Aula boa");
        assertThat(results.get(1).description()).isEqualTo("Aula excelente");

        ArgumentCaptor<QueryRequest> captor = ArgumentCaptor.forClass(QueryRequest.class);
        verify(dynamoDb, times(8)).query(captor.capture());

        List<QueryRequest> capturedRequests = captor.getAllValues();
        // A segunda consulta deve ter exclusiveStartKey preenchida
        assertThat(capturedRequests.get(1).exclusiveStartKey()).isEqualTo(lastKey);
    }

    @Test
    @DisplayName("Reconstrói Evaluation preservando a Urgency original sem recalcular a partir do Score (ADR 0001, CONTEXT-MAP §Relationships)")
    void shouldPreserveOriginalUrgencyWithoutRecalculation() {
        ReportWeek week = ReportWeek.parse("2026-W46");

        // Nota 10 normalmente seria LOW, mas gravada como CRITICAL por override histórico
        Map<String, AttributeValue> itemWithOverriddenUrgency = createItem("Caso especial", 10, "CRITICAL", "2026-11-09T10:00:00Z");

        when(dynamoDb.query(any(QueryRequest.class)))
                .thenReturn(QueryResponse.builder().items(List.of(itemWithOverriddenUrgency)).build())
                .thenReturn(QueryResponse.builder().items(List.of()).build());

        List<Evaluation> results = reader.findByReportWeek(week);

        assertThat(results).hasSize(1);
        Evaluation evaluation = results.get(0);
        assertThat(evaluation.score().value()).isEqualTo(10);
        assertThat(evaluation.urgency()).isEqualTo(Urgency.CRITICAL);
    }

    private Map<String, AttributeValue> createItem(String description, int score, String urgency, String submissionTime) {
        return Map.of(
                "id", AttributeValue.fromS(UUID.randomUUID().toString()),
                "description", AttributeValue.fromS(description),
                "score", AttributeValue.fromN(Integer.toString(score)),
                "urgency", AttributeValue.fromS(urgency),
                "submissionTime", AttributeValue.fromS(submissionTime)
        );
    }
}
