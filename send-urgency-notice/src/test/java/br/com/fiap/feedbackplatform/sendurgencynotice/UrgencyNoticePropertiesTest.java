package br.com.fiap.feedbackplatform.sendurgencynotice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Prova que a aplicação falha ao iniciar quando {@code ADMINISTRATORS} ou {@code EVALUATIONS_TABLE}
 * estão vazias, em branco ou com formato de e-mail inválido, e que deriva o remetente do primeiro
 * Administrator, conforme ADRs 0020 e 0023.
 */
class UrgencyNoticePropertiesTest {

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "\t\n"})
    void applicationFailsToStartWhenAdministratorsIsEmptyOrBlank(String invalidAdmins) {
        SpringApplication app = new SpringApplication(SendUrgencyNoticeApplication.class);
        assertThrows(BeanCreationException.class, () -> {
            try (ConfigurableApplicationContext ignored = app.run(
                    "--evaluations.table=evaluations-test",
                    "--administrators=" + invalidAdmins)) {
            }
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "invalido",
            "sem-dominio@",
            "@dominio.com",
            "admin@dominio",
            "admin@dominio.",
            "admin 1@fiap.com.br",
            "admin1@fiap.com.br, invalido",
            "admin1@fiap.com.br, , admin2@fiap.com.br"
    })
    void applicationFailsToStartWhenAnyAdministratorEmailIsInvalid(String invalidAdmins) {
        SpringApplication app = new SpringApplication(SendUrgencyNoticeApplication.class);
        assertThrows(BeanCreationException.class, () -> {
            try (ConfigurableApplicationContext ignored = app.run(
                    "--evaluations.table=evaluations-test",
                    "--administrators=" + invalidAdmins)) {
            }
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void applicationFailsToStartWhenEvaluationsTableIsEmptyOrBlank(String invalidTable) {
        SpringApplication app = new SpringApplication(SendUrgencyNoticeApplication.class);
        assertThrows(BeanCreationException.class, () -> {
            try (ConfigurableApplicationContext ignored = app.run(
                    "--evaluations.table=" + invalidTable,
                    "--administrators=admin1@fiap.com.br")) {
            }
        });
    }

    @Test
    void trimsAdministratorsAndBuildsSenderFromFirstAddress() {
        System.setProperty("aws.region", "sa-east-1");
        SpringApplication app = new SpringApplication(SendUrgencyNoticeApplication.class);
        try (ConfigurableApplicationContext context = app.run(
                "--evaluations.table= evaluations-prod ",
                "--administrators=  primeiro@fiap.com.br , segundo@gmail.com ,terceiro@outlook.com  ")) {
            UrgencyNoticeProperties props = context.getBean(UrgencyNoticeProperties.class);
            assertEquals("evaluations-prod", props.table());
            assertEquals(List.of("primeiro@fiap.com.br", "segundo@gmail.com", "terceiro@outlook.com"),
                    props.administrators());
            assertEquals("Feedback Platform <primeiro@fiap.com.br>", props.sender());
        }
    }
}
