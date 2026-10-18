package vn.edu.topicregistration.entity;

import java.math.BigDecimal;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "score_components")
public class ScoreComponentEntity {
    public static final String COUNCIL_SCORE_NAME = "Điểm hội đồng";
    public static final BigDecimal MINIMUM_SCORE = new BigDecimal("0.00");
    public static final BigDecimal MAXIMUM_SCORE = new BigDecimal("10.00");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "period_id", nullable = false)
    private RegistrationPeriodEntity period;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(name = "minimum_score", precision = 12, scale = 4)
    private BigDecimal minimumScore;

    @Column(name = "maximum_score", precision = 12, scale = 4)
    private BigDecimal maximumScore;

    protected ScoreComponentEntity() {
    }

    public ScoreComponentEntity(RegistrationPeriodEntity period) {
        this.period = Objects.requireNonNull(period, "period");
        this.name = COUNCIL_SCORE_NAME;
        this.minimumScore = MINIMUM_SCORE;
        this.maximumScore = MAXIMUM_SCORE;
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

    public BigDecimal getMinimumScore() {
        return minimumScore;
    }

    public BigDecimal getMaximumScore() {
        return maximumScore;
    }
}
