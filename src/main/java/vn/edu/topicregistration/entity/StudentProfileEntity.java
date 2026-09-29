package vn.edu.topicregistration.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "student_profiles")
public class StudentProfileEntity {
    @Id
    @Column(name = "user_id")
    private Long userId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserAccountEntity user;

    @Column(name = "student_code", nullable = false, unique = true, length = 32)
    private String studentCode;

    @Column(name = "class_name", nullable = false, length = 64)
    private String className;

    @Column(name = "cohort_code", nullable = false, length = 16)
    private String cohortCode;

    protected StudentProfileEntity() {
    }

    public StudentProfileEntity(
            UserAccountEntity user,
            String studentCode,
            String className,
            String cohortCode) {
        this.user = user;
        this.studentCode = studentCode;
        this.className = className;
        this.cohortCode = cohortCode;
    }

    public Long getUserId() {
        return userId;
    }

    public UserAccountEntity getUser() {
        return user;
    }

    public String getStudentCode() {
        return studentCode;
    }

    public String getClassName() {
        return className;
    }

    public String getCohortCode() {
        return cohortCode;
    }
}
