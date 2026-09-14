package vn.edu.topicregistration.entity;


import java.io.Serializable;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;

@Entity
@Table(name = "group_memberships")
public class GroupMembershipEntity {
    @EmbeddedId
    private GroupMembershipId id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("groupId")
    @JoinColumn(name = "group_id", nullable = false)
    private StudentGroupEntity group;

    @Column(name = "period_id", nullable = false, updatable = false)
    private Long periodId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("studentId")
    @JoinColumn(name = "student_id", nullable = false)
    private UserAccountEntity student;

    @Column(nullable = false)
    private short slot;

    protected GroupMembershipEntity() {
    }

    public GroupMembershipEntity(
            StudentGroupEntity group,
            UserAccountEntity student,
            int slot) {
        this.group = Objects.requireNonNull(group, "group");
        this.student = Objects.requireNonNull(student, "student");
        if (group.getId() == null || group.getPeriod().getId() == null || student.getId() == null) {
            throw new IllegalArgumentException("Membership references must already exist");
        }
        if (slot < 1 || slot > 3) {
            throw new IllegalArgumentException("Membership slot must be between 1 and 3");
        }
        this.id = new GroupMembershipId(group.getId(), student.getId());
        this.periodId = group.getPeriod().getId();
        this.slot = (short) slot;
    }

    public GroupMembershipId getId() {
        return id;
    }

    public StudentGroupEntity getGroup() {
        return group;
    }

    public Long getPeriodId() {
        return periodId;
    }

    public UserAccountEntity getStudent() {
        return student;
    }

    public int getSlot() {
        return slot;
    }

    @Embeddable
    public static class GroupMembershipId implements Serializable {
        @Column(name = "group_id")
        private Long groupId;

        @Column(name = "student_id")
        private Long studentId;

        protected GroupMembershipId() {
        }

        public GroupMembershipId(Long groupId, Long studentId) {
            this.groupId = groupId;
            this.studentId = studentId;
        }

        public Long getGroupId() {
            return groupId;
        }

        public Long getStudentId() {
            return studentId;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof GroupMembershipId that)) {
                return false;
            }
            return Objects.equals(groupId, that.groupId)
                    && Objects.equals(studentId, that.studentId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(groupId, studentId);
        }
    }
}
