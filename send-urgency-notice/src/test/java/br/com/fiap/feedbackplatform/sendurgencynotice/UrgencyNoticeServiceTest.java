package br.com.fiap.feedbackplatform.sendurgencynotice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.amazonaws.services.lambda.runtime.events.DynamodbEvent;
import com.amazonaws.services.lambda.runtime.events.DynamodbEvent.DynamodbStreamRecord;
import com.amazonaws.services.lambda.runtime.events.StreamsEventResponse;
import com.amazonaws.services.lambda.runtime.events.StreamsEventResponse.BatchItemFailure;
import com.amazonaws.services.lambda.runtime.events.models.dynamodb.StreamRecord;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.ReturnValue;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemResponse;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.SendEmailRequest;
import software.amazon.awssdk.services.ses.model.SendEmailResponse;

/**
 * Testes unitários do {@link UrgencyNoticeService} e {@link SendUrgencyNoticeHandler}, provando
 * guarda de evento/Urgency gravada, contrato do GetItem/SendEmail/UpdateItem, higienização de logs
 * e relato de falhas parciais, conforme ADRs 0001, 0005, 0009, 0014, 0019, 0020, 0022 e 0023.
 */
class UrgencyNoticeServiceTest {
    private static final String TABLE = "evaluations-unit";
    private static final Instant FIXED_NOW = Instant.parse("2026-11-09T03:00:00Z");

    private DynamoDbClient dynamoDb;
    private SesClient ses;
    private UrgencyNoticeProperties properties;
    private SendUrgencyNoticeHandler handler;
    private Logger serviceLogger;
    private ListAppender<ILoggingEvent> logAppender;

    @BeforeEach
    void setUp() {
        dynamoDb = mock(DynamoDbClient.class);
        ses = mock(SesClient.class);
        properties = UrgencyNoticeProperties.of(
                TABLE,
                "admin-alfa-771@fiap.com.br, admin-beta-882@gmail.com");
        UrgencyNoticeService service = new UrgencyNoticeService(
                dynamoDb, ses, Clock.fixed(FIXED_NOW, ZoneOffset.UTC), properties);
        handler = new SendUrgencyNoticeHandler(service);

        serviceLogger = (Logger) LoggerFactory.getLogger(UrgencyNoticeService.class);
        logAppender = new ListAppender<>();
        logAppender.start();
        serviceLogger.addAppender(logAppender);
    }

    @AfterEach
    void tearDown() {
        if (serviceLogger != null && logAppender != null) {
            serviceLogger.detachAppender(logAppender);
        }
    }

    @ParameterizedTest
    @CsvSource({
            "INSERT, MEDIUM, 2",
            "INSERT, LOW, 1",
            "MODIFY, CRITICAL, 1",
            "REMOVE, CRITICAL, 0"
    })
    void ignoresNonInsertOrNonCriticalStoredUrgencyWithoutRecalculatingFromScore(
            String eventName, String storedUrgency, int scoreValue) {
        DynamodbEvent event = new DynamodbEvent();
        event.setRecords(List.of(buildRecord("seq-1", eventName, UUID.randomUUID(),
                "Descricao com nota baixa mas urgency gravada nao critica", scoreValue, storedUrgency)));

        StreamsEventResponse response = handler.handleRequest(event, null);

        assertTrue(response.getBatchItemFailures().isEmpty());
        verifyNoInteractions(dynamoDb, ses);
    }

    @Test
    void sendsUrgencyNoticeMarksItemAndLogsWithoutDescriptionOrAdminEmails() {
        when(dynamoDb.getItem(any(GetItemRequest.class)))
                .thenReturn(GetItemResponse.builder().item(Map.of()).build());
        when(ses.sendEmail(any(SendEmailRequest.class)))
                .thenReturn(SendEmailResponse.builder().messageId("msg-1").build());
        when(dynamoDb.updateItem(any(UpdateItemRequest.class)))
                .thenReturn(UpdateItemResponse.builder().build());

        UUID id = UUID.fromString("11111111-2222-3333-4444-555555555555");
        String sentinelDescription = "SENTINELA-DESCRICAO-SECRETA-991 <script>alert(1)</script>";
        DynamodbEvent event = new DynamodbEvent();
        event.setRecords(List.of(buildRecord("seq-10", "INSERT", id, sentinelDescription, 1, "CRITICAL")));

        StreamsEventResponse response = handler.handleRequest(event, null);

        assertTrue(response.getBatchItemFailures().isEmpty());

        ArgumentCaptor<GetItemRequest> getCaptor = ArgumentCaptor.forClass(GetItemRequest.class);
        verify(dynamoDb).getItem(getCaptor.capture());
        GetItemRequest getReq = getCaptor.getValue();
        assertEquals(TABLE, getReq.tableName());
        assertTrue(getReq.consistentRead());
        assertEquals("urgencyNoticeSentAt", getReq.projectionExpression());
        assertEquals("2026-11-08", getReq.key().get("submissionDay").s());
        assertEquals("2026-11-09T02:30:00Z#" + id, getReq.key().get("submissionKey").s());

        ArgumentCaptor<SendEmailRequest> sesCaptor = ArgumentCaptor.forClass(SendEmailRequest.class);
        verify(ses).sendEmail(sesCaptor.capture());
        SendEmailRequest sesReq = sesCaptor.getValue();
        assertEquals("Feedback Platform <admin-alfa-771@fiap.com.br>", sesReq.source());
        assertEquals(List.of("admin-alfa-771@fiap.com.br", "admin-beta-882@gmail.com"),
                sesReq.destination().toAddresses());
        assertEquals("Aviso de urgência: avaliação crítica", sesReq.message().subject().data());
        assertEquals("UTF-8", sesReq.message().subject().charset());
        assertEquals("UTF-8", sesReq.message().body().html().charset());
        assertEquals("UTF-8", sesReq.message().body().text().charset());
        assertTrue(sesReq.message().body().html().data().contains("&lt;script&gt;alert(1)&lt;/script&gt;"));
        assertTrue(sesReq.message().body().text().data().contains(sentinelDescription));

        ArgumentCaptor<UpdateItemRequest> updateCaptor = ArgumentCaptor.forClass(UpdateItemRequest.class);
        verify(dynamoDb).updateItem(updateCaptor.capture());
        UpdateItemRequest updateReq = updateCaptor.getValue();
        assertEquals(TABLE, updateReq.tableName());
        assertEquals("SET urgencyNoticeSentAt = :t", updateReq.updateExpression());
        assertEquals(FIXED_NOW.toString(), updateReq.expressionAttributeValues().get(":t").s());
        assertEquals(ReturnValue.NONE, updateReq.returnValues());

        String allLogs = logAppender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .collect(Collectors.joining("\n"));
        assertTrue(allLogs.contains("enviado para 2 destinatários"));
        assertTrue(allLogs.contains("id=" + id));
        assertTrue(allLogs.contains("score=1"));
        assertTrue(allLogs.contains("urgency=CRITICAL"));
        assertTrue(allLogs.contains("descriptionLength=" + sentinelDescription.length()));
        assertFalse(allLogs.contains(sentinelDescription));
        assertFalse(allLogs.contains("SENTINELA-DESCRICAO-SECRETA-991"));
        assertFalse(allLogs.contains("admin-alfa-771@fiap.com.br"));
        assertFalse(allLogs.contains("admin-beta-882@gmail.com"));
    }

