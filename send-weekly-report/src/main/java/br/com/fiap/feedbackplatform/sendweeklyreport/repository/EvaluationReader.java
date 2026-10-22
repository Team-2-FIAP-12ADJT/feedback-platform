package br.com.fiap.feedbackplatform.sendweeklyreport.repository;

import br.com.fiap.feedbackplatform.domain.Evaluation;
import br.com.fiap.feedbackplatform.domain.ReportWeek;
import java.util.List;

/**
 * Contrato para leitura de Evaluations de uma ReportWeek, conforme ADRs 0003, 0008 e 0010.
 */
public interface EvaluationReader {

    /**
     * Consulta todas as Evaluations registradas nos sete dias de uma ReportWeek.
     *
     * @param week ReportWeek coberta
     * @return lista imutável de Evaluations encontradas
     */
    List<Evaluation> findByReportWeek(ReportWeek week);
}
