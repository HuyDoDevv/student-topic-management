package vn.edu.topicregistration.entity;


import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
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

@Entity
@Table(name = "topics")
public class TopicEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "period_id", nullable = false)
    private RegistrationPeriodEntity period;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id")
    private DepartmentEntity department;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id", nullable = false)
    private UserAccountEntity author;

    @Column(length = 500)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(columnDefinition = "TEXT")
    private String objectives;

    @Column(name = "student_requirements", columnDefinition = "TEXT")
    private String studentRequirements;

    @Column(name = "expected_results", columnDefinition = "TEXT")
    private String expectedResults;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(name = "hidden_at", columnDefinition = "TIMESTAMP(6)")
    private Instant hiddenAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "hidden_by")
    private UserAccountEntity hiddenBy;

    @Column(name = "hidden_reason", length = 1000)
    private String hiddenReason;

    @OneToMany(mappedBy = "topic", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("slot ASC")
    private List<TopicSupervisorEntity> supervisors = new ArrayList<>();

    @Version
    @Column(nullable = false)
    private long version;

    protected TopicEntity() {
    }

    public TopicEntity(
            RegistrationPeriodEntity period,
            UserAccountEntity author) {
        this(period, author, null);
    }

    public TopicEntity(
            RegistrationPeriodEntity period,
            UserAccountEntity author,
            DepartmentEntity department) {
        this.period = period;
        this.author = author;
        this.department = department;
        this.status = "DRAFT";
    }

    public void updateDraft(
            String title,
            String description,
            String objectives,
            String studentRequirements,
            String expectedResults) {
        this.title = title;
        this.description = description;
        this.objectives = objectives;
        this.studentRequirements = studentRequirements;
        this.expectedResults = expectedResults;
    }

    public void replaceSupervisors(List<UserAccountEntity> lecturers) {
        supervisors.removeIf(supervisor -> lecturers.stream()
                .noneMatch(lecturer -> lecturer.getId().equals(
                        supervisor.getLecturer().getId())));
        for (int index = 0; index < lecturers.size(); index++) {
            UserAccountEntity lecturer = lecturers.get(index);
            if (supervisors.stream().noneMatch(supervisor -> supervisor.getLecturer().getId()
                    .equals(lecturer.getId()))) {
                supervisors.add(new TopicSupervisorEntity(this, lecturer, index + 1));
            }
        }
    }

    public void changeStatus(String status) {
        this.status = status;
    }

    public void hide(UserAccountEntity moderator, String reason, Instant hiddenAt) {
        this.hiddenBy = moderator;
        this.hiddenReason = reason;
        this.hiddenAt = hiddenAt;
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

    public UserAccountEntity getAuthor() {
        return author;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public String getObjectives() {
        return objectives;
    }

    public String getStudentRequirements() {
        return studentRequirements;
    }

    public String getExpectedResults() {
        return expectedResults;
    }

    public String getStatus() {
        return status;
    }

    public Instant getHiddenAt() {
        return hiddenAt;
    }

    public UserAccountEntity getHiddenBy() {
        return hiddenBy;
    }

    public String getHiddenReason() {
        return hiddenReason;
    }

    public List<TopicSupervisorEntity> getSupervisors() {
        return Collections.unmodifiableList(supervisors);
    }

    public long getVersion() {
        return version;
    }
}
