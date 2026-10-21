package br.com.fiap.feedbackplatform.sendurgencynotice;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Configuração validada da função `send-urgency-notice`, exigindo a tabela de Evaluations e a lista de
 * Administrators com formato de e-mail válido na inicialização, conforme ADRs 0010, 0020 e 0023.
 *
 * @param table nome da tabela DynamoDB de Evaluations
 * @param administrators lista imutável de e-mails dos Administrators
 * @param sender remetente do SES no formato {@code Feedback Platform <primeiro endereço>}
 */
public record UrgencyNoticeProperties(String table, List<String> administrators, String sender) {
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@.]+$");

    /**
     * Valida os parâmetros já construídos, garantindo que a tabela, os Administrators e o remetente
     * estejam preenchidos e em formato válido, conforme ADRs 0020 e 0023.
     *
     * @param table nome da tabela DynamoDB
     * @param administrators lista de e-mails dos Administrators
     * @param sender remetente formatado para o SES
     */
    public UrgencyNoticeProperties {
        if (table == null || table.isBlank()) {
            throw new IllegalStateException("EVALUATIONS_TABLE must not be blank");
        }
        if (administrators == null || administrators.isEmpty()) {
            throw new IllegalStateException("ADMINISTRATORS must not be empty");
        }
        for (String email : administrators) {
            if (email == null || !EMAIL_PATTERN.matcher(email).matches()) {
                throw new IllegalStateException("Invalid administrator email format");
            }
        }
        administrators = List.copyOf(administrators);
        if (sender == null || sender.isBlank()) {
            throw new IllegalStateException("Sender must not be blank");
        }
    }

    /**
     * Constrói e valida a configuração a partir dos valores brutos de {@code EVALUATIONS_TABLE} e
     * {@code ADMINISTRATORS}, aplicando {@code trim} em cada endereço e derivando o remetente do
     * primeiro Administrator, conforme ADRs 0020 e 0023.
     *
     * @param rawTable valor da variável {@code EVALUATIONS_TABLE}
     * @param rawAdministrators valor da variável {@code ADMINISTRATORS} separado por vírgula
     * @return configuração validada e imutável
     * @throws IllegalStateException se a tabela ou a lista de Administrators estiver ausente, em branco
     *                               ou contiver endereço inválido
     */
    public static UrgencyNoticeProperties of(String rawTable, String rawAdministrators) {
        if (rawTable == null || rawTable.isBlank()) {
            throw new IllegalStateException("EVALUATIONS_TABLE must not be blank");
        }
        if (rawAdministrators == null || rawAdministrators.isBlank()) {
            throw new IllegalStateException("ADMINISTRATORS must not be blank");
        }
        String[] parts = rawAdministrators.split(",", -1);
        List<String> emails = Arrays.stream(parts)
                .map(String::trim)
                .toList();
        if (emails.isEmpty()) {
            throw new IllegalStateException("ADMINISTRATORS must not be empty");
        }
        for (String email : emails) {
            if (email.isEmpty() || !EMAIL_PATTERN.matcher(email).matches()) {
                throw new IllegalStateException("Invalid administrator email format");
            }
        }
        String sender = "Feedback Platform <" + emails.getFirst() + ">";
        return new UrgencyNoticeProperties(rawTable.trim(), emails, sender);
    }
}
