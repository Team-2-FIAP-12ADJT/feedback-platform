package br.com.fiap.feedbackplatform.sendurgencynotice;

import br.com.fiap.feedbackplatform.domain.Evaluation;
import br.com.fiap.feedbackplatform.domain.Score;
import br.com.fiap.feedbackplatform.domain.Urgency;
import br.com.fiap.feedbackplatform.emailsupport.EmailMessage;
import br.com.fiap.feedbackplatform.emailsupport.UrgencyNoticeEmail;
import com.amazonaws.services.lambda.runtime.events.DynamodbEvent;
import com.amazonaws.services.lambda.runtime.events.DynamodbEvent.DynamodbStreamRecord;
import com.amazonaws.services.lambda.runtime.events.StreamsEventResponse;
import com.amazonaws.services.lambda.runtime.events.StreamsEventResponse.BatchItemFailure;
import com.amazonaws.services.lambda.runtime.events.models.dynamodb.StreamRecord;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.ReturnValue;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.Body;
import software.amazon.awssdk.services.ses.model.Content;
import software.amazon.awssdk.services.ses.model.Destination;
import software.amazon.awssdk.services.ses.model.Message;
import software.amazon.awssdk.services.ses.model.SendEmailRequest;

/**
 * Processa lotes do DynamoDB Streams enviando o Urgency Notice de cada Critical Evaluation ainda
 * não notificada e gravando a marca {@code urgencyNoticeSentAt}, conforme ADRs 0001, 0003, 0005,
 * 0009, 0010, 0014, 0017, 0019, 0020, 0022 e 0023.
 */
@Service
public class UrgencyNoticeService {
    private static final Logger LOG = LoggerFactory.getLogger(UrgencyNoticeService.class);
    private static final String CHARSET = StandardCharsets.UTF_8.name();
    private static final String MARK_ATTRIBUTE = "urgencyNoticeSentAt";

    private final DynamoDbClient dynamoDb;
    private final SesClient ses;
    private final Clock clock;
    private final UrgencyNoticeProperties properties;

