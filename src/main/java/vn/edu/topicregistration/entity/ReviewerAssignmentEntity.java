package vn.edu.topicregistration.entity;

import java.time.Instant;
import java.time.LocalDate;
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
@Table(name = "reviewer_assignments")
public class ReviewerAssignmentEntity {
    public enum Status { LEGACY_UNVERIFIED, ASSIGNED, SUBMITTED, REPLACED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "registration_id", nullable = false)
    private RegistrationEntity registration;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lecturer_id", nullable = false)
    private UserAccountEntity reviewer;

    @Column(name = "deadline_date")
    private LocalDate deadlineDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private Status status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_by")
    private UserAccountEntity assignedBy;

    @Column(name = "assigned_at", columnDefinition = "TIMESTAMP(6)")
    private Instant assignedAt;

    @Column(name = "submitted_at", columnDefinition = "TIMESTAMP(6)")
    private Instant submittedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "replaced_by")
    private UserAccountEntity replacedBy;

    @Column(name = "replaced_at", columnDefinition = "TIMESTAMP(6)")
    private Instant replacedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected ReviewerAssignmentEntity() {
    }

    public ReviewerAssignmentEntity(
            RegistrationEntity registration,
            UserAccountEntity reviewer,
            LocalDate deadlineDate,
            UserAccountEntity assignedBy,
            Instant assignedAt) {
        this.registration = Objects.requireNonNull(registration, "registration");
        this.reviewer = Objects.requireNonNull(reviewer, "reviewer");
        this.deadlineDate = Objects.requireNonNull(deadlineDate, "deadlineDate");
        this.assignedBy = Objects.requireNonNull(assignedBy, "assignedBy");
        this.assignedAt = Objects.requireNonNull(assignedAt, "assignedAt");
        this.status = Status.ASSIGNED;
    }

    public void markSubmitted(Instant submittedAt) {
        if (status != Status.ASSIGNED) {
            throw new IllegalStateException("Only an assigned reviewer can submit");
        }
        this.submittedAt = Objects.requireNonNull(submittedAt, "submittedAt");
        this.status = Status.SUBMITTED;
    }

    public void markReplaced(UserAccountEntity replacedBy, Instant replacedAt) {
        if (status != Status.ASSIGNED) {
            throw new IllegalStateException("Only an unsubmitted assignment can be replaced");
        }
        this.replacedBy = Objects.requireNonNull(replacedBy, "replacedBy");
        this.replacedAt = Objects.requireNonNull(replacedAt, "replacedAt");
        this.status = Status.REPLACED;
    }

    public Long getId() { return id; }
    public RegistrationEntity getRegistration() { return registration; }
    public UserAccountEntity getReviewer() { return reviewer; }
    public LocalDate getDeadlineDate() { return deadlineDate; }
    public Status getStatus() { return status; }
    public UserAccountEntity getAssignedBy() { return assignedBy; }
    public Instant getAssignedAt() { return assignedAt; }
    public Instant getSubmittedAt() { return submittedAt; }
    public UserAccountEntity getReplacedBy() { return replacedBy; }
    public Instant getReplacedAt() { return replacedAt; }
    public long getVersion() { return version; }
}
