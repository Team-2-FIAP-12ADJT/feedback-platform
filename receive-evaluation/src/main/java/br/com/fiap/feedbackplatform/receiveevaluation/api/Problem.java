package br.com.fiap.feedbackplatform.receiveevaluation.api;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Representa o documento RFC 9457 devolvido pela API, conforme o contrato POST /avaliacao.
 * @param type tipo RFC 9457
 * @param title título público
 * @param status status HTTP
 * @param detail detalhe fixo em português
 * @param field campo inválido, quando aplicável
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Problem(String type, String title, int status, String detail, String field) {}
