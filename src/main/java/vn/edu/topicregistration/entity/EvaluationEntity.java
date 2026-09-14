package vn.edu.topicregistration.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "evaluations")
public class EvaluationEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "registration_id", nullable = false)
    private RegistrationEntity registration;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "period_id", nullable = false)
    private RegistrationPeriodEntity period;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "evaluator_id", nullable = false)
    private UserAccountEntity evaluator;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "component_id", nullable = false)
    private ScoreComponentEntity component;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "council_id", nullable = false)
    private CouncilEntity council;

    @Column(nullable = false, precision = 12, scale = 4)
    private BigDecimal score;

    @Column(columnDefinition = "TEXT")
    private String feedback;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private EvaluationStatus status;

    @Column(name = "evaluated_at", nullable = false, columnDefinition = "TIMESTAMP(6)")
    private Instant evaluatedAt;

    @Column(name = "submitted_at", columnDefinition = "TIMESTAMP(6)")
    private Instant submittedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected EvaluationEntity() {
    }

    public EvaluationEntity(
            RegistrationEntity registration,
            UserAccountEntity evaluator,
            ScoreComponentEntity component,
            CouncilEntity council,
            BigDecimal score,
            String feedback,
            Instant evaluatedAt) {
        this.registration = Objects.requireNonNull(registration, "registration");
        this.period = Objects.requireNonNull(registration.getPeriod(), "registration.period");
        this.evaluator = Objects.requireNonNull(evaluator, "evaluator");
        this.component = Objects.requireNonNull(component, "component");
        this.council = Objects.requireNonNull(council, "council");
        this.score = Objects.requireNonNull(score, "score");
        this.feedback = normalizedFeedback(feedback);
        this.evaluatedAt = Objects.requireNonNull(evaluatedAt, "evaluatedAt");
        this.status = EvaluationStatus.DRAFT;
    }

    public void saveDraft(BigDecimal score, String feedback, Instant evaluatedAt) {
        update(score, feedback, evaluatedAt);
        this.status = EvaluationStatus.DRAFT;
        this.submittedAt = null;
    }

    public void submit(BigDecimal score, String feedback, Instant submittedAt) {
        update(score, feedback, submittedAt);
        this.status = EvaluationStatus.SUBMITTED;
        this.submittedAt = Objects.requireNonNull(submittedAt, "submittedAt");
    }

    private void update(BigDecimal score, String feedback, Instant evaluatedAt) {
        this.score = Objects.requireNonNull(score, "score");
        this.feedback = normalizedFeedback(feedback);
        this.evaluatedAt = Objects.requireNonNull(evaluatedAt, "evaluatedAt");
    }

    private static String normalizedFeedback(String feedback) {
        if (feedback == null || feedback.isBlank()) {
            return null;
        }
        return feedback.strip();
    }

    public Long getId() {
        return id;
    }

    public RegistrationEntity getRegistration() {
        return registration;
    }

    public RegistrationPeriodEntity getPeriod() {
        return period;
    }

    public UserAccountEntity getEvaluator() {
        return evaluator;
    }

    public ScoreComponentEntity getComponent() {
        return component;
    }

    public CouncilEntity getCouncil() {
        return council;
    }

    public BigDecimal getScore() {
        return score;
    }

    public String getFeedback() {
        return feedback;
    }

    public EvaluationStatus getStatus() {
        return status;
    }

    public Instant getEvaluatedAt() {
        return evaluatedAt;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public long getVersion() {
        return version;
    }
}
