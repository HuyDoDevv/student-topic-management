package vn.edu.topicregistration.entity;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "final_results")
public class FinalResultEntity {
    @EmbeddedId
    private FinalResultId id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("registrationId")
    @JoinColumn(name = "registration_id", nullable = false)
    private RegistrationEntity registration;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("studentId")
    @JoinColumn(name = "student_id", nullable = false)
    private UserAccountEntity student;

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
            UserAccountEntity student,
            CouncilEntity council,
            UserAccountEntity consolidatedBy,
            Instant completedAt,
            String evaluationSummary,
            BigDecimal finalScore) {
        this.registration = Objects.requireNonNull(registration, "registration");
        this.student = Objects.requireNonNull(student, "student");
        this.id = new FinalResultId(registration.getId(), student.getId());
        this.council = Objects.requireNonNull(council, "council");
        this.consolidatedBy = Objects.requireNonNull(consolidatedBy, "consolidatedBy");
        this.consolidatedAt = Objects.requireNonNull(completedAt, "completedAt");
        this.reviewCompletedAt = completedAt;
        this.gradingCompletedAt = completedAt;
        this.evaluationSummary = Objects.requireNonNull(evaluationSummary, "evaluationSummary");
        this.finalScore = Objects.requireNonNull(finalScore, "finalScore");
        this.status = FinalResultStatus.READY;
    }

    public void publish(UserAccountEntity publishedBy, Instant publishedAt) {
        if (status != FinalResultStatus.READY) {
            throw new IllegalStateException("Only a ready result can be published");
        }
        this.publishedBy = Objects.requireNonNull(publishedBy, "publishedBy");
        this.publishedAt = Objects.requireNonNull(publishedAt, "publishedAt");
        this.status = FinalResultStatus.PUBLISHED;
    }

    public Long getRegistrationId() {
        return id == null ? null : id.registrationId;
    }

    public Long getStudentId() {
        return id == null ? null : id.studentId;
    }

    public RegistrationEntity getRegistration() {
        return registration;
    }

    public UserAccountEntity getStudent() {
        return student;
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

    @Embeddable
    public static class FinalResultId implements Serializable {
        @Column(name = "registration_id")
        private Long registrationId;

        @Column(name = "student_id")
        private Long studentId;

        protected FinalResultId() {
        }

        public FinalResultId(Long registrationId, Long studentId) {
            this.registrationId = registrationId;
            this.studentId = studentId;
        }

        public Long getRegistrationId() {
            return registrationId;
        }

        public Long getStudentId() {
            return studentId;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof FinalResultId that)) {
                return false;
            }
            return Objects.equals(registrationId, that.registrationId)
                    && Objects.equals(studentId, that.studentId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(registrationId, studentId);
        }
    }
}
