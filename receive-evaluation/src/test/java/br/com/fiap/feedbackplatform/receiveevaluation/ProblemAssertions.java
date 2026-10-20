package br.com.fiap.feedbackplatform.receiveevaluation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.restassured.response.Response;
import jakarta.ws.rs.core.MediaType;

final class ProblemAssertions {
    private ProblemAssertions() {}

    static void assertProblemContentType(Response response) {
        MediaType mediaType = MediaType.valueOf(response.getContentType());
        assertEquals("application", mediaType.getType());
        assertEquals("problem+json", mediaType.getSubtype());
    }
}
