package vn.edu.topicregistration.topics.persistence;

import java.io.Serializable;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import vn.edu.topicregistration.identity.persistence.UserAccountEntity;

@Entity
@Table(name = "topic_supervisors")
public class TopicSupervisorEntity {
    @EmbeddedId
    private TopicSupervisorId id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("topicId")
    @JoinColumn(name = "topic_id", nullable = false)
    private TopicEntity topic;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("lecturerId")
    @JoinColumn(name = "lecturer_id", nullable = false)
    private UserAccountEntity lecturer;

    @Column(nullable = false)
    private short slot;

    protected TopicSupervisorEntity() {
    }

    TopicSupervisorEntity(TopicEntity topic, UserAccountEntity lecturer, int slot) {
        this.topic = Objects.requireNonNull(topic, "topic");
        this.lecturer = Objects.requireNonNull(lecturer, "lecturer");
        this.id = new TopicSupervisorId(topic.getId(), lecturer.getId());
        if (slot < 1 || slot > 2) {
            throw new IllegalArgumentException("Supervisor slot must be 1 or 2");
        }
        this.slot = (short) slot;
    }

    public TopicSupervisorId getId() {
        return id;
    }

    public TopicEntity getTopic() {
        return topic;
    }

    public UserAccountEntity getLecturer() {
        return lecturer;
    }

    public int getSlot() {
        return slot;
    }

    @jakarta.persistence.Embeddable
    public static class TopicSupervisorId implements Serializable {
        @Column(name = "topic_id")
        private Long topicId;

        @Column(name = "lecturer_id")
        private Long lecturerId;

        protected TopicSupervisorId() {
        }

        TopicSupervisorId(Long topicId, Long lecturerId) {
            this.topicId = topicId;
            this.lecturerId = lecturerId;
        }

        public Long getTopicId() {
            return topicId;
        }

        public Long getLecturerId() {
            return lecturerId;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof TopicSupervisorId that)) {
                return false;
            }
            return Objects.equals(topicId, that.topicId)
                    && Objects.equals(lecturerId, that.lecturerId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(topicId, lecturerId);
        }
    }
}
