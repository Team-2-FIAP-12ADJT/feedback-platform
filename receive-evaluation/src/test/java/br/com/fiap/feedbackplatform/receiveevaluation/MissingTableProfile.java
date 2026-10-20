package br.com.fiap.feedbackplatform.receiveevaluation;

import java.util.Map;
import io.quarkus.test.junit.QuarkusTestProfile;

/** Perfil que aponta para uma tabela inexistente para provar o erro de infraestrutura. */
public class MissingTableProfile implements QuarkusTestProfile {
    /** Retorna o nome deliberadamente inexistente. */
    @Override public Map<String, String> getConfigOverrides() {
        return Map.of("evaluations.table", "table-does-not-exist");
    }
}
