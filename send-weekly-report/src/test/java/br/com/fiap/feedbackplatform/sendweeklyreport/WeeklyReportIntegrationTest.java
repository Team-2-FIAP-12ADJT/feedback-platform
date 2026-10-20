package br.com.fiap.feedbackplatform.sendweeklyreport;

import br.com.fiap.feedbackplatform.domain.Evaluation;
import br.com.fiap.feedbackplatform.domain.ReportWeek;
import br.com.fiap.feedbackplatform.domain.Score;
import br.com.fiap.feedbackplatform.domain.Urgency;
import br.com.fiap.feedbackplatform.sendweeklyreport.config.WeeklyReportConfig;
import br.com.fiap.feedbackplatform.sendweeklyreport.repository.DynamoDbEvaluationReader;
import br.com.fiap.feedbackplatform.sendweeklyreport.service.WeeklyReportService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeDefinition;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.BatchWriteItemRequest;
import software.amazon.awssdk.services.dynamodb.model.BatchWriteItemResponse;
import software.amazon.awssdk.services.dynamodb.model.CreateTableRequest;
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement;
import software.amazon.awssdk.services.dynamodb.model.KeyType;
import software.amazon.awssdk.services.dynamodb.model.ProvisionedThroughput;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.PutRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.WriteRequest;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.SendEmailRequest;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@DisplayName("WeeklyReportIntegrationTest — Teste de integração com DynamoDB emulado (ADRs 0001, 0008, 0010, 0014, 0020, 0023)")
class WeeklyReportIntegrationTest {

    private static final String TABLE_NAME = "evaluations-it";
    private static GenericContainer<?> dynamoContainer;
    private static DynamoDbClient dynamoDbClient;
    private static WeeklyReportConfig config;
    private static boolean dockerAvailable;

