package br.com.fiap.feedbackplatform.emailsupport;

/**
 * Conteúdo renderizado de um e-mail para envio pelo SES, conforme ADRs 0004, 0008 e 0014.
 *
 * @param subject assunto
 * @param htmlBody corpo HTML
 * @param textBody corpo em texto puro
 */
public record EmailMessage(String subject, String htmlBody, String textBody) {
}
