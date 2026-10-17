package vn.edu.topicregistration.entity;


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
@Table(name = "registrations")
public class RegistrationEntity {
    public static final String TOPIC_TAKEN_REASON = "Đề tài đã đủ số lượng nhóm";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "period_id", nullable = false)
    private RegistrationPeriodEntity period;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id", nullable = false)
    private StudentGroupEntity group;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "topic_id", nullable = false)
    private TopicEntity topic;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private RegistrationStatus status;

    @Column(name = "submitted_at", nullable = false, columnDefinition = "TIMESTAMP(6)")
    private Instant submittedAt;

    @Column(name = "decided_at", columnDefinition = "TIMESTAMP(6)")
    private Instant decidedAt;

    @Column(name = "withdrawn_at", columnDefinition = "TIMESTAMP(6)")
    private Instant withdrawnAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "decision_actor_id")
    private UserAccountEntity decisionActor;

    @Enumerated(EnumType.STRING)
    @Column(name = "rejection_kind", length = 32)
    private RegistrationRejectionKind rejectionKind;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    @Version
    @Column(nullable = false)
    private long version;

    protected RegistrationEntity() {
    }

    public RegistrationEntity(
            RegistrationPeriodEntity period,
            StudentGroupEntity group,
            TopicEntity topic,
            Instant submittedAt) {
        this.period = Objects.requireNonNull(period, "period");
        this.group = Objects.requireNonNull(group, "group");
        this.topic = Objects.requireNonNull(topic, "topic");
        this.submittedAt = Objects.requireNonNull(submittedAt, "submittedAt");
        this.status = RegistrationStatus.PENDING;
    }

    public void withdraw(Instant withdrawnAt) {
        if (status != RegistrationStatus.PENDING) {
            throw new IllegalStateException("Only a pending registration can be withdrawn");
        }
        this.status = RegistrationStatus.WITHDRAWN;
        this.withdrawnAt = Objects.requireNonNull(withdrawnAt, "withdrawnAt");
    }

    public void accept(UserAccountEntity decisionActor, Instant decidedAt) {
        requirePending();
        this.status = RegistrationStatus.ACCEPTED;
        this.decisionActor = Objects.requireNonNull(decisionActor, "decisionActor");
        this.decidedAt = Objects.requireNonNull(decidedAt, "decidedAt");
    }

    public void rejectManually(
            UserAccountEntity decisionActor,
            String rejectionReason,
            Instant decidedAt) {
        String normalizedReason = rejectionReason == null ? null : rejectionReason.strip();
        if (normalizedReason == null || normalizedReason.isEmpty()) {
            throw new IllegalArgumentException("Manual rejection reason is required");
        }
        reject(
                decisionActor,
                RegistrationRejectionKind.MANUAL,
                normalizedReason,
                decidedAt);
    }

    public void rejectBecauseTopicTaken(
            UserAccountEntity decisionActor,
            Instant decidedAt) {
        reject(
                decisionActor,
                RegistrationRejectionKind.TOPIC_TAKEN,
                TOPIC_TAKEN_REASON,
                decidedAt);
    }

    private void reject(
            UserAccountEntity decisionActor,
            RegistrationRejectionKind rejectionKind,
            String rejectionReason,
            Instant decidedAt) {
        requirePending();
        this.status = RegistrationStatus.REJECTED;
        this.decisionActor = Objects.requireNonNull(decisionActor, "decisionActor");
        this.rejectionKind = Objects.requireNonNull(rejectionKind, "rejectionKind");
        this.rejectionReason = Objects.requireNonNull(rejectionReason, "rejectionReason");
        this.decidedAt = Objects.requireNonNull(decidedAt, "decidedAt");
    }

    private void requirePending() {
        if (status != RegistrationStatus.PENDING) {
            throw new IllegalStateException("Only a pending registration can be decided");
        }
    }

    public Long getId() {
        return id;
    }

    public RegistrationPeriodEntity getPeriod() {
        return period;
    }

    public StudentGroupEntity getGroup() {
        return group;
    }

    public TopicEntity getTopic() {
        return topic;
    }

    public RegistrationStatus getStatus() {
        return status;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }

    public Instant getWithdrawnAt() {
        return withdrawnAt;
    }

    public UserAccountEntity getDecisionActor() {
        return decisionActor;
    }

    public RegistrationRejectionKind getRejectionKind() {
        return rejectionKind;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public long getVersion() {
        return version;
    }
}
