package br.com.fiap.feedbackplatform.domain;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Testes da classificação Critical Evaluation conforme ADR 0001.
 */
class UrgencyTest {
    @Test
    void identifiesOnlyCriticalUrgency() {
        assertTrue(Urgency.CRITICAL.isCritical());
        assertFalse(Urgency.MEDIUM.isCritical());
        assertFalse(Urgency.LOW.isCritical());
    }
}
