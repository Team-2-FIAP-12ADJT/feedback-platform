/**
 * Função {@code send-urgency-notice}: envia o Urgency Notice de cada Critical Evaluation.
 *
 * <p>O gatilho é o DynamoDB Streams. A lista de Administrators é exigida na subida. A marca
 * {@code urgencyNoticeSentAt} evita um segundo envio, e o lote com falha devolve
 * {@code ReportBatchItemFailures}. O log não leva Description nem endereço de Administrator,
 * conforme as ADRs 0003, 0005, 0009, 0010, 0014, 0017, 0019, 0020 e 0022.</p>
 */
package br.com.fiap.feedbackplatform.sendurgencynotice;
