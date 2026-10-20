package br.com.fiap.feedbackplatform.receiveevaluation;

import java.util.Map;
import io.quarkus.test.junit.QuarkusTestProfile;

/** Perfil de teste com o instante fixo para verificar chaves e TTL. */
public class ReceiveEvaluationTestProfile implements QuarkusTestProfile {
    /** Retorna configurações determinísticas da suíte. */
    @Override
    public Map<String, String> getConfigOverrides() { return Map.of(); }
}
