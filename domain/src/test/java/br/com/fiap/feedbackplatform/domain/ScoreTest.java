package br.com.fiap.feedbackplatform.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Testes do Score conforme ADR 0001 e contrato POST /avaliacao.
 */
class ScoreTest {
    @ParameterizedTest
    @CsvSource({
            "0, CRITICAL", "1, CRITICAL", "2, CRITICAL", "3, CRITICAL",
            "4, MEDIUM", "5, MEDIUM", "6, MEDIUM",
            "7, LOW", "8, LOW", "9, LOW", "10, LOW"
    })
    void mapsEveryScoreToExpectedUrgency(int value, Urgency expected) {
        assertEquals(expected, new Score(value).urgency());
    }

    @Test
    void rejectsScoresOutsideRange() {
        assertThrows(IllegalArgumentException.class, () -> new Score(-1));
        assertThrows(IllegalArgumentException.class, () -> new Score(11));
    }
}
