/**
 * Função {@code send-weekly-report}: envia o Weekly Report de uma Report Week.
 *
 * <p>O gatilho é o EventBridge Scheduler, ou uma execução manual que informa a semana. A lista de
 * Administrators é exigida na subida, conforme as ADRs 0003, 0004, 0008, 0017 e 0020.</p>
 */
package br.com.fiap.feedbackplatform.sendweeklyreport;
