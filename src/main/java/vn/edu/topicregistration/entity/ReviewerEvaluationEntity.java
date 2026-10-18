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
@Table(name = "reviewer_evaluations")
public class ReviewerEvaluationEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assignment_id", nullable = false)
    private ReviewerAssignmentEntity assignment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "registration_id", nullable = false)
    private RegistrationEntity registration;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private UserAccountEntity student;

    @Column(nullable = false, precision = 12, scale = 4)
    private BigDecimal score;

    @Column(columnDefinition = "TEXT")
    private String feedback;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private EvaluationStatus status;

    @Column(name = "submitted_at", columnDefinition = "TIMESTAMP(6)")
    private Instant submittedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected ReviewerEvaluationEntity() {
    }

    public ReviewerEvaluationEntity(
            ReviewerAssignmentEntity assignment,
            UserAccountEntity student,
            BigDecimal score,
            String feedback) {
        this.assignment = Objects.requireNonNull(assignment, "assignment");
        this.registration = Objects.requireNonNull(assignment.getRegistration(), "registration");
        this.student = Objects.requireNonNull(student, "student");
        this.score = Objects.requireNonNull(score, "score");
        this.feedback = feedback == null || feedback.isBlank() ? null : feedback.strip();
        this.status = EvaluationStatus.DRAFT;
    }

    public void submit(Instant submittedAt) {
        if (status != EvaluationStatus.DRAFT) {
            throw new IllegalStateException("Reviewer evaluation is already submitted");
        }
        this.submittedAt = Objects.requireNonNull(submittedAt, "submittedAt");
        this.status = EvaluationStatus.SUBMITTED;
    }

    public Long getId() { return id; }
    public ReviewerAssignmentEntity getAssignment() { return assignment; }
    public RegistrationEntity getRegistration() { return registration; }
    public UserAccountEntity getStudent() { return student; }
    public BigDecimal getScore() { return score; }
    public String getFeedback() { return feedback; }
    public EvaluationStatus getStatus() { return status; }
    public Instant getSubmittedAt() { return submittedAt; }
    public long getVersion() { return version; }
}
