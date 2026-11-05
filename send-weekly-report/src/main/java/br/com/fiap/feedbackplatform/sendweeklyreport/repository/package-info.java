/**
 * Leitura das Evaluations de uma Report Week.
 *
 * <p>São sete consultas {@code Query}, uma por {@code submissionDay}, paginadas até o fim. Não há
 * {@code Scan} e a Urgency gravada não é recalculada, conforme as ADRs 0001, 0008, 0010 e 0019.</p>
 */
package br.com.fiap.feedbackplatform.sendweeklyreport.repository;
