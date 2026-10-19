package br.com.fiap.feedbackplatform.domain;

/**
 * Representa o Score inteiro permitido para uma Evaluation, conforme ADR 0001 e o contrato POST /avaliacao.
 */
public record Score(int value) {

    /**
     * Cria um Score validado entre zero e dez, conforme ADR 0001 e o contrato POST /avaliacao.
     *
     * @param value valor inteiro do Score
     * @throws IllegalArgumentException se o valor estiver fora da faixa
     */
    public Score {
        if (value < 0 || value > 10) {
            throw new IllegalArgumentException("score must be between 0 and 10");
        }
    }

    /**
     * Retorna o valor inteiro do Score, conforme ADR 0001.
     *
     * @return valor entre zero e dez
     */
    @Override
    public int value() {
        return value;
    }

    /**
     * Deriva a Urgency do Score, conforme ADR 0001.
     *
     * @return Urgency correspondente à faixa do Score
     */
    public Urgency urgency() {
        return Urgency.from(this);
    }
}
