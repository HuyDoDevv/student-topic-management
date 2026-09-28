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
@Table(name = "councils")
public class CouncilEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "period_id", nullable = false)
    private RegistrationPeriodEntity period;

    @Column(nullable = false, length = 255)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "chair_id", nullable = false)
    private UserAccountEntity chair;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "secretary_id", nullable = false)
    private UserAccountEntity secretary;

    @Enumerated(EnumType.STRING)
    @Column(name = "grading_mode", nullable = false, length = 16)
    private CouncilMode mode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private CouncilStatus status;

    @Column(name = "created_at", nullable = false, columnDefinition = "TIMESTAMP(6)")
    private Instant createdAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected CouncilEntity() {
    }

    public CouncilEntity(
            RegistrationPeriodEntity period,
            String name,
            UserAccountEntity chair,
            UserAccountEntity secretary,
            Instant createdAt) {
        this(period, name, chair, secretary, CouncilMode.COUNCIL, createdAt);
    }

    private CouncilEntity(
            RegistrationPeriodEntity period,
            String name,
            UserAccountEntity chair,
            UserAccountEntity secretary,
            CouncilMode mode,
            Instant createdAt) {
        this.period = Objects.requireNonNull(period, "period");
        this.name = Objects.requireNonNull(name, "name");
        this.chair = Objects.requireNonNull(chair, "chair");
        this.secretary = Objects.requireNonNull(secretary, "secretary");
        this.mode = Objects.requireNonNull(mode, "mode");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.status = CouncilStatus.DRAFT;
    }

    public static CouncilEntity directGrading(
            RegistrationPeriodEntity period,
            String name,
            UserAccountEntity grader,
            Instant createdAt) {
        return new CouncilEntity(
                period,
                name,
                grader,
                grader,
                CouncilMode.DIRECT,
                createdAt);
    }

    public void transitionTo(CouncilStatus target) {
        CouncilStatus required = switch (status) {
            case DRAFT -> CouncilStatus.ASSIGNED;
            case ASSIGNED -> CouncilStatus.SCHEDULED;
            case SCHEDULED -> CouncilStatus.ACTIVE;
            case ACTIVE -> CouncilStatus.COMPLETED;
            case COMPLETED -> CouncilStatus.PUBLISHED;
            case PUBLISHED -> null;
        };
        if (target == null || target != required) {
            throw new IllegalStateException("Council transition is not allowed");
        }
        this.status = target;
    }

    public Long getId() {
        return id;
    }

    public RegistrationPeriodEntity getPeriod() {
        return period;
    }

    public String getName() {
        return name;
    }

    public UserAccountEntity getChair() {
        return chair;
    }

    public UserAccountEntity getSecretary() {
        return secretary;
    }

    public CouncilMode getMode() {
        return mode;
    }

    public CouncilStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public long getVersion() {
        return version;
    }
}
