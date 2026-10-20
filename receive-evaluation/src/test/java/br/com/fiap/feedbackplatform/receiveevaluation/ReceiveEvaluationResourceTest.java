package br.com.fiap.feedbackplatform.receiveevaluation;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.junit.QuarkusMock;
import jakarta.inject.Inject;
import java.time.Clock;
import java.time.ZoneOffset;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import jakarta.ws.rs.core.MediaType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;

/** Prova cada entrada do contrato e a persistência exata da Evaluation. */
@QuarkusTest
@TestProfile(ReceiveEvaluationTestProfile.class)
@QuarkusTestResource(DynamoDbResource.class)
class ReceiveEvaluationResourceTest {
    private static final String JSON = "application/json";
    private static final String PROBLEM = "application/problem+json";
    @Inject DynamoDbClient dynamoDb;
    private static Handler logHandler;
    private static final StringBuilder logs = new StringBuilder();
    private static boolean thrownLogRecord;

    @BeforeAll
    static void installTestClockAndLogCapture() {
        QuarkusMock.installMockForType(Clock.fixed(Instant.parse("2026-11-09T02:30:00.123Z"), ZoneOffset.UTC), Clock.class);
        logHandler = new Handler() {
            @Override public void publish(LogRecord record) {
                logs.append(record.getMessage());
                thrownLogRecord |= record.getThrown() != null;
                if (record.getParameters() != null) {
                    for (Object parameter : record.getParameters()) logs.append(' ').append(parameter);
                }
                logs.append('\n');
            }
            @Override public void flush() {}
            @Override public void close() {}
        };
        java.util.logging.Logger.getLogger("").addHandler(logHandler);
    }

    @AfterAll
    static void removeLogCapture() {
        java.util.logging.Logger.getLogger("").removeHandler(logHandler);
    }

    @BeforeEach
    void clearLogs() { logs.setLength(0); thrownLogRecord = false; }

    @Test void notaAusenteRetorna400ComFieldNota() { invalid("{\"descricao\":\"x\"}", "nota", "nota é obrigatória"); }
    @Test void notaNullRetorna400ComFieldNota() { invalid("{\"descricao\":\"x\",\"nota\":null}", "nota", "nota é obrigatória"); }
    @Test void notaMenosUmRetorna400ComFieldNota() { invalid("{\"descricao\":\"x\",\"nota\":-1}", "nota", "nota deve ser um inteiro entre 0 e 10"); }
    @Test void notaOnzeRetorna400ComFieldNota() { invalid("{\"descricao\":\"x\",\"nota\":11}", "nota", "nota deve ser um inteiro entre 0 e 10"); }
    @Test void notaDecimalRetorna400ComFieldNota() { invalid("{\"descricao\":\"x\",\"nota\":7.5}", "nota", "campo nota possui formato inválido"); }
    @Test void notaDecimalInteiroRetorna400ComFieldNota() { invalid("{\"descricao\":\"x\",\"nota\":7.0}", "nota", "campo nota possui formato inválido"); }
    @Test void notaTextoRetorna400ComFieldNota() { invalid("{\"descricao\":\"x\",\"nota\":\"7\"}", "nota", "campo nota possui formato inválido"); }
    @Test void notaBooleanoRetorna400ComFieldNota() { invalid("{\"descricao\":\"x\",\"nota\":true}", "nota", "campo nota possui formato inválido"); }
    @Test void descricaoAusenteRetorna400ComFieldDescricao() { invalid("{\"nota\":2}", "descricao", "descricao é obrigatória"); }
    @Test void descricaoNullRetorna400ComFieldDescricao() { invalid("{\"descricao\":null,\"nota\":2}", "descricao", "descricao é obrigatória"); }
    @Test void descricaoVaziaRetorna400ComFieldDescricao() { invalid("{\"descricao\":\"\",\"nota\":2}", "descricao", "descricao deve conter texto com no máximo 1.000 caracteres"); }
    @Test void descricaoBrancaRetorna400ComFieldDescricao() { invalid("{\"descricao\":\"   \",\"nota\":2}", "descricao", "descricao deve conter texto com no máximo 1.000 caracteres"); }
    @Test void descricaoTipoNumeroRetorna400ComFieldDescricao() { invalid("{\"descricao\":5,\"nota\":2}", "descricao", "campo descricao possui formato inválido"); }
    @Test void campoDesconhecidoRetorna400ComFieldDoCampo() { invalid("{\"descricao\":\"x\",\"nota\":2,\"urgencia\":\"LOW\"}", "urgencia", "campo desconhecido"); }
    @Test void jsonComVirgulaSobrandoRetorna400SemField() { invalid("{\"descricao\":\"x\",\"nota\":2,}", null, "corpo deve ser um JSON válido"); }
    @Test void corpoNaoJsonRetorna400SemField() { invalid("SENTINELA-NOT-JSON", null, "corpo deve ser um JSON válido"); }
    @Test void corpoVazioRetorna400SemField() { invalid("", null, "o corpo deve ser um JSON válido"); }

