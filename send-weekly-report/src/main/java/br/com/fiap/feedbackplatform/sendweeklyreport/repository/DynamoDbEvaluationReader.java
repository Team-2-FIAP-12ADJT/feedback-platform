package br.com.fiap.feedbackplatform.sendweeklyreport.repository;

import br.com.fiap.feedbackplatform.domain.Evaluation;
import br.com.fiap.feedbackplatform.domain.ReportWeek;
import br.com.fiap.feedbackplatform.domain.Score;
import br.com.fiap.feedbackplatform.domain.Urgency;
import br.com.fiap.feedbackplatform.sendweeklyreport.config.WeeklyReportConfig;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;

/**
 * Leitor de Evaluations particionadas por dia no DynamoDB, conforme ADRs 0001, 0003, 0010 e 0019.
 */
@Repository
public class DynamoDbEvaluationReader implements EvaluationReader {

    private final DynamoDbClient dynamoDb;
    private final String tableName;

    /**
     * Construtor com injeção de dependências.
     *
     * @param dynamoDb cliente DynamoDB SDK v2
     * @param config configuração de infraestrutura
     */
    public DynamoDbEvaluationReader(DynamoDbClient dynamoDb, WeeklyReportConfig config) {
        this.dynamoDb = Objects.requireNonNull(dynamoDb, "dynamoDb must not be null");
        this.tableName = Objects.requireNonNull(config.tableName(), "tableName must not be null");
    }

    /**
     * Consulta todas as Evaluations nos sete dias da ReportWeek informada, paginando cada partição diária até o fim,
     * conforme ADRs 0008 e 0010.
     *
     * @param week ReportWeek coberta pela consulta
     * @return lista imutável com todas as Evaluations da semana
     */
    @Override
    public List<Evaluation> findByReportWeek(ReportWeek week) {
        Objects.requireNonNull(week, "week must not be null");
        List<Evaluation> evaluations = new ArrayList<>();

        for (LocalDate day : week.days()) {
            evaluations.addAll(queryDay(day));
        }

        return List.copyOf(evaluations);
    }

    private List<Evaluation> queryDay(LocalDate day) {
        List<Evaluation> dayEvaluations = new ArrayList<>();
        Map<String, AttributeValue> lastKey = null;

        do {
            var requestBuilder = QueryRequest.builder()
                    .tableName(tableName)
                    .keyConditionExpression("submissionDay = :day")
                    .expressionAttributeValues(Map.of(":day", AttributeValue.fromS(day.toString())));

            if (lastKey != null && !lastKey.isEmpty()) {
                requestBuilder.exclusiveStartKey(lastKey);
            }

            QueryResponse response = dynamoDb.query(requestBuilder.build());

            for (Map<String, AttributeValue> item : response.items()) {
                dayEvaluations.add(reconstructEvaluation(item));
            }

            lastKey = response.lastEvaluatedKey();
        } while (lastKey != null && !lastKey.isEmpty());

        return dayEvaluations;
    }

    private Evaluation reconstructEvaluation(Map<String, AttributeValue> item) {
        UUID id = UUID.fromString(item.get("id").s());
        String description = item.get("description").s();
        int scoreValue = Integer.parseInt(item.get("score").n());
        Score score = new Score(scoreValue);
        // Preserva a Urgency gravada na tabela sem recalculá-la a partir do Score (ADR 0001, CONTEXT-MAP §Relationships)
        Urgency urgency = Urgency.valueOf(item.get("urgency").s());
        Instant submissionTime = Instant.parse(item.get("submissionTime").s());

        Instant urgencyNoticeSentAt = null;
        if (item.containsKey("urgencyNoticeSentAt") && item.get("urgencyNoticeSentAt").s() != null) {
            urgencyNoticeSentAt = Instant.parse(item.get("urgencyNoticeSentAt").s());
        }

        return Evaluation.reconstruct(id, description, score, urgency, submissionTime, urgencyNoticeSentAt);
    }
}
