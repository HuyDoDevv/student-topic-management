package vn.edu.topicregistration.registrations.persistence;

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
import vn.edu.topicregistration.groups.persistence.StudentGroupEntity;
import vn.edu.topicregistration.identity.persistence.UserAccountEntity;
import vn.edu.topicregistration.periods.persistence.RegistrationPeriodEntity;
import vn.edu.topicregistration.topics.persistence.TopicEntity;

@Entity
@Table(name = "registrations")
public class RegistrationEntity {
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
