package vn.edu.topicregistration.groups.persistence;

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
import vn.edu.topicregistration.identity.persistence.UserAccountEntity;
import vn.edu.topicregistration.periods.persistence.RegistrationPeriodEntity;

@Entity
@Table(name = "group_invitations")
public class GroupInvitationEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id", nullable = false)
    private StudentGroupEntity group;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "period_id", nullable = false)
    private RegistrationPeriodEntity period;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private UserAccountEntity student;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invited_by", nullable = false)
    private UserAccountEntity invitedBy;

    @Column(nullable = false)
    private short slot;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private GroupInvitationStatus status;

    @Column(name = "created_at", nullable = false, columnDefinition = "TIMESTAMP(6)")
    private Instant createdAt;

    @Column(name = "responded_at", columnDefinition = "TIMESTAMP(6)")
    private Instant respondedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected GroupInvitationEntity() {
    }

    public GroupInvitationEntity(
            StudentGroupEntity group,
            UserAccountEntity student,
            UserAccountEntity invitedBy,
            int slot,
            Instant createdAt) {
        this.group = Objects.requireNonNull(group, "group");
        this.period = Objects.requireNonNull(group.getPeriod(), "period");
        this.student = Objects.requireNonNull(student, "student");
        this.invitedBy = Objects.requireNonNull(invitedBy, "invitedBy");
        if (slot < 1 || slot > 3) {
            throw new IllegalArgumentException("Invitation slot must be between 1 and 3");
        }
        this.slot = (short) slot;
        this.status = GroupInvitationStatus.PENDING;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    }

    public Long getId() {
        return id;
    }

    public StudentGroupEntity getGroup() {
        return group;
    }

    public RegistrationPeriodEntity getPeriod() {
        return period;
    }

    public UserAccountEntity getStudent() {
        return student;
    }

    public UserAccountEntity getInvitedBy() {
        return invitedBy;
    }

    public int getSlot() {
        return slot;
    }

    public GroupInvitationStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getRespondedAt() {
        return respondedAt;
    }

    public long getVersion() {
        return version;
    }
}
