package br.com.fiap.feedbackplatform.receiveevaluation.api;

import br.com.fiap.feedbackplatform.domain.Evaluation;
import br.com.fiap.feedbackplatform.domain.Score;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;

/** Endpoint público POST /avaliacao, conforme ADRs 0002, 0003, 0006 e 0015. */
@Path("/avaliacao")
@ApplicationScoped
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class EvaluationResource {
    private static final Logger LOG = Logger.getLogger(EvaluationResource.class);
    private final DynamoDbClient dynamoDb;
    private final Clock clock;
    private final String table;

    /**
     * Cria o recurso com suas dependências de persistência e tempo.
     * @param dynamoDb cliente DynamoDB
     * @param clock relógio do Submission Time
     * @param table nome da tabela
     */
    @Inject
    public EvaluationResource(DynamoDbClient dynamoDb, Clock clock,
                              @ConfigProperty(name = "evaluations.table") String table) {
        this.dynamoDb = dynamoDb;
        this.clock = clock;
        this.table = table;
    }

    /**
     * Recebe, valida pelo domínio e grava uma Evaluation com PutItem.
     * @param request DTO da requisição
     * @return resposta 201 com id e dataEnvio
     */
    @POST
    public Response receive(EvaluationRequest request) {
        if (request == null) throw new InvalidRequestException(null, "o corpo deve ser um JSON válido");
        if (request.score() == null) throw new InvalidRequestException("nota", "nota é obrigatória");
        if (request.description() == null) throw new InvalidRequestException("descricao", "descricao é obrigatória");
        Evaluation evaluation;
        Score score;
        try {
            score = new Score(request.score());
        } catch (IllegalArgumentException exception) {
            throw new InvalidRequestException("nota", "nota deve ser um inteiro entre 0 e 10");
        }
        Instant submissionTime = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        try {
            evaluation = Evaluation.receive(UUID.randomUUID(), request.description(), score, submissionTime);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new InvalidRequestException("descricao",
                    "descricao deve conter texto com no máximo 1.000 caracteres");
        }
        persist(evaluation);
        LOG.infof("Evaluation received id=%s score=%d urgency=%s descriptionLength=%d",
                evaluation.id(), evaluation.score().value(), evaluation.urgency(),
                evaluation.description().codePointCount(0, evaluation.description().length()));
        return Response.status(Response.Status.CREATED)
                .entity(new EvaluationResponse(evaluation.id(), evaluation.submissionTime())).build();
    }

    private void persist(Evaluation evaluation) {
        String submissionTime = evaluation.submissionTime().toString();
        var item = new java.util.HashMap<String, AttributeValue>();
        item.put("submissionDay", AttributeValue.builder().s(evaluation.submissionDay().toString()).build());
        item.put("submissionKey", AttributeValue.builder().s(submissionTime + "#" + evaluation.id()).build());
        item.put("id", AttributeValue.builder().s(evaluation.id().toString()).build());
        item.put("description", AttributeValue.builder().s(evaluation.description()).build());
        item.put("score", AttributeValue.builder().n(Integer.toString(evaluation.score().value())).build());
        item.put("urgency", AttributeValue.builder().s(evaluation.urgency().name()).build());
        item.put("submissionTime", AttributeValue.builder().s(submissionTime).build());
        item.put("expiresAt", AttributeValue.builder().n(Long.toString(evaluation.expiresAt())).build());
        dynamoDb.putItem(PutItemRequest.builder().tableName(table).item(item).build());
    }
}