    /**
     * Cria o serviço com seus clientes AWS, relógio e propriedades validadas, conforme ADRs 0004, 0014 e 0022.
     *
     * @param dynamoDb cliente DynamoDB para ler e gravar a marca {@code urgencyNoticeSentAt}
     * @param ses cliente SES v1 para enviar o Urgency Notice
     * @param clock relógio UTC para o instante da marca
     * @param properties configuração validada de tabela e Administrators
     */
    public UrgencyNoticeService(DynamoDbClient dynamoDb, SesClient ses, Clock clock,
                                UrgencyNoticeProperties properties) {
        this.dynamoDb = Objects.requireNonNull(dynamoDb, "dynamoDb must not be null");
        this.ses = Objects.requireNonNull(ses, "ses must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
    }

    /**
     * Processa os registros do lote em ordem, retornando em {@code batchItemFailures} apenas o
     * {@code SequenceNumber} dos registros que falharam sem interromper os demais, conforme ADRs 0005, 0017 e 0022.
     *
     * @param event evento do DynamoDB Streams recebido pela função
     * @return resposta contendo as falhas parciais do lote
     */
    public StreamsEventResponse process(DynamodbEvent event) {
        if (event == null || event.getRecords() == null || event.getRecords().isEmpty()) {
            return new StreamsEventResponse(List.of());
        }
        List<BatchItemFailure> failures = new ArrayList<>();
        for (DynamodbStreamRecord record : event.getRecords()) {
            if (record == null) {
                continue;
            }
            try {
                processRecord(record);
            } catch (Exception exception) {
                String sequenceNumber = extractSequenceNumber(record);
                failures.add(new BatchItemFailure(sequenceNumber));
                LOG.error("Failed to process urgency notice sequenceNumber={} reason={}",
                        sequenceNumber, exception.getClass().getSimpleName());
            }
        }
        return new StreamsEventResponse(failures);
    }

    private void processRecord(DynamodbStreamRecord record) {
        if (!"INSERT".equals(record.getEventName())) {
            return;
        }
        StreamRecord streamRecord = record.getDynamodb();
        if (streamRecord == null || streamRecord.getNewImage() == null) {
            return;
        }
        Map<String, com.amazonaws.services.lambda.runtime.events.models.dynamodb.AttributeValue> newImage =
                streamRecord.getNewImage();
        var urgencyAttr = newImage.get("urgency");
        if (urgencyAttr == null || !"CRITICAL".equals(urgencyAttr.getS())) {
            return;
        }

        Evaluation evaluation = reconstructEvaluation(newImage);
        Map<String, AttributeValue> key = extractKey(streamRecord, newImage);

        if (isAlreadySent(key)) {
            LOG.info("Urgency notice already sent id={} score={} urgency={} descriptionLength={}",
                    evaluation.id(), evaluation.score().value(), evaluation.urgency(),
                    descriptionLength(evaluation));
            return;
        }

        EmailMessage email = UrgencyNoticeEmail.render(evaluation);
        sendEmail(email);
        markSent(key);

        LOG.info("Urgency notice enviado para {} destinatários id={} score={} urgency={} descriptionLength={}",
                properties.administrators().size(),
                evaluation.id(),
                evaluation.score().value(),
                evaluation.urgency(),
                descriptionLength(evaluation));
    }

    private static Evaluation reconstructEvaluation(
            Map<String, com.amazonaws.services.lambda.runtime.events.models.dynamodb.AttributeValue> newImage) {
        UUID id = UUID.fromString(requireString(newImage, "id"));
        String description = requireString(newImage, "description");
        Score score = new Score(Integer.parseInt(requireNumber(newImage, "score")));
        Urgency urgency = Urgency.valueOf(requireString(newImage, "urgency"));
        Instant submissionTime = Instant.parse(requireString(newImage, "submissionTime"));
        return Evaluation.reconstruct(id, description, score, urgency, submissionTime, null);
    }

    private static Map<String, AttributeValue> extractKey(
            StreamRecord streamRecord,
            Map<String, com.amazonaws.services.lambda.runtime.events.models.dynamodb.AttributeValue> newImage) {
        Map<String, com.amazonaws.services.lambda.runtime.events.models.dynamodb.AttributeValue> source =
                streamRecord.getKeys() != null && !streamRecord.getKeys().isEmpty()
                        ? streamRecord.getKeys()
                        : newImage;
        String submissionDay = requireString(source, "submissionDay");
        String submissionKey = requireString(source, "submissionKey");
        return Map.of(
                "submissionDay", AttributeValue.builder().s(submissionDay).build(),
                "submissionKey", AttributeValue.builder().s(submissionKey).build()
        );
    }

    private boolean isAlreadySent(Map<String, AttributeValue> key) {
        GetItemResponse response = dynamoDb.getItem(GetItemRequest.builder()
                .tableName(properties.table())
                .key(key)
                .consistentRead(true)
                .projectionExpression(MARK_ATTRIBUTE)
                .build());
        if (!response.hasItem()) {
            return false;
        }
        AttributeValue mark = response.item().get(MARK_ATTRIBUTE);
        return mark != null && mark.s() != null && !mark.s().isBlank();
    }

    private void sendEmail(EmailMessage email) {
        ses.sendEmail(SendEmailRequest.builder()
                .source(properties.sender())
                .destination(Destination.builder()
                        .toAddresses(properties.administrators())
                        .build())
                .message(Message.builder()
                        .subject(Content.builder().data(email.subject()).charset(CHARSET).build())
                        .body(Body.builder()
                                .html(Content.builder().data(email.htmlBody()).charset(CHARSET).build())
                                .text(Content.builder().data(email.textBody()).charset(CHARSET).build())
                                .build())
                        .build())
                .build());
    }

    private void markSent(Map<String, AttributeValue> key) {
        String now = clock.instant().toString();
        dynamoDb.updateItem(UpdateItemRequest.builder()
                .tableName(properties.table())
                .key(key)
                .updateExpression("SET urgencyNoticeSentAt = :t")
                .expressionAttributeValues(Map.of(":t", AttributeValue.builder().s(now).build()))
                .returnValues(ReturnValue.NONE)
                .build());
    }

    private static String requireString(
            Map<String, com.amazonaws.services.lambda.runtime.events.models.dynamodb.AttributeValue> map,
            String attribute) {
        var value = map.get(attribute);
        if (value == null || value.getS() == null) {
            throw new IllegalArgumentException("Missing string attribute: " + attribute);
        }
        return value.getS();
    }

    private static String requireNumber(
            Map<String, com.amazonaws.services.lambda.runtime.events.models.dynamodb.AttributeValue> map,
            String attribute) {
        var value = map.get(attribute);
        if (value == null || value.getN() == null) {
            throw new IllegalArgumentException("Missing number attribute: " + attribute);
        }
        return value.getN();
    }

    private static String extractSequenceNumber(DynamodbStreamRecord record) {
        if (record.getDynamodb() != null && record.getDynamodb().getSequenceNumber() != null) {
            return record.getDynamodb().getSequenceNumber();
        }
        return record.getEventID();
    }

    private static int descriptionLength(Evaluation evaluation) {
        return evaluation.description().codePointCount(0, evaluation.description().length());
    }
}
