/**
 * Montagem do Urgency Notice e do Weekly Report para envio pelo SES.
 *
 * <p>Cada mensagem tem assunto, corpo HTML e versão em texto puro. A Urgency aparece como
 * Crítica, Média ou Baixa. A data usa {@code America/Sao_Paulo} no formato {@code dd/MM/yyyy HH:mm}.
 * A Description é escapada antes de entrar no HTML, conforme as ADRs 0002, 0004, 0008 e 0014.</p>
 */
package br.com.fiap.feedbackplatform.emailsupport;
