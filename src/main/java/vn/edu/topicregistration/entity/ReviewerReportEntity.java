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

@Entity
@Table(name = "reviewer_reports")
public class ReviewerReportEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assignment_id", nullable = false)
    private ReviewerAssignmentEntity assignment;

    @Column(name = "file_name", nullable = false, length = 500)
    private String fileName;

    @Column(name = "storage_key", nullable = false, length = 1000)
    private String storageKey;

    @Column(name = "content_type", nullable = false, length = 255)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "submitted_by", nullable = false)
    private UserAccountEntity submittedBy;

    @Column(name = "submitted_at", nullable = false, columnDefinition = "TIMESTAMP(6)")
    private Instant submittedAt;

    protected ReviewerReportEntity() {
    }

    public ReviewerReportEntity(
            ReviewerAssignmentEntity assignment,
            String fileName,
            String storageKey,
            String contentType,
            long sizeBytes,
            UserAccountEntity submittedBy,
            Instant submittedAt) {
        this.assignment = Objects.requireNonNull(assignment, "assignment");
        this.fileName = Objects.requireNonNull(fileName, "fileName");
        this.storageKey = Objects.requireNonNull(storageKey, "storageKey");
        this.contentType = Objects.requireNonNull(contentType, "contentType");
        this.sizeBytes = sizeBytes;
        this.submittedBy = Objects.requireNonNull(submittedBy, "submittedBy");
        this.submittedAt = Objects.requireNonNull(submittedAt, "submittedAt");
    }

    public Long getId() { return id; }
    public ReviewerAssignmentEntity getAssignment() { return assignment; }
    public String getFileName() { return fileName; }
    public String getStorageKey() { return storageKey; }
    public String getContentType() { return contentType; }
    public long getSizeBytes() { return sizeBytes; }
    public UserAccountEntity getSubmittedBy() { return submittedBy; }
    public Instant getSubmittedAt() { return submittedAt; }
}
