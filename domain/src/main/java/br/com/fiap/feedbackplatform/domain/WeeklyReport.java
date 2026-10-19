package br.com.fiap.feedbackplatform.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Agregação pura de uma ReportWeek, conforme ADR 0008, sem recalcular a Urgency gravada.
 */
public final class WeeklyReport {
    /** Limite público de Evaluations listadas no relatório, conforme ADR 0008. */
    public static final int MAX_LISTED_EVALUATIONS = 500;

    private static final Comparator<Evaluation> ORDER = Comparator
            .comparing(Evaluation::urgency)
            .thenComparing(Evaluation::submissionTime)
            .thenComparing(Evaluation::id);

    private final ReportWeek week;
    private final List<Evaluation> allEvaluations;
    private final List<Evaluation> listedEvaluations;
    private final Map<Urgency, Long> countsByUrgency;
    private final Map<LocalDate, Long> countsByDay;

    /**
     * Cria um WeeklyReport validando o pertencimento de todas as Evaluations, conforme ADR 0008.
     *
     * @param week ReportWeek coberta
     * @param evaluations Evaluations da agregação
     * @throws IllegalArgumentException quando uma Evaluation está fora da semana
     */
    public WeeklyReport(ReportWeek week, Collection<Evaluation> evaluations) {
        this.week = Objects.requireNonNull(week, "week must not be null");
        Objects.requireNonNull(evaluations, "evaluations must not be null");
        this.allEvaluations = List.copyOf(evaluations);
        if (allEvaluations.stream().anyMatch(evaluation -> !week.contains(evaluation))) {
            throw new IllegalArgumentException("evaluation does not belong to report week");
        }

        EnumMap<Urgency, Long> urgencyCounts = new EnumMap<>(Urgency.class);
        for (Urgency urgency : Urgency.values()) {
            urgencyCounts.put(urgency, 0L);
        }
        allEvaluations.forEach(evaluation ->
                urgencyCounts.merge(evaluation.urgency(), 1L, Long::sum));
        this.countsByUrgency = Collections.unmodifiableMap(urgencyCounts);

        java.util.LinkedHashMap<LocalDate, Long> dayCounts = new java.util.LinkedHashMap<>();
        week.days().forEach(day -> dayCounts.put(day, 0L));
        allEvaluations.forEach(evaluation ->
                dayCounts.merge(evaluation.submissionDay(), 1L, Long::sum));
        this.countsByDay = Collections.unmodifiableMap(dayCounts);

        ArrayList<Evaluation> sorted = new ArrayList<>(allEvaluations);
        sorted.sort(ORDER);
        this.listedEvaluations = List.copyOf(sorted.subList(0, Math.min(MAX_LISTED_EVALUATIONS, sorted.size())));
    }

    /**
     * Retorna a ReportWeek agregada, conforme ADR 0008.
     *
     * @return semana do relatório
     */
    public ReportWeek week() { return week; }

    /**
     * Retorna a quantidade total, incluindo itens não listados, conforme ADR 0008.
     *
     * @return total de Evaluations
     */
    public int total() { return allEvaluations.size(); }

    /**
     * Calcula a média com duas casas e HALF_UP, conforme ADR 0008.
     *
     * @return média ou vazio quando não há Evaluations
     */
    public Optional<BigDecimal> averageScore() {
        if (allEvaluations.isEmpty()) {
            return Optional.empty();
        }
        BigDecimal sum = allEvaluations.stream()
                .map(evaluation -> BigDecimal.valueOf(evaluation.score().value()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return Optional.of(sum.divide(BigDecimal.valueOf(allEvaluations.size()), 2, RoundingMode.HALF_UP));
    }

    /**
     * Retorna contagens por todas as Urgencies gravadas, conforme ADRs 0001 e 0008.
     *
     * @return mapa imutável com CRITICAL, MEDIUM e LOW
     */
    public Map<Urgency, Long> countsByUrgency() { return countsByUrgency; }

    /**
     * Retorna contagens dos sete dias em ordem de segunda a domingo, conforme ADR 0008.
     *
     * @return mapa imutável ordenado pelos dias da semana
     */
    public Map<LocalDate, Long> countsByDay() { return countsByDay; }

    /**
     * Retorna a lista ordenada e limitada a 500, conforme ADR 0008 e ADR 0010.
     *
     * @return lista imutável de Evaluations listadas
     */
    public List<Evaluation> evaluations() { return listedEvaluations; }

    /**
     * Retorna a quantidade omitida pelo teto da lista, conforme ADR 0008.
     *
     * @return quantidade não listada
     */
    public int notListedCount() { return allEvaluations.size() - listedEvaluations.size(); }
}
