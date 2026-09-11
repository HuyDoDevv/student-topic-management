package vn.edu.topicregistration.groups.persistence;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import jakarta.persistence.Column;
import vn.edu.topicregistration.identity.persistence.UserAccountEntity;
import vn.edu.topicregistration.periods.persistence.RegistrationPeriodEntity;

@Entity
@Table(name = "student_groups")
public class StudentGroupEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "period_id", nullable = false)
    private RegistrationPeriodEntity period;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "leader_student_id", nullable = false)
    private UserAccountEntity leader;

    @OneToMany(mappedBy = "group", fetch = FetchType.LAZY)
    @OrderBy("slot ASC")
    private List<GroupMembershipEntity> members = new ArrayList<>();

    @Version
    @Column(nullable = false)
    private long version;

    protected StudentGroupEntity() {
    }

    public StudentGroupEntity(
            RegistrationPeriodEntity period,
            UserAccountEntity leader) {
        this.period = Objects.requireNonNull(period, "period");
        this.leader = Objects.requireNonNull(leader, "leader");
    }

    public void transferLeadership(UserAccountEntity newLeader) {
        this.leader = Objects.requireNonNull(newLeader, "newLeader");
    }

    public Long getId() {
        return id;
    }

    public RegistrationPeriodEntity getPeriod() {
        return period;
    }

    public UserAccountEntity getLeader() {
        return leader;
    }

    public List<GroupMembershipEntity> getMembers() {
        return Collections.unmodifiableList(members);
    }

    public long getVersion() {
        return version;
    }
}
