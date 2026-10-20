package br.com.fiap.feedbackplatform.receiveevaluation.api;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * DTO de entrada que preserva a distinção entre campo ausente, null e valor, conforme ADR 0002.
 *
 * @param description Description em português recebida no contrato
 * @param score Score em português recebido no contrato
 */
public record EvaluationRequest(
        @JsonProperty("descricao") String description,
        @JsonProperty("nota") Integer score) {}
