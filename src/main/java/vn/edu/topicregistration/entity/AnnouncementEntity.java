package vn.edu.topicregistration.entity;

import java.time.Instant;
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
import jakarta.persistence.Version;

@Entity
@Table(name = "announcements")
public class AnnouncementEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 500)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String body;

    @Column(name = "source_name", nullable = false, length = 255)
    private String sourceName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "faculty_id")
    private FacultyEntity faculty;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private UserAccountEntity createdBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "published_by")
    private UserAccountEntity publishedBy;

    @Column(name = "published_at", columnDefinition = "TIMESTAMP(6)")
    private Instant publishedAt;

    @Column(name = "created_at", nullable = false, columnDefinition = "TIMESTAMP(6)")
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, columnDefinition = "TIMESTAMP(6)")
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected AnnouncementEntity() {
    }

    public AnnouncementEntity(
            String title,
            String body,
            String sourceName,
            FacultyEntity faculty,
            UserAccountEntity createdBy,
            Instant createdAt) {
        this.title = Objects.requireNonNull(title, "title");
        this.body = Objects.requireNonNull(body, "body");
        this.sourceName = Objects.requireNonNull(sourceName, "sourceName");
        this.faculty = faculty;
        this.createdBy = Objects.requireNonNull(createdBy, "createdBy");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getBody() {
        return body;
    }

    public String getSourceName() {
        return sourceName;
    }

    public FacultyEntity getFaculty() {
        return faculty;
    }

    public UserAccountEntity getCreatedBy() {
        return createdBy;
    }

    public UserAccountEntity getPublishedBy() {
        return publishedBy;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public long getVersion() {
        return version;
    }

    public boolean isPublished() {
        return publishedAt != null;
    }

    public void revise(String title, String body, Instant updatedAt) {
        if (isPublished()) {
            throw new IllegalStateException("Published announcements are immutable");
        }
        this.title = Objects.requireNonNull(title, "title");
        this.body = Objects.requireNonNull(body, "body");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
    }

    public void publish(UserAccountEntity publisher, Instant publishedAt) {
        if (isPublished()) {
            throw new IllegalStateException("Announcement is already published");
        }
        this.publishedBy = Objects.requireNonNull(publisher, "publisher");
        this.publishedAt = Objects.requireNonNull(publishedAt, "publishedAt");
        this.updatedAt = publishedAt;
    }
}