    @BeforeAll
    static void setUpAll() {
        try {
            dockerAvailable = DockerClientFactory.instance().isDockerAvailable();
        } catch (Exception e) {
            dockerAvailable = false;
        }

        if (!dockerAvailable) {
            return;
        }

        dynamoContainer = new GenericContainer<>(DockerImageName.parse("amazon/dynamodb-local:3.3.2"))
                .withExposedPorts(8000)
                .withCommand("-jar", "DynamoDBLocal.jar", "-inMemory", "-sharedDb");
        dynamoContainer.start();

        String endpoint = "http://" + dynamoContainer.getHost() + ":" + dynamoContainer.getMappedPort(8000);

        dynamoDbClient = DynamoDbClient.builder()
                .endpointOverride(URI.create(endpoint))
                .region(Region.SA_EAST_1)
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create("test", "test")))
                .httpClientBuilder(UrlConnectionHttpClient.builder())
                .build();

        dynamoDbClient.createTable(CreateTableRequest.builder()
                .tableName(TABLE_NAME)
                .keySchema(
                        KeySchemaElement.builder().attributeName("submissionDay").keyType(KeyType.HASH).build(),
                        KeySchemaElement.builder().attributeName("submissionKey").keyType(KeyType.RANGE).build()
                )
                .attributeDefinitions(
                        AttributeDefinition.builder().attributeName("submissionDay").attributeType("S").build(),
                        AttributeDefinition.builder().attributeName("submissionKey").attributeType("S").build()
                )
                .provisionedThroughput(ProvisionedThroughput.builder().readCapacityUnits(5L).writeCapacityUnits(5L).build())
                .build());

        config = new WeeklyReportConfig(
                "admin1@fiap.com.br,admin2@fiap.com.br",
                TABLE_NAME,
                "sa-east-1",
                endpoint,
                null
        );
    }

    @AfterAll
    static void tearDownAll() {
        if (dynamoDbClient != null) {
            dynamoDbClient.close();
        }
        if (dynamoContainer != null) {
            dynamoContainer.stop();
        }
    }

    @Test
    @DisplayName("Executa 7 consultas por submissionDay, inclui domingo 23:30 de Brasília, exclui semanas vizinhas e envia por SES")
    void shouldQuerySevenDaysIncludeSundayNightExcludeNeighborWeeksAndSendEmail() {
        Assumptions.assumeTrue(dockerAvailable, "Docker daemon não está disponível neste ambiente");

        // 1. Segunda-feira da semana 2026-W46: 09/11/2026 12:00 UTC (09:00 Brasília)
        insertEvaluation(UUID.randomUUID(), "Aula inicial da semana", 8, Urgency.LOW,
                "2026-11-09", "2026-11-09T12:00:00Z");

        // 2. Quarta-feira da semana 2026-W46: 11/11/2026 18:00 UTC (15:00 Brasília)
        insertEvaluation(UUID.randomUUID(), "Laboratório prático", 2, Urgency.CRITICAL,
                "2026-11-11", "2026-11-11T18:00:00Z");

        // 3. Caso crítico da ADR 0008: Domingo 15/11/2026 às 23:30 Brasília (16/11/2026 02:30 UTC)
        // submissionDay deve ser 2026-11-15 (dia em Brasília). Deve ser incluída na semana 2026-W46!
        insertEvaluation(UUID.randomUUID(), "Dúvida enviada domingo tarde da noite", 7, Urgency.LOW,
                "2026-11-15", "2026-11-16T02:30:00Z");

        // 4. Semana anterior (2026-W45): Domingo 08/11/2026
        insertEvaluation(UUID.randomUUID(), "Semana anterior W45", 5, Urgency.MEDIUM,
                "2026-11-08", "2026-11-08T15:00:00Z");

        // 5. Semana posterior (2026-W47): Segunda 16/11/2026 14:00 UTC (11:00 Brasília)
        insertEvaluation(UUID.randomUUID(), "Semana seguinte W47", 10, Urgency.LOW,
                "2026-11-16", "2026-11-16T14:00:00Z");

        DynamoDbEvaluationReader reader = new DynamoDbEvaluationReader(dynamoDbClient, config);
        SesClient sesClient = mock(SesClient.class);
        Clock clock = Clock.fixed(Instant.parse("2026-11-16T11:00:00Z"), ZoneOffset.UTC);
        WeeklyReportService service = new WeeklyReportService(reader, config, sesClient, clock);

        String result = service.processWeek(ReportWeek.parse("2026-W46"));

        assertThat(result).contains("ReportWeek 2026-W46: 3 avaliações, enviado para 2 destinatários.");

        ArgumentCaptor<SendEmailRequest> captor = ArgumentCaptor.forClass(SendEmailRequest.class);
        verify(sesClient).sendEmail(captor.capture());

        SendEmailRequest request = captor.getValue();
        assertThat(request.source()).isEqualTo("Feedback Platform <admin1@fiap.com.br>");
        assertThat(request.destination().toAddresses()).containsExactly("admin1@fiap.com.br", "admin2@fiap.com.br");
        assertThat(request.message().subject().data()).isEqualTo("Relatório semanal 09/11–15/11");

        String html = request.message().body().html().data();
        assertThat(html).contains("Total de avaliações:</strong> 3");
        assertThat(html).contains("Aula inicial da semana");
        assertThat(html).contains("Laboratório prático");
        assertThat(html).contains("Dúvida enviada domingo tarde da noite");
        assertThat(html).doesNotContain("Semana anterior W45");
        assertThat(html).doesNotContain("Semana seguinte W47");
    }

    @Test
    @DisplayName("Semana sem nenhuma avaliação gravada envia e-mail com assunto e média de semana vazia (ADR 0008)")
    void shouldSendEmptyReportWhenNoEvaluationsRecorded() {
        Assumptions.assumeTrue(dockerAvailable, "Docker daemon não está disponível neste ambiente");

        DynamoDbEvaluationReader reader = new DynamoDbEvaluationReader(dynamoDbClient, config);
        SesClient sesClient = mock(SesClient.class);
        Clock clock = Clock.fixed(Instant.parse("2026-10-05T11:00:00Z"), ZoneOffset.UTC);
        WeeklyReportService service = new WeeklyReportService(reader, config, sesClient, clock);

        // Semana 2026-W40 não possui nenhuma avaliação na tabela
        String result = service.processWeek(ReportWeek.parse("2026-W40"));

        assertThat(result).contains("ReportWeek 2026-W40: 0 avaliações, enviado para 2 destinatários.");

        ArgumentCaptor<SendEmailRequest> captor = ArgumentCaptor.forClass(SendEmailRequest.class);
        verify(sesClient).sendEmail(captor.capture());

        SendEmailRequest request = captor.getValue();
        assertThat(request.message().subject().data()).isEqualTo("Relatório semanal 28/09–04/10: nenhuma avaliação");
        assertThat(request.message().body().html().data()).contains("Média das avaliações:</strong> sem avaliações");
        assertThat(request.message().body().text().data()).contains("Média das avaliações: sem avaliações");
    }

    @Test
    @DisplayName("Pagina consulta real acima de 1 MB em um único submissionDay usando LastEvaluatedKey até o fim (ADR 0008, 0010)")
    void shouldPaginateRealQueryOverOneMegabyteAcrossMultiplePages() {
        Assumptions.assumeTrue(dockerAvailable, "Docker daemon não está disponível neste ambiente");

        // Semana 2026-W30 (20/07/2026 a 26/07/2026), isolada dos demais testes
        ReportWeek week = ReportWeek.parse("2026-W30");
        String targetSubmissionDay = "2026-07-22";
        int totalItemsToInsert = 1_200;
        String thousandCharDescription = "A".repeat(1_000);

        List<WriteRequest> pendingWrites = new ArrayList<>(25);
        for (int i = 0; i < totalItemsToInsert; i++) {
            UUID id = UUID.randomUUID();
            String submissionTime = Instant.parse("2026-07-22T12:00:00Z").plusSeconds(i).toString();
            Map<String, AttributeValue> item = new HashMap<>();
            item.put("submissionDay", AttributeValue.fromS(targetSubmissionDay));
            item.put("submissionKey", AttributeValue.fromS(submissionTime + "#" + id));
            item.put("id", AttributeValue.fromS(id.toString()));
            item.put("description", AttributeValue.fromS(thousandCharDescription));
            item.put("score", AttributeValue.fromN("8"));
            item.put("urgency", AttributeValue.fromS(Urgency.LOW.name()));
            item.put("submissionTime", AttributeValue.fromS(submissionTime));
            item.put("expiresAt", AttributeValue.fromN(Long.toString(Instant.parse(submissionTime).getEpochSecond() + 86400L * 84L)));

            pendingWrites.add(WriteRequest.builder()
                    .putRequest(PutRequest.builder().item(item).build())
                    .build());

            if (pendingWrites.size() == 25) {
                flushBatch(pendingWrites);
                pendingWrites.clear();
            }
        }
        if (!pendingWrites.isEmpty()) {
            flushBatch(pendingWrites);
        }

        List<QueryRequest> recordedQueries = new ArrayList<>();
        DynamoDbClient recordingClient = (DynamoDbClient) Proxy.newProxyInstance(
                DynamoDbClient.class.getClassLoader(),
                new Class<?>[]{DynamoDbClient.class},
                (proxy, method, args) -> {
                    if ("query".equals(method.getName()) && args != null && args.length == 1 && args[0] instanceof QueryRequest qr) {
                        recordedQueries.add(qr);
                    }
                    try {
                        return method.invoke(dynamoDbClient, args);
                    } catch (InvocationTargetException e) {
                        throw e.getCause();
                    }
                }
        );

        DynamoDbEvaluationReader reader = new DynamoDbEvaluationReader(recordingClient, config);
        List<Evaluation> evaluations = reader.findByReportWeek(week);

        // Total de Evaluations devolvidas = total gravado
        assertThat(evaluations).hasSize(totalItemsToInsert);

        // Ao menos 8 Query no total (6 dias com 1 página + 1 dia paginado em 2 páginas = 8 Queries)
        assertThat(recordedQueries).hasSize(8);

        // Os 7 submissionDay da semana consultados
        Set<String> queriedDays = recordedQueries.stream()
                .map(q -> q.expressionAttributeValues().get(":day").s())
                .collect(Collectors.toSet());
        Set<String> expectedDays = week.days().stream()
                .map(LocalDate::toString)
                .collect(Collectors.toSet());
        assertThat(queriedDays).hasSize(7).isEqualTo(expectedDays);

        // Ao menos uma Query com exclusiveStartKey preenchida
        long paginatedQueries = recordedQueries.stream()
                .filter(q -> q.hasExclusiveStartKey() && !q.exclusiveStartKey().isEmpty())
                .count();
        assertThat(paginatedQueries).isEqualTo(1);
    }

    private void flushBatch(List<WriteRequest> batch) {
        Map<String, List<WriteRequest>> requestItems = Map.of(TABLE_NAME, List.copyOf(batch));
        while (requestItems != null && !requestItems.isEmpty()) {
            BatchWriteItemResponse response = dynamoDbClient.batchWriteItem(
                    BatchWriteItemRequest.builder().requestItems(requestItems).build());
            requestItems = response.hasUnprocessedItems() ? response.unprocessedItems() : Map.of();
        }
    }

    private void insertEvaluation(UUID id, String description, int score, Urgency urgency, String submissionDay, String submissionTime) {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("submissionDay", AttributeValue.fromS(submissionDay));
        item.put("submissionKey", AttributeValue.fromS(submissionTime + "#" + id));
        item.put("id", AttributeValue.fromS(id.toString()));
        item.put("description", AttributeValue.fromS(description));
        item.put("score", AttributeValue.fromN(Integer.toString(score)));
        item.put("urgency", AttributeValue.fromS(urgency.name()));
        item.put("submissionTime", AttributeValue.fromS(submissionTime));
        item.put("expiresAt", AttributeValue.fromN(Long.toString(Instant.parse(submissionTime).getEpochSecond() + 86400L * 84L)));

        dynamoDbClient.putItem(PutItemRequest.builder()
                .tableName(TABLE_NAME)
                .item(item)
                .build());
    }
}
