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
@Table(name = "council_members")
public class CouncilMemberEntity {
    @EmbeddedId
    private CouncilMemberId id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("councilId")
    @JoinColumn(name = "council_id", nullable = false)
    private CouncilEntity council;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("lecturerId")
    @JoinColumn(name = "lecturer_id", nullable = false)
    private UserAccountEntity lecturer;

    @Column(nullable = false)
    private short slot;

    protected CouncilMemberEntity() {
    }

    public CouncilMemberEntity(CouncilEntity council, UserAccountEntity lecturer, int slot) {
        this.council = Objects.requireNonNull(council, "council");
        this.lecturer = Objects.requireNonNull(lecturer, "lecturer");
        if (council.getId() == null || lecturer.getId() == null) {
            throw new IllegalArgumentException("Council member references must already exist");
        }
        if (slot < 1 || slot > 5) {
            throw new IllegalArgumentException("Council member slot must be between 1 and 5");
        }
        this.id = new CouncilMemberId(council.getId(), lecturer.getId());
        this.slot = (short) slot;
    }

    public CouncilMemberId getId() {
        return id;
    }

    public CouncilEntity getCouncil() {
        return council;
    }

    public UserAccountEntity getLecturer() {
        return lecturer;
    }

    public int getSlot() {
        return slot;
    }

    @Embeddable
    public static class CouncilMemberId implements Serializable {
        @Column(name = "council_id")
        private Long councilId;

        @Column(name = "lecturer_id")
        private Long lecturerId;

        protected CouncilMemberId() {
        }

        public CouncilMemberId(Long councilId, Long lecturerId) {
            this.councilId = councilId;
            this.lecturerId = lecturerId;
        }

        public Long getCouncilId() {
            return councilId;
        }

        public Long getLecturerId() {
            return lecturerId;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof CouncilMemberId that)) {
                return false;
            }
            return Objects.equals(councilId, that.councilId)
                    && Objects.equals(lecturerId, that.lecturerId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(councilId, lecturerId);
        }
    }
}
