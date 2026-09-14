package vn.edu.topicregistration.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "final_results")
public class FinalResultEntity {
    @Id
    @Column(name = "registration_id")
    private Long registrationId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId
    @JoinColumn(name = "registration_id", nullable = false)
    private RegistrationEntity registration;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private FinalResultStatus status;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "council_id", nullable = false)
    private CouncilEntity council;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "consolidated_by", nullable = false)
    private UserAccountEntity consolidatedBy;

    @Column(name = "consolidated_at", nullable = false, columnDefinition = "TIMESTAMP(6)")
    private Instant consolidatedAt;

    @Column(name = "evaluation_summary", nullable = false, columnDefinition = "TEXT")
    private String evaluationSummary;

    @Column(name = "final_score", nullable = false, precision = 12, scale = 4)
    private BigDecimal finalScore;

    @Column(name = "review_completed_at", nullable = false, columnDefinition = "TIMESTAMP(6)")
    private Instant reviewCompletedAt;

    @Column(name = "grading_completed_at", nullable = false, columnDefinition = "TIMESTAMP(6)")
    private Instant gradingCompletedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "published_by")
    private UserAccountEntity publishedBy;

    @Column(name = "published_at", columnDefinition = "TIMESTAMP(6)")
    private Instant publishedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected FinalResultEntity() {
    }

    public FinalResultEntity(
            RegistrationEntity registration,
            CouncilEntity council,
            UserAccountEntity consolidatedBy,
            Instant completedAt,
            String evaluationSummary,
            BigDecimal finalScore) {
        this.registration = Objects.requireNonNull(registration, "registration");
        this.council = Objects.requireNonNull(council, "council");
        this.consolidatedBy = Objects.requireNonNull(consolidatedBy, "consolidatedBy");
        this.consolidatedAt = Objects.requireNonNull(completedAt, "completedAt");
        this.reviewCompletedAt = completedAt;
        this.gradingCompletedAt = completedAt;
        this.evaluationSummary = Objects.requireNonNull(evaluationSummary, "evaluationSummary");
        this.finalScore = Objects.requireNonNull(finalScore, "finalScore");
        this.status = FinalResultStatus.READY;
    }

    public Long getRegistrationId() {
        return registrationId;
    }

    public RegistrationEntity getRegistration() {
        return registration;
    }

    public FinalResultStatus getStatus() {
        return status;
    }

    public CouncilEntity getCouncil() {
        return council;
    }

    public UserAccountEntity getConsolidatedBy() {
        return consolidatedBy;
    }

    public Instant getConsolidatedAt() {
        return consolidatedAt;
    }

    public String getEvaluationSummary() {
        return evaluationSummary;
    }

    public BigDecimal getFinalScore() {
        return finalScore;
    }

    public Instant getReviewCompletedAt() {
        return reviewCompletedAt;
    }

    public Instant getGradingCompletedAt() {
        return gradingCompletedAt;
    }

    public UserAccountEntity getPublishedBy() {
        return publishedBy;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public long getVersion() {
        return version;
    }
}
