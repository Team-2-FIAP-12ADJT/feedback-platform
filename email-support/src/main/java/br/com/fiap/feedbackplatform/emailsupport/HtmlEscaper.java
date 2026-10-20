package br.com.fiap.feedbackplatform.emailsupport;

/**
 * Escapa caracteres especiais de Description interpolada em HTML, conforme ADRs 0004 e 0008.
 */
final class HtmlEscaper {
    private HtmlEscaper() {
    }

    /**
     * Escapa {@code &}, {@code <}, {@code >}, {@code "} e {@code '} para proteger
     * Description no HTML do Urgency Notice e do Weekly Report, conforme ADRs 0004 e 0008.
     *
     * @param value Description a escapar
     * @return Description segura para interpolação HTML
     */
    static String escape(String value) {
        StringBuilder escaped = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            switch (value.charAt(index)) {
                case '&' -> escaped.append("&amp;");
                case '<' -> escaped.append("&lt;");
                case '>' -> escaped.append("&gt;");
                case '"' -> escaped.append("&quot;");
                case '\'' -> escaped.append("&#39;");
                default -> escaped.append(value.charAt(index));
            }
        }
        return escaped.toString();
    }
}
