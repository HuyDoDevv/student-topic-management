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
@Table(name = "project_reports")
public class ProjectReportEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "registration_id", nullable = false)
    private RegistrationEntity registration;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "submitted_by", nullable = false)
    private UserAccountEntity submittedBy;

    @Column(name = "submitted_at", nullable = false, columnDefinition = "TIMESTAMP(6)")
    private Instant submittedAt;

    @Column(name = "file_name", nullable = false, length = 500)
    private String fileName;

    @Column(name = "storage_key", nullable = false, length = 1000)
    private String storageKey;

    @Column(name = "content_type", nullable = false, length = 255)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "replaced_at", columnDefinition = "TIMESTAMP(6)")
    private Instant replacedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected ProjectReportEntity() {
    }

    public ProjectReportEntity(
            RegistrationEntity registration,
            UserAccountEntity submittedBy,
            Instant submittedAt,
            String fileName,
            String storageKey,
            String contentType,
            long sizeBytes) {
        this.registration = Objects.requireNonNull(registration, "registration");
        this.submittedBy = Objects.requireNonNull(submittedBy, "submittedBy");
        this.submittedAt = Objects.requireNonNull(submittedAt, "submittedAt");
        this.fileName = Objects.requireNonNull(fileName, "fileName");
        this.storageKey = Objects.requireNonNull(storageKey, "storageKey");
        this.contentType = Objects.requireNonNull(contentType, "contentType");
        this.sizeBytes = sizeBytes;
    }

    public void markReplaced(Instant replacedAt) {
        if (this.replacedAt != null) {
            throw new IllegalStateException("Report has already been replaced");
        }
        this.replacedAt = Objects.requireNonNull(replacedAt, "replacedAt");
    }

    public Long getId() {
        return id;
    }

    public RegistrationEntity getRegistration() {
        return registration;
    }

    public UserAccountEntity getSubmittedBy() {
        return submittedBy;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public String getFileName() {
        return fileName;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public String getContentType() {
        return contentType;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public Instant getReplacedAt() {
        return replacedAt;
    }

    public long getVersion() {
        return version;
    }
}
