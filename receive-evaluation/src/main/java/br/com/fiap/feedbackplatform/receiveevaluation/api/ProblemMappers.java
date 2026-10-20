package br.com.fiap.feedbackplatform.receiveevaluation.api;

import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import jakarta.ws.rs.NotSupportedException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

/** Centraliza as respostas RFC 9457 e os logs de borda, conforme ADR 0009. */
public class ProblemMappers {
    private static final Logger LOG = Logger.getLogger(ProblemMappers.class);

    /** Mapeia propriedade JSON desconhecida.
     * @param exception exceção com o nome do campo
     * @return resposta 400
     */
    @ServerExceptionMapper(priority = 1)
    public Response map(UnrecognizedPropertyException exception) {
        return invalid(exception.getPropertyName(), "campo desconhecido");
    }

    /** Mapeia tipos JSON incompatíveis.
     * @param exception exceção Jackson
     * @return resposta 400
     */
    @ServerExceptionMapper(priority = 1)
    public Response map(MismatchedInputException exception) {
        String field = field(exception);
        return invalid(field, field == null ? "corpo deve ser um JSON válido"
                : "campo " + field + " possui formato inválido");
    }

    /** Mapeia validações do domínio sem ecoar valores.
     * @param exception erro de validação
     * @return resposta 400
     */
    @ServerExceptionMapper(priority = 1)
    public Response map(InvalidRequestException exception) {
        return invalid(exception.field(), exception.getMessage());
    }

    /** Mapeia Content-Type rejeitado.
     * @param exception exceção JAX-RS
     * @return resposta 415
     */
    @ServerExceptionMapper(priority = 1)
    public Response map(NotSupportedException exception) {
        LOG.warnf("Invalid request field=Content-Type reason=tipo de conteúdo não suportado");
        return problem(415, "Tipo de conteúdo não suportado",
                "Content-Type deve ser application/json", null);
    }

    /** Preserva respostas 404/405 do JAX-RS.
     * @param exception exceção HTTP
     * @return resposta problem+json
     */
    @ServerExceptionMapper(priority = 2)
    public Response map(WebApplicationException exception) {
        int status = exception.getResponse().getStatus();
        // JSON malformado chega ao Quarkus REST como WebApplicationException 400.
        String title = status == 404 ? "Não encontrado"
                : status == 405 ? "Método não permitido" : "Avaliação inválida";
        String detail = status == 404 ? "rota não encontrada"
                : status == 405 ? "método não permitido" : "corpo deve ser um JSON válido";
        LOG.warnf("Invalid request field=%s reason=%s", status == 400 ? "corpo" : "rota", detail);
        return problem(status, title, detail, null);
    }

    /** Converte falhas de infraestrutura para erro genérico.
     * @param exception falha inesperada
     * @return resposta 500
     */
    @ServerExceptionMapper(priority = 1000)
    public Response map(Throwable exception) {
        LOG.errorf("Infrastructure failure class=%s message=%s",
                exception.getClass().getName(), exception.getMessage());
        return problem(500, "Erro interno", "não foi possível processar a avaliação", null);
    }

    private Response invalid(String field, String detail) {
        LOG.warnf("Invalid request field=%s reason=%s", field == null ? "corpo" : field, detail);
        return problem(400, "Avaliação inválida", detail, field);
    }

    private static String field(MismatchedInputException exception) {
        if (exception.getPath().isEmpty()) return null;
        return exception.getPath().get(exception.getPath().size() - 1).getFieldName();
    }

    private static Response problem(int status, String title, String detail, String field) {
        return Response.status(status).type(MediaType.valueOf("application/problem+json"))
                .entity(new Problem("about:blank", title, status, detail, field)).build();
    }
}
