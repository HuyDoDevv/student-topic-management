package vn.edu.topicregistration.entity;

import java.io.Serializable;
import java.time.Instant;
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
@Table(name = "council_topics")
public class CouncilTopicEntity {
    @EmbeddedId
    private CouncilTopicId id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("councilId")
    @JoinColumn(name = "council_id", nullable = false)
    private CouncilEntity council;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("registrationId")
    @JoinColumn(name = "registration_id", nullable = false)
    private RegistrationEntity registration;

    @Column(name = "period_id", nullable = false, updatable = false)
    private Long periodId;

    @Column(name = "scheduled_at", columnDefinition = "TIMESTAMP(6)")
    private Instant scheduledAt;

    @Column(length = 500)
    private String location;

    protected CouncilTopicEntity() {
    }

    public CouncilTopicEntity(CouncilEntity council, RegistrationEntity registration) {
        this.council = Objects.requireNonNull(council, "council");
        this.registration = Objects.requireNonNull(registration, "registration");
        if (council.getId() == null
                || registration.getId() == null
                || registration.getPeriod().getId() == null) {
            throw new IllegalArgumentException("Council assignment references must already exist");
        }
        this.id = new CouncilTopicId(council.getId(), registration.getId());
        this.periodId = registration.getPeriod().getId();
    }

    public CouncilTopicId getId() {
        return id;
    }

    public CouncilEntity getCouncil() {
        return council;
    }

    public RegistrationEntity getRegistration() {
        return registration;
    }

    public Long getPeriodId() {
        return periodId;
    }

    public Instant getScheduledAt() {
        return scheduledAt;
    }

    public String getLocation() {
        return location;
    }

    @Embeddable
    public static class CouncilTopicId implements Serializable {
        @Column(name = "council_id")
        private Long councilId;

        @Column(name = "registration_id")
        private Long registrationId;

        protected CouncilTopicId() {
        }

        public CouncilTopicId(Long councilId, Long registrationId) {
            this.councilId = councilId;
            this.registrationId = registrationId;
        }

        public Long getCouncilId() {
            return councilId;
        }

        public Long getRegistrationId() {
            return registrationId;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof CouncilTopicId that)) {
                return false;
            }
            return Objects.equals(councilId, that.councilId)
                    && Objects.equals(registrationId, that.registrationId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(councilId, registrationId);
        }
    }
}
