package br.com.fiap.feedbackplatform.receiveevaluation.api;

/** Erro de validação de entrada, traduzido para RFC 9457 pelo mapper. */
public class InvalidRequestException extends RuntimeException {
    private final String field;
    /**
     * Cria erro com campo e detalhe fixo, sem armazenar o valor recusado.
     * @param field campo do contrato
     * @param detail detalhe fixo sem valor recebido
     */
    public InvalidRequestException(String field, String detail) { super(detail); this.field = field; }
    /**
     * Retorna o campo do contrato que falhou.
     * @return campo ou null quando o corpo inteiro é inválido
     */
    public String field() { return field; }
}
