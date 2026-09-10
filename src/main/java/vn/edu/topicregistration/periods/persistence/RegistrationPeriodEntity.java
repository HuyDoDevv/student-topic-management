package vn.edu.topicregistration.periods.persistence;

import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import vn.edu.topicregistration.identity.persistence.FacultyEntity;

@Entity
@Table(name = "registration_periods")
public class RegistrationPeriodEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "faculty_id", nullable = false)
    private FacultyEntity faculty;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(name = "topic_type", nullable = false, length = 16)
    private String topicType;

    @Column(name = "lecturer_start", nullable = false, columnDefinition = "TIMESTAMP(6)")
    private Instant lecturerStart;

    @Column(name = "lecturer_end", nullable = false, columnDefinition = "TIMESTAMP(6)")
    private Instant lecturerEnd;

    @Column(name = "student_start", nullable = false, columnDefinition = "TIMESTAMP(6)")
    private Instant studentStart;

    @Column(name = "student_end", nullable = false, columnDefinition = "TIMESTAMP(6)")
    private Instant studentEnd;

    @Column(name = "reviewer_score_deadline")
    private LocalDate reviewerScoreDeadline;

    @Column(name = "council_report_date")
    private LocalDate councilReportDate;

    @Version
    @Column(nullable = false)
    private long version;

    protected RegistrationPeriodEntity() {
    }

    public RegistrationPeriodEntity(
            FacultyEntity faculty,
            String name,
            String topicType,
            Instant lecturerStart,
            Instant lecturerEnd,
            Instant studentStart,
            Instant studentEnd,
            LocalDate reviewerScoreDeadline,
            LocalDate councilReportDate) {
        this.faculty = faculty;
        this.name = name;
        this.topicType = topicType;
        this.lecturerStart = lecturerStart;
        this.lecturerEnd = lecturerEnd;
        this.studentStart = studentStart;
        this.studentEnd = studentEnd;
        this.reviewerScoreDeadline = reviewerScoreDeadline;
        this.councilReportDate = councilReportDate;
    }

    public void update(
            String name,
            String topicType,
            Instant lecturerStart,
            Instant lecturerEnd,
            Instant studentStart,
            Instant studentEnd,
            LocalDate reviewerScoreDeadline,
            LocalDate councilReportDate) {
        this.name = name;
        this.topicType = topicType;
        this.lecturerStart = lecturerStart;
        this.lecturerEnd = lecturerEnd;
        this.studentStart = studentStart;
        this.studentEnd = studentEnd;
        this.reviewerScoreDeadline = reviewerScoreDeadline;
        this.councilReportDate = councilReportDate;
    }

    public Long getId() {
        return id;
    }

    public FacultyEntity getFaculty() {
        return faculty;
    }

    public String getName() {
        return name;
    }

    public String getTopicType() {
        return topicType;
    }

    public Instant getLecturerStart() {
        return lecturerStart;
    }

    public Instant getLecturerEnd() {
        return lecturerEnd;
    }

    public Instant getStudentStart() {
        return studentStart;
    }

    public Instant getStudentEnd() {
        return studentEnd;
    }

    public LocalDate getReviewerScoreDeadline() {
        return reviewerScoreDeadline;
    }

    public LocalDate getCouncilReportDate() {
        return councilReportDate;
    }

    public long getVersion() {
        return version;
    }
}
