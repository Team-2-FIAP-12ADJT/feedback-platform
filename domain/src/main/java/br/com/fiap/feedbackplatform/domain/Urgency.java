package br.com.fiap.feedbackplatform.domain;

/**
 * Classifica uma Evaluation conforme seu Score, conforme ADR 0001.
 */
public enum Urgency {
    CRITICAL,
    MEDIUM,
    LOW;

    /**
     * Deriva a Urgency de um Score validado, conforme ADR 0001.
     *
     * @param score Score da Evaluation
     * @return classificação correspondente
     */
    public static Urgency from(Score score) {
        if (score == null) {
            throw new NullPointerException("score must not be null");
        }
        return switch (score.value()) {
            case 0, 1, 2, 3 -> CRITICAL;
            case 4, 5, 6 -> MEDIUM;
            case 7, 8, 9, 10 -> LOW;
            default -> throw new IllegalStateException("score is outside the valid range");
        };
    }

    /**
     * Informa se esta Urgency identifica uma Critical Evaluation, conforme ADR 0001.
     *
     * @return true apenas para CRITICAL
     */
    public boolean isCritical() {
        return this == CRITICAL;
    }
}
