package br.com.fiap.feedbackplatform.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Objects;
import java.util.UUID;

/**
 * Evaluation imutável recebida ou reconstruída da persistência, conforme ADRs 0001, 0003, 0005 e 0009.
 */
public final class Evaluation {
    private static final ZoneId BRASILIA = ZoneId.of("America/Sao_Paulo");
    private static final long RETENTION_SECONDS = 84L * 24 * 60 * 60;

    private final UUID id;
    private final String description;
    private final Score score;
    private final Urgency urgency;
    private final Instant submissionTime;
    private final Instant urgencyNoticeSentAt;

    private Evaluation(UUID id, String description, Score score, Urgency urgency,
                       Instant submissionTime, Instant urgencyNoticeSentAt) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.description = normalizeDescription(description);
        this.score = Objects.requireNonNull(score, "score must not be null");
        this.urgency = Objects.requireNonNull(urgency, "urgency must not be null");
        this.submissionTime = Objects.requireNonNull(submissionTime, "submissionTime must not be null");
        if (urgencyNoticeSentAt != null && !urgency.isCritical()) {
            throw new IllegalArgumentException("urgencyNoticeSentAt is only allowed for critical evaluations");
        }
        this.urgencyNoticeSentAt = urgencyNoticeSentAt;
    }

    /**
     * Cria uma Evaluation nova e calcula sua Urgency pelo Score, conforme ADR 0001.
     *
     * @param id identificador da Evaluation
     * @param description Description submetida
     * @param score Score submetido
     * @param submissionTime instante de recepção
     * @return Evaluation nova
     */
    public static Evaluation receive(UUID id, String description, Score score, Instant submissionTime) {
        Objects.requireNonNull(score, "score must not be null");
        return new Evaluation(id, description, score, score.urgency(), submissionTime, null);
    }

    /**
     * Reconstrói uma Evaluation preservando a Urgency gravada, conforme ADRs 0001 e 0009.
     *
     * @param id identificador persistido
     * @param description Description persistida
     * @param score Score persistido
     * @param urgency Urgency persistida
     * @param submissionTime instante persistido
     * @param urgencyNoticeSentAt marca persistida do aviso
     * @return Evaluation reconstruída
     */
    public static Evaluation reconstruct(UUID id, String description, Score score, Urgency urgency,
                                         Instant submissionTime, Instant urgencyNoticeSentAt) {
        return new Evaluation(id, description, score, urgency, submissionTime, urgencyNoticeSentAt);
    }

    /**
     * Retorna o identificador, conforme ADR 0009.
     *
     * @return UUID da Evaluation
     */
    public UUID id() { return id; }

    /**
     * Retorna a Description normalizada, conforme ADR 0009.
     *
     * @return Description sem espaços nas pontas
     */
    public String description() { return description; }

    /**
     * Retorna o Score, conforme ADR 0001.
     *
     * @return Score gravado
     */
    public Score score() { return score; }

    /**
     * Retorna a Urgency gravada, conforme ADR 0001.
     *
     * @return Urgency da Evaluation
     */
    public Urgency urgency() { return urgency; }

    /**
     * Retorna o Submission Time, conforme ADR 0009.
     *
     * @return instante de submissão
     */
    public Instant submissionTime() { return submissionTime; }

    /**
     * Retorna a marca do Urgency Notice, conforme ADR 0005.
     *
     * @return instante da marca, ou vazio
     */
    public java.util.Optional<Instant> urgencyNoticeSentAt() {
        return java.util.Optional.ofNullable(urgencyNoticeSentAt);
    }

    /**
     * Calcula o dia de submissão em Brasília, conforme ADRs 0008 e 0010.
     *
     * @return dia local da submissão
     */
    public LocalDate submissionDay() { return submissionTime.atZone(BRASILIA).toLocalDate(); }

    /**
     * Calcula o TTL de 12 semanas em epoch seconds, conforme ADR 0009 e ADR 0010.
     *
     * @return epoch seconds do instante de expiração
     */
    public long expiresAt() { return submissionTime.getEpochSecond() + RETENTION_SECONDS; }

    private static String normalizeDescription(String value) {
        Objects.requireNonNull(value, "description must not be null");
        String normalized = value.strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("description must not be blank");
        }
        if (normalized.codePointCount(0, normalized.length()) > 1_000) {
            throw new IllegalArgumentException("description must not exceed 1000 characters");
        }
        return normalized;
    }
}
