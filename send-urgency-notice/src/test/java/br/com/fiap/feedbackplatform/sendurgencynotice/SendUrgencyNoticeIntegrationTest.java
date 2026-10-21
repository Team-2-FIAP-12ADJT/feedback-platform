package br.com.fiap.feedbackplatform.sendurgencynotice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.fiap.feedbackplatform.domain.Evaluation;
import br.com.fiap.feedbackplatform.domain.Score;
import com.amazonaws.services.lambda.runtime.events.DynamodbEvent;
import com.amazonaws.services.lambda.runtime.events.DynamodbEvent.DynamodbStreamRecord;
import com.amazonaws.services.lambda.runtime.events.StreamsEventResponse;
import com.amazonaws.services.lambda.runtime.events.StreamsEventResponse.BatchItemFailure;
import com.amazonaws.services.lambda.runtime.events.models.dynamodb.StreamRecord;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeDefinition;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.CreateTableRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement;
import software.amazon.awssdk.services.dynamodb.model.KeyType;
import software.amazon.awssdk.services.dynamodb.model.ProvisionedThroughput;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.SendEmailRequest;
import software.amazon.awssdk.services.ses.model.SendEmailResponse;
import software.amazon.awssdk.services.ses.model.SesException;

/**
 * Testes de integração contra DynamoDB Local em container (Testcontainers), provando a idempotência
 * pela marca {@code urgencyNoticeSentAt} e o comportamento em lote de 10 com falha no item 6 e
 * reentrega dos itens 6 a 10, conforme ADRs 0005, 0010, 0017 e 0022.
 */
class SendUrgencyNoticeIntegrationTest {
    private static final String TABLE = "evaluations-integration-test";
    private static final Instant SUBMISSION_TIME = Instant.parse("2026-11-09T02:30:00Z");
    private static final Instant SENT_AT_TIME = Instant.parse("2026-11-09T02:30:05Z");

    private static GenericContainer<?> container;
    private static DynamoDbClient dynamoDb;
    private static SesClient ses;
    private static SendUrgencyNoticeHandler handler;

