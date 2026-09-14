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

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "department_id", nullable = false)
    private DepartmentEntity department;

    @Column(nullable = false, length = 255)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "chair_id", nullable = false)
    private UserAccountEntity chair;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "secretary_id", nullable = false)
    private UserAccountEntity secretary;

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
            DepartmentEntity department,
            String name,
            UserAccountEntity chair,
            UserAccountEntity secretary,
            Instant createdAt) {
        this.period = Objects.requireNonNull(period, "period");
        this.department = Objects.requireNonNull(department, "department");
        this.name = Objects.requireNonNull(name, "name");
        this.chair = Objects.requireNonNull(chair, "chair");
        this.secretary = Objects.requireNonNull(secretary, "secretary");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.status = CouncilStatus.DRAFT;
    }

    public Long getId() {
        return id;
    }

    public RegistrationPeriodEntity getPeriod() {
        return period;
    }

    public DepartmentEntity getDepartment() {
        return department;
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
