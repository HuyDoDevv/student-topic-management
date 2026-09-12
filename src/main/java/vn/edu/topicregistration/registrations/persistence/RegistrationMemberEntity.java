package vn.edu.topicregistration.registrations.persistence;

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
import vn.edu.topicregistration.identity.persistence.UserAccountEntity;

@Entity
@Table(name = "registration_members")
public class RegistrationMemberEntity {
    @EmbeddedId
    private RegistrationMemberId id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("registrationId")
    @JoinColumn(name = "registration_id", nullable = false, updatable = false)
    private RegistrationEntity registration;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("studentId")
    @JoinColumn(name = "student_id", nullable = false, updatable = false)
    private UserAccountEntity student;

    @Column(nullable = false, updatable = false)
    private short slot;

    @Column(name = "leader_at_submission", nullable = false, updatable = false)
    private boolean leaderAtSubmission;

    protected RegistrationMemberEntity() {
    }

    public RegistrationMemberId getId() {
        return id;
    }

    public RegistrationEntity getRegistration() {
        return registration;
    }

    public UserAccountEntity getStudent() {
        return student;
    }

    public int getSlot() {
        return slot;
    }

    public boolean isLeaderAtSubmission() {
        return leaderAtSubmission;
    }

    @Embeddable
    public static class RegistrationMemberId implements Serializable {
        @Column(name = "registration_id")
        private Long registrationId;

        @Column(name = "student_id")
        private Long studentId;

        protected RegistrationMemberId() {
        }

        public RegistrationMemberId(Long registrationId, Long studentId) {
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
            if (!(other instanceof RegistrationMemberId that)) {
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
