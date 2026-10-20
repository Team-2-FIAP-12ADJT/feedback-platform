package br.com.fiap.feedbackplatform.sendweeklyreport.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("WeeklyReportConfig — Validação de configuração na inicialização (ADR 0014, 0020, 0023)")
class WeeklyReportConfigTest {

    @Test
    @DisplayName("Falha ao subir quando ADMINISTRATORS for nula (ADR 0020)")
    void shouldFailWhenAdministratorsIsNull() {
        assertThatThrownBy(() -> new WeeklyReportConfig(null, "table", "sa-east-1", null, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ADR 0020");
    }

    @Test
    @DisplayName("Falha ao subir quando ADMINISTRATORS for vazia ou apenas espaços (ADR 0020)")
    void shouldFailWhenAdministratorsIsBlank() {
        assertThatThrownBy(() -> new WeeklyReportConfig("   ", "table", "sa-east-1", null, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ADR 0020");
    }

    @Test
    @DisplayName("Falha ao subir quando ADMINISTRATORS contiver apenas vírgulas (ADR 0020)")
    void shouldFailWhenAdministratorsHasOnlyCommas() {
        assertThatThrownBy(() -> new WeeklyReportConfig(" , , ", "table", "sa-east-1", null, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ADR 0020");
    }

    @Test
    @DisplayName("Falha ao subir quando ADMINISTRATORS contiver e-mail inválido (ADR 0020)")
    void shouldFailWhenAdministratorsHasInvalidEmail() {
        assertThatThrownBy(() -> new WeeklyReportConfig("admin@fiap.com.br,invalido-sem-arroba", "table", "sa-east-1", null, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("E-mail inválido")
                .hasMessageContaining("ADR 0020");
    }

    @Test
    @DisplayName("Inicializa com sucesso e define o primeiro administrador como remetente (ADR 0020, 0023)")
    void shouldInitializeSuccessfullyWithValidAdministrators() {
        WeeklyReportConfig config = new WeeklyReportConfig(
                " admin1@fiap.com.br , admin2@fiap.com.br ",
                "evaluations-table",
                "sa-east-1",
                "http://localhost:8000",
                "http://localhost:4566"
        );

        assertThat(config.administrators())
                .isEqualTo(List.of("admin1@fiap.com.br", "admin2@fiap.com.br"));
        assertThat(config.senderEmail())
                .isEqualTo("admin1@fiap.com.br");
        assertThat(config.formattedSender())
                .isEqualTo("Feedback Platform <admin1@fiap.com.br>");
        assertThat(config.tableName())
                .isEqualTo("evaluations-table");
    }
}