    @Test void contentTypeIncorretoRetorna415ProblemJson() {
        String sentinel = "SENTINELA-415-731";
        var response = given().contentType("text/plain").body(sentinel).when().post("/avaliacao")
                .then().statusCode(415).body("type", equalTo("about:blank"))
                .body("title", equalTo("Tipo de conteúdo não suportado"))
                .body("status", equalTo(415))
                .body("detail", equalTo("Content-Type deve ser application/json"))
                .body("$", not(hasKey("field"))).extract().response();
        ProblemAssertions.assertProblemContentType(response);
        assertTrue(logs.toString().contains("field=Content-Type"));
        assertTrue(logs.toString().contains("tipo de conteúdo não suportado"));
        assertFalse(logs.toString().contains(sentinel));
        assertFalse(thrownLogRecord);
    }

    @Test void contentTypeComCharsetRetorna201() {
        var response = given().contentType("application/json; charset=utf-8").body("{\"descricao\":\"charset\",\"nota\":7}")
                .when().post("/avaliacao").then().statusCode(201).extract().response();
        assertEquals("application/json", MediaType.valueOf(response.getContentType()).getType()
                + "/" + MediaType.valueOf(response.getContentType()).getSubtype());
    }

    @Test void descricaoComEspacosNasPontasETrimada() {
        given().contentType(JSON).body("{\"descricao\":\"  trimmed  \",\"nota\":4}").when().post("/avaliacao")
                .then().statusCode(201);
    }

    @Test void descricaoComMilCodePointsIncluindoEmojiRetorna201() {
        String description = "🙂".repeat(600) + "a".repeat(400);
        var response = given().contentType(JSON).body("{\"descricao\":\"" + description + "\",\"nota\":2}").when().post("/avaliacao")
                .then().statusCode(201).extract().response();
        response.then().body("keySet()", containsInAnyOrder("id", "dataEnvio"));
        assertTrue(logs.toString().contains("descriptionLength=1000"));
        assertEquals("2026-11-09T02:30:00Z", response.jsonPath().getString("dataEnvio"));
        UUID id = UUID.fromString(response.jsonPath().getString("id"));
        var item = dynamoDb.scan(r -> r.tableName("evaluations-test")).items().stream()
                .filter(value -> value.get("id").s().equals(id.toString())).findFirst().orElseThrow();
        assertEquals(description, item.get("description").s());
    }

    @Test void descricaoComMilEUmCodePointsRetorna400() {
        String description = "🙂".repeat(600) + "a".repeat(401);
        invalid("{\"descricao\":\"" + description + "\",\"nota\":2}", "descricao",
                "descricao deve conter texto com no máximo 1.000 caracteres");
    }

    @Test void logDe201NaoVazaDescriptionERegistraMetadados() {
        String sentinel = "SENTINELA-SUCESSO-731";
        given().contentType(JSON).body("{\"descricao\":\"" + sentinel + "\",\"nota\":4}").when().post("/avaliacao")
                .then().statusCode(201);
        assertTrue(logs.toString().contains("score=4"));
        assertTrue(logs.toString().contains("urgency=MEDIUM"));
        assertTrue(logs.toString().contains("descriptionLength=" + sentinel.length()));
        assertFalse(logs.toString().contains(sentinel));
        assertFalse(thrownLogRecord);
    }

