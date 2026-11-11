/**
 * Borda HTTP da função {@code receive-evaluation}: {@code POST /avaliacao}.
 *
 * <p>Os campos do contrato ficam em português ({@code descricao}, {@code nota} e {@code dataEnvio}).
 * A Urgency não volta na resposta. Entrada inválida vira {@code application/problem+json}, sem ecoar
 * o valor recusado nem a Description no log, conforme o contrato da rota e as ADRs 0001, 0002, 0003,
 * 0006, 0009, 0010 e 0019.</p>
 */
package br.com.fiap.feedbackplatform.receiveevaluation.api;
