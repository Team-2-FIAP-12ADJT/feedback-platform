package br.com.fiap.feedbackplatform.receiveevaluation.api;

import java.time.Instant;
import java.util.UUID;

/**
 * Resposta pública de uma Evaluation recebida, sem expor a Urgency.
 *
 * @param id identificador da Evaluation
 * @param dataEnvio Submission Time no contrato
 */
public record EvaluationResponse(UUID id, Instant dataEnvio) {}
