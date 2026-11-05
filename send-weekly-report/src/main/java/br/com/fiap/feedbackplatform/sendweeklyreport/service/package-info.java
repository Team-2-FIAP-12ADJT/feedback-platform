/**
 * Consolidação e envio do Weekly Report aos Administrators.
 *
 * <p>O Average Score e as contagens usam todas as Evaluations da Report Week. Semana vazia também
 * envia. O log não registra Description nem endereço de Administrator, conforme as ADRs 0001, 0008,
 * 0009, 0014, 0020 e 0023.</p>
 */
package br.com.fiap.feedbackplatform.sendweeklyreport.service;
