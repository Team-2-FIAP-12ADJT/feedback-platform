package br.com.fiap.feedbackplatform.receiveevaluation;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterAll;
import java.util.logging.Handler;
import java.util.logging.LogRecord;

/** Prova que falha real do DynamoDB vira erro genérico sem detalhes internos. */
@QuarkusTest
@TestProfile(MissingTableProfile.class)
@QuarkusTestResource(DynamoDbResource.class)
class DynamoDbFailureTest {
    private static Handler handler;
    private static final StringBuilder logs = new StringBuilder();
    private static boolean thrownLogRecord;

    @BeforeAll
    static void captureLogs() {
        handler = new Handler() {
            @Override public void publish(LogRecord record) {
                logs.append(record.getMessage()).append('\n');
                thrownLogRecord |= record.getThrown() != null;
            }
            @Override public void flush() {}
            @Override public void close() {}
        };
        java.util.logging.Logger.getLogger("").addHandler(handler);
    }
    @BeforeEach void clearLogs() { logs.setLength(0); thrownLogRecord = false; }
    @AfterAll static void removeHandler() { java.util.logging.Logger.getLogger("").removeHandler(handler); }

    /** Grava em tabela ausente e verifica o documento problem+json sem stack trace. */
    @Test void tabelaInexistenteRetorna500Generico() {
        var response = given().contentType("application/json").body("{\"descricao\":\"SENTINELA-ERRO\",\"nota\":2}")
                .when().post("/avaliacao").then().statusCode(500)
                .body("keySet()", containsInAnyOrder("type", "title", "status", "detail"))
                .body("type", equalTo("about:blank")).body("title", equalTo("Erro interno"))
                .body("status", equalTo(500))
                .body("detail", equalTo("não foi possível processar a avaliação"))
                .extract().response();
        ProblemAssertions.assertProblemContentType(response);
        assertTrue(logs.toString().contains("ResourceNotFoundException"));
        assertFalse(logs.toString().contains("SENTINELA-ERRO"));
        assertFalse(logs.toString().contains("\tat "));
        assertFalse(logs.toString().contains("Caused by"));
        assertFalse(thrownLogRecord);
    }
}