    @BeforeAll
    static void startDynamoDb() {
        container = new GenericContainer<>(DockerImageName.parse("amazon/dynamodb-local:3.3.2"))
                .withExposedPorts(8000)
                .withCommand("-jar", "DynamoDBLocal.jar", "-inMemory", "-sharedDb");
        container.start();

        String endpoint = "http://" + container.getHost() + ":" + container.getMappedPort(8000);
        dynamoDb = DynamoDbClient.builder()
                .endpointOverride(URI.create(endpoint))
                .region(Region.SA_EAST_1)
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("test", "test")))
                .httpClientBuilder(UrlConnectionHttpClient.builder())
                .build();

        dynamoDb.createTable(CreateTableRequest.builder()
                .tableName(TABLE)
                .keySchema(
                        KeySchemaElement.builder().attributeName("submissionDay").keyType(KeyType.HASH).build(),
                        KeySchemaElement.builder().attributeName("submissionKey").keyType(KeyType.RANGE).build())
                .attributeDefinitions(
                        AttributeDefinition.builder().attributeName("submissionDay").attributeType("S").build(),
                        AttributeDefinition.builder().attributeName("submissionKey").attributeType("S").build())
                .provisionedThroughput(ProvisionedThroughput.builder()
                        .readCapacityUnits(5L)
                        .writeCapacityUnits(5L)
                        .build())
                .build());

        ses = mock(SesClient.class);
        UrgencyNoticeProperties properties = UrgencyNoticeProperties.of(
                TABLE,
                "admin1@fiap.com.br, admin2@fiap.com.br");
        UrgencyNoticeService service = new UrgencyNoticeService(
                dynamoDb,
                ses,
                Clock.fixed(SENT_AT_TIME, ZoneOffset.UTC),
                properties);
        handler = new SendUrgencyNoticeHandler(service);
    }

    @AfterAll
    static void stopDynamoDb() {
        if (dynamoDb != null) {
            dynamoDb.close();
        }
        if (container != null) {
            container.stop();
        }
    }

    @BeforeEach
    void resetMocks() {
        reset(ses);
    }

    @Test
    void sameEventProcessedTwiceSendsOnlyOneEmailAndPopulatesUrgencyNoticeSentAt() {
        when(ses.sendEmail(any(SendEmailRequest.class)))
                .thenReturn(SendEmailResponse.builder().messageId("ses-msg-1").build());

        Evaluation evaluation = Evaluation.receive(
                UUID.randomUUID(),
                "Avaliação crítica sobre laboratório indisponível",
                new Score(2),
                SUBMISSION_TIME);
        persistItem(evaluation);

        DynamodbEvent event = new DynamodbEvent();
        event.setRecords(List.of(toStreamRecord("seq-dup-1", evaluation)));

        StreamsEventResponse firstResponse = handler.handleRequest(event, null);
        StreamsEventResponse secondResponse = handler.handleRequest(event, null);

        assertTrue(firstResponse.getBatchItemFailures().isEmpty());
        assertTrue(secondResponse.getBatchItemFailures().isEmpty());
        verify(ses, times(1)).sendEmail(any(SendEmailRequest.class));

        Map<String, AttributeValue> stored = getStoredItem(evaluation);
        assertNotNull(stored.get("urgencyNoticeSentAt"));
        assertEquals(SENT_AT_TIME.toString(), stored.get("urgencyNoticeSentAt").s());
        assertEquals(evaluation.description(), stored.get("description").s());
        assertEquals(Integer.toString(evaluation.score().value()), stored.get("score").n());
    }

    @Test
    void batchOfTenWithFailureOnSixthSendsOtherNineAndRedeliverySixToTenSendsOnlySixth() {
        List<Evaluation> evaluations = new ArrayList<>();
        List<DynamodbStreamRecord> allTenRecords = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            Evaluation evaluation = Evaluation.receive(
                    UUID.randomUUID(),
                    "Item crítico do lote número " + i,
                    new Score(i % 4),
                    SUBMISSION_TIME.plusSeconds(i));
            persistItem(evaluation);
            evaluations.add(evaluation);
            allTenRecords.add(toStreamRecord("seq-batch-" + i, evaluation));
        }

        AtomicInteger callCount = new AtomicInteger(0);
        doAnswer(invocation -> {
            int current = callCount.incrementAndGet();
            if (current == 6) {
                throw SesException.builder().message("Simulated SES failure on item 6").build();
            }
            return SendEmailResponse.builder().messageId("msg-" + current).build();
        }).when(ses).sendEmail(any(SendEmailRequest.class));

        DynamodbEvent firstBatch = new DynamodbEvent();
        firstBatch.setRecords(allTenRecords);

        StreamsEventResponse firstResponse = handler.handleRequest(firstBatch, null);

        assertEquals(
                List.of("seq-batch-6"),
                firstResponse.getBatchItemFailures().stream().map(BatchItemFailure::getItemIdentifier).toList());
        verify(ses, times(10)).sendEmail(any(SendEmailRequest.class));

        for (int i = 1; i <= 10; i++) {
            Map<String, AttributeValue> stored = getStoredItem(evaluations.get(i - 1));
            if (i == 6) {
                assertNull(stored.get("urgencyNoticeSentAt"), "Item 6 must not be marked when SES fails");
            } else {
                assertNotNull(stored.get("urgencyNoticeSentAt"), "Item " + i + " must be marked as sent");
                assertEquals(SENT_AT_TIME.toString(), stored.get("urgencyNoticeSentAt").s());
            }
        }

        reset(ses);
        when(ses.sendEmail(any(SendEmailRequest.class)))
                .thenReturn(SendEmailResponse.builder().messageId("msg-retry-6").build());

        DynamodbEvent redeliverySixToTen = new DynamodbEvent();
        redeliverySixToTen.setRecords(allTenRecords.subList(5, 10));

        StreamsEventResponse secondResponse = handler.handleRequest(redeliverySixToTen, null);

        assertTrue(secondResponse.getBatchItemFailures().isEmpty());
        ArgumentCaptor<SendEmailRequest> retryCaptor = ArgumentCaptor.forClass(SendEmailRequest.class);
        verify(ses, times(1)).sendEmail(retryCaptor.capture());
        assertTrue(retryCaptor.getValue().message().body().text().data()
                .contains("Item crítico do lote número 6"));

        for (int i = 1; i <= 10; i++) {
            Map<String, AttributeValue> stored = getStoredItem(evaluations.get(i - 1));
            assertNotNull(stored.get("urgencyNoticeSentAt"));
            assertEquals(SENT_AT_TIME.toString(), stored.get("urgencyNoticeSentAt").s());
        }
    }

    private static void persistItem(Evaluation evaluation) {
        String submissionTime = evaluation.submissionTime().toString();
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("submissionDay", AttributeValue.builder().s(evaluation.submissionDay().toString()).build());
        item.put("submissionKey", AttributeValue.builder().s(submissionTime + "#" + evaluation.id()).build());
        item.put("id", AttributeValue.builder().s(evaluation.id().toString()).build());
        item.put("description", AttributeValue.builder().s(evaluation.description()).build());
        item.put("score", AttributeValue.builder().n(Integer.toString(evaluation.score().value())).build());
        item.put("urgency", AttributeValue.builder().s(evaluation.urgency().name()).build());
        item.put("submissionTime", AttributeValue.builder().s(submissionTime).build());
        item.put("expiresAt", AttributeValue.builder().n(Long.toString(evaluation.expiresAt())).build());
        dynamoDb.putItem(PutItemRequest.builder().tableName(TABLE).item(item).build());
    }

    private static Map<String, AttributeValue> getStoredItem(Evaluation evaluation) {
        String submissionTime = evaluation.submissionTime().toString();
        return dynamoDb.getItem(GetItemRequest.builder()
                .tableName(TABLE)
                .consistentRead(true)
                .key(Map.of(
                        "submissionDay", AttributeValue.builder().s(evaluation.submissionDay().toString()).build(),
                        "submissionKey", AttributeValue.builder().s(submissionTime + "#" + evaluation.id()).build()))
                .build()).item();
    }

    private static DynamodbStreamRecord toStreamRecord(String sequenceNumber, Evaluation evaluation) {
        String submissionTime = evaluation.submissionTime().toString();
        String submissionDay = evaluation.submissionDay().toString();
        String submissionKey = submissionTime + "#" + evaluation.id();

        var attrDay = new com.amazonaws.services.lambda.runtime.events.models.dynamodb.AttributeValue(submissionDay);
        var attrKey = new com.amazonaws.services.lambda.runtime.events.models.dynamodb.AttributeValue(submissionKey);

        Map<String, com.amazonaws.services.lambda.runtime.events.models.dynamodb.AttributeValue> newImage =
                new HashMap<>();
        newImage.put("submissionDay", attrDay);
        newImage.put("submissionKey", attrKey);
        newImage.put("id", new com.amazonaws.services.lambda.runtime.events.models.dynamodb.AttributeValue(
                evaluation.id().toString()));
        newImage.put("description", new com.amazonaws.services.lambda.runtime.events.models.dynamodb.AttributeValue(
                evaluation.description()));
        var scoreAttr = new com.amazonaws.services.lambda.runtime.events.models.dynamodb.AttributeValue();
        scoreAttr.setN(Integer.toString(evaluation.score().value()));
        newImage.put("score", scoreAttr);
        newImage.put("urgency", new com.amazonaws.services.lambda.runtime.events.models.dynamodb.AttributeValue(
                evaluation.urgency().name()));
        newImage.put("submissionTime", new com.amazonaws.services.lambda.runtime.events.models.dynamodb.AttributeValue(
                submissionTime));
        var expiresAttr = new com.amazonaws.services.lambda.runtime.events.models.dynamodb.AttributeValue();
        expiresAttr.setN(Long.toString(evaluation.expiresAt()));
        newImage.put("expiresAt", expiresAttr);

        StreamRecord streamRecord = new StreamRecord()
                .withSequenceNumber(sequenceNumber)
                .withKeys(Map.of("submissionDay", attrDay, "submissionKey", attrKey))
                .withNewImage(newImage);

        DynamodbStreamRecord record = new DynamodbStreamRecord();
        record.setEventID("evt-" + sequenceNumber);
        record.setEventName("INSERT");
        record.setDynamodb(streamRecord);
        return record;
    }
}
