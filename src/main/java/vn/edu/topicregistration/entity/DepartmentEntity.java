package vn.edu.topicregistration.entity;

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
@Table(name = "departments")
public class DepartmentEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "faculty_id", nullable = false)
    private FacultyEntity faculty;

    @Column(nullable = false, length = 255)
    private String name;

    protected DepartmentEntity() {
    }

    public DepartmentEntity(FacultyEntity faculty, String name) {
        this.faculty = faculty;
        this.name = name;
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
}