    @Test void logDe400NaoVazaValorRecusado() {
        String sentinel = "SENTINELA-400-731";
        invalid("{\"descricao\":\"" + sentinel + "\",\"nota\":11}", "nota", "nota deve ser um inteiro entre 0 e 10");
        assertTrue(logs.toString().contains("field=nota"));
        assertFalse(logs.toString().contains(sentinel));
        assertFalse(logs.toString().contains("11"));
        assertFalse(thrownLogRecord);
    }

    @Test void logDeTipoJsonNaoVazaValorRecusado() {
        String sentinel = "SENTINELA-TIPO-731";
        invalid("{\"descricao\":\"" + sentinel + "\",\"nota\":\"7\"}", "nota",
                "campo nota possui formato inválido");
        assertTrue(logs.toString().contains("field=nota"));
        assertFalse(logs.toString().contains(sentinel));
        assertFalse(logs.toString().contains("\"7\""));
        assertFalse(thrownLogRecord);
    }

    @Test void logDeCampoDesconhecidoNaoVazaValorRecusado() {
        String sentinel = "SENTINELA-CAMPO-731";
        invalid("{\"descricao\":\"" + sentinel + "\",\"nota\":2,\"urgencia\":\"" + sentinel + "\"}", "urgencia",
                "campo desconhecido");
        assertTrue(logs.toString().contains("field=urgencia"));
        assertFalse(logs.toString().contains(sentinel));
        assertFalse(thrownLogRecord);
    }

    @Test void logDeJsonMalformadoNaoVazaCorpo() {
        String sentinel = "SENTINELA-MALFORMADO-731";
        invalid("{\"descricao\":\"" + sentinel + "\",}", null, "corpo deve ser um JSON válido");
        assertTrue(logs.toString().contains("field=corpo"));
        assertFalse(logs.toString().contains(sentinel));
        assertFalse(thrownLogRecord);
    }

    @Test void rotaInexistenteNaoRetorna500() {
        given().contentType(JSON).body("{}").when().post("/avaliação").then().statusCode(404);
    }

    @Test void metodoGetNaoRetorna500() {
        given().when().get("/avaliacao").then().statusCode(405);
    }

    @ParameterizedTest
    @CsvSource({"0, CRITICAL", "3, CRITICAL", "4, MEDIUM", "6, MEDIUM", "7, LOW", "10, LOW"})
    void resposta201EItemDynamoTemUrgenciaDaFaixa(int score, String urgency) {
        var response = given().contentType(JSON).body("{\"descricao\":\" item \",\"nota\":" + score + "}").when().post("/avaliacao")
                .then().statusCode(201).header("Location", nullValue()).extract().response();
        UUID id = UUID.fromString(response.jsonPath().getString("id"));
        String time = response.jsonPath().getString("dataEnvio");
        var item = dynamoDb.scan(r -> r.tableName("evaluations-test")).items().stream()
                .filter(value -> value.get("id").s().equals(id.toString())).findFirst().orElseThrow();
        assertEquals(List.of("description", "expiresAt", "id", "score", "submissionDay",
                "submissionKey", "submissionTime", "urgency"),
                item.keySet().stream().sorted().toList());
        assertEquals("item", item.get("description").s());
        assertEquals(Integer.toString(score), item.get("score").n());
        assertEquals(urgency, item.get("urgency").s());
        assertEquals(time, item.get("submissionTime").s());
        assertEquals("2026-11-08", item.get("submissionDay").s());
        assertEquals(time + "#" + id, item.get("submissionKey").s());
        assertEquals(Long.toString(Instant.parse(time).getEpochSecond() + 84L * 24 * 60 * 60),
                item.get("expiresAt").n());
    }

    private void invalid(String body, String field, String detail) {
        var response = given().contentType(JSON).body(body).when().post("/avaliacao").then()
                .statusCode(400).body("type", equalTo("about:blank"))
                .body("title", equalTo("Avaliação inválida")).body("status", equalTo(400));
        var raw = response.extract().response();
        ProblemAssertions.assertProblemContentType(raw);
        if (field == null) response.body("$", not(hasKey("field")));
        else response.body("field", equalTo(field));
        response.body("detail", equalTo(detail));
        assertFalse(thrownLogRecord);
    }

}
