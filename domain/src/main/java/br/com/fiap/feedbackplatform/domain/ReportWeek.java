package br.com.fiap.feedbackplatform.domain;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.WeekFields;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Semana ISO de segunda a domingo em Brasília, conforme ADR 0008.
 */
public record ReportWeek(int year, int week) {
    private static final ZoneId BRASILIA = ZoneId.of("America/Sao_Paulo");
    private static final Pattern FORMAT = Pattern.compile("^(\\d{4})-W(\\d{2})$");
    private static final WeekFields ISO = WeekFields.ISO;

    /**
     * Cria uma ReportWeek válida no calendário ISO, conforme ADR 0008.
     *
     * @param year ano ISO
     * @param week semana ISO
     */
    public ReportWeek {
        if (week < 1 || week > 53) {
            throw new IllegalArgumentException("week must be between 1 and 53");
        }
        LocalDate monday = LocalDate.of(year, 1, 4).with(ISO.weekOfWeekBasedYear(), week)
                .with(ISO.dayOfWeek(), 1);
        if (monday.get(ISO.weekBasedYear()) != year || monday.get(ISO.weekOfWeekBasedYear()) != week) {
            throw new IllegalArgumentException("invalid ISO week");
        }
    }

    /**
     * Retorna o ano ISO da semana, conforme ADR 0008.
     *
     * @return ano ISO
     */
    @Override
    public int year() {
        return year;
    }

    /**
     * Retorna o número ISO da semana, conforme ADR 0008.
     *
     * @return número da semana
     */
    @Override
    public int week() {
        return week;
    }

    /**
     * Faz parse do formato `yyyy-Www`, conforme ADR 0008.
     *
     * @param value texto da semana
     * @return ReportWeek correspondente
     */
    public static ReportWeek parse(String value) {
        Objects.requireNonNull(value, "week must not be null");
        Matcher matcher = FORMAT.matcher(value);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("week must use yyyy-Www format");
        }
        return new ReportWeek(Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2)));
    }

    /**
     * Formata a semana como `yyyy-Www`, conforme ADR 0008.
     *
     * @return identificador ISO
     */
    public String format() { return "%04d-W%02d".formatted(year, week); }

    /**
     * Obtém a semana ISO fechada anterior ao instante em Brasília, conforme ADR 0008.
     *
     * @param reference instante de referência
     * @return semana anterior
     */
    public static ReportWeek previousClosedWeek(Instant reference) {
        Objects.requireNonNull(reference, "reference must not be null");
        LocalDate currentDay = reference.atZone(BRASILIA).toLocalDate();
        LocalDate previousMonday = currentDay.with(ISO.dayOfWeek(), 1).minusWeeks(1);
        return new ReportWeek(previousMonday.get(ISO.weekBasedYear()), previousMonday.get(ISO.weekOfWeekBasedYear()));
    }

    /**
     * Retorna os sete dias da semana, de segunda a domingo, conforme ADR 0008.
     *
     * @return lista imutável de dias
     */
    public List<LocalDate> days() {
        LocalDate monday = LocalDate.of(year, 1, 4).with(ISO.weekBasedYear(), year)
                .with(ISO.weekOfWeekBasedYear(), week).with(ISO.dayOfWeek(), 1);
        return monday.datesUntil(monday.plusDays(7)).toList();
    }

    /**
     * Informa se uma Evaluation pertence a esta semana em Brasília, conforme ADRs 0008 e 0010.
     *
     * @param evaluation Evaluation a verificar
     * @return true quando o dia de submissão está na semana
     */
    public boolean contains(Evaluation evaluation) {
        Objects.requireNonNull(evaluation, "evaluation must not be null");
        return days().contains(evaluation.submissionDay());
    }
}