    @Test
    void failureInUpdateItemOrReconstructionReportsSequenceNumberWithoutLeakingSensitiveData() {
        when(dynamoDb.getItem(any(GetItemRequest.class)))
                .thenReturn(GetItemResponse.builder().item(Map.of()).build());
        when(ses.sendEmail(any(SendEmailRequest.class)))
                .thenReturn(SendEmailResponse.builder().messageId("msg-1").build());
        when(dynamoDb.updateItem(any(UpdateItemRequest.class)))
                .thenThrow(new RuntimeException("SENTINELA-FALHA-UPDATE"));

        DynamodbStreamRecord r1 = buildRecord("seq-err-1", "INSERT", UUID.randomUUID(),
                "SENTINELA-DESC-FALHA-1", 0, "CRITICAL");
        DynamodbStreamRecord r2 = buildRecord("seq-err-2", "INSERT", UUID.randomUUID(),
                "   ", 0, "CRITICAL");

        DynamodbEvent event = new DynamodbEvent();
        event.setRecords(List.of(r1, r2));

        StreamsEventResponse response = handler.handleRequest(event, null);

        assertEquals(
                List.of("seq-err-1", "seq-err-2"),
                response.getBatchItemFailures().stream().map(BatchItemFailure::getItemIdentifier).toList());
        verify(ses, times(1)).sendEmail(any(SendEmailRequest.class));

        String allLogs = logAppender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .collect(Collectors.joining("\n"));
        assertFalse(allLogs.contains("SENTINELA-DESC-FALHA-1"));
        assertFalse(allLogs.contains("admin-alfa-771@fiap.com.br"));
    }

    private static DynamodbStreamRecord buildRecord(
            String sequenceNumber, String eventName, UUID id, String description, int score, String urgency) {
        String submissionTime = "2026-11-09T02:30:00Z";
        String submissionDay = "2026-11-08";
        String submissionKey = submissionTime + "#" + id;

        var attrDay = new com.amazonaws.services.lambda.runtime.events.models.dynamodb.AttributeValue(submissionDay);
        var attrKey = new com.amazonaws.services.lambda.runtime.events.models.dynamodb.AttributeValue(submissionKey);

        Map<String, com.amazonaws.services.lambda.runtime.events.models.dynamodb.AttributeValue> newImage =
                new HashMap<>();
        newImage.put("submissionDay", attrDay);
        newImage.put("submissionKey", attrKey);
        newImage.put("id", new com.amazonaws.services.lambda.runtime.events.models.dynamodb.AttributeValue(id.toString()));
        newImage.put("description", new com.amazonaws.services.lambda.runtime.events.models.dynamodb.AttributeValue(description));
        var scoreAttr = new com.amazonaws.services.lambda.runtime.events.models.dynamodb.AttributeValue();
        scoreAttr.setN(Integer.toString(score));
        newImage.put("score", scoreAttr);
        newImage.put("urgency", new com.amazonaws.services.lambda.runtime.events.models.dynamodb.AttributeValue(urgency));
        newImage.put("submissionTime", new com.amazonaws.services.lambda.runtime.events.models.dynamodb.AttributeValue(submissionTime));

        StreamRecord streamRecord = new StreamRecord()
                .withSequenceNumber(sequenceNumber)
                .withKeys(Map.of("submissionDay", attrDay, "submissionKey", attrKey))
                .withNewImage(newImage);

        DynamodbStreamRecord record = new DynamodbStreamRecord();
        record.setEventID("evt-" + sequenceNumber);
        record.setEventName(eventName);
        record.setDynamodb(streamRecord);
        return record;
    }
}
