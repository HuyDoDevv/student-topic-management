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

@Entity
@Table(name = "notification_events")
public class NotificationEventEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_key", nullable = false, unique = true, length = 191)
    private String eventKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private NotificationCategory category;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 64)
    private NotificationEventType eventType;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String body;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_id")
    private UserAccountEntity actor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "announcement_id")
    private AnnouncementEntity announcement;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 32)
    private NotificationTargetType targetType;

    @Column(name = "target_id")
    private Long targetId;

    @Column(name = "created_at", nullable = false, columnDefinition = "TIMESTAMP(6)")
    private Instant createdAt;

    protected NotificationEventEntity() {
    }

    public NotificationEventEntity(
            String eventKey,
            NotificationEventType eventType,
            String title,
            String body,
            UserAccountEntity actor,
            AnnouncementEntity announcement,
            NotificationTargetType targetType,
            Long targetId,
            Instant createdAt) {
        this.eventKey = Objects.requireNonNull(eventKey, "eventKey");
        this.eventType = Objects.requireNonNull(eventType, "eventType");
        this.category = eventType.category();
        this.title = Objects.requireNonNull(title, "title");
        this.body = Objects.requireNonNull(body, "body");
        this.actor = actor;
        this.announcement = announcement;
        this.targetType = Objects.requireNonNull(targetType, "targetType");
        this.targetId = targetId;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    }

    public Long getId() {
        return id;
    }

    public String getEventKey() {
        return eventKey;
    }

    public NotificationCategory getCategory() {
        return category;
    }

    public NotificationEventType getEventType() {
        return eventType;
    }

    public String getTitle() {
        return title;
    }

    public String getBody() {
        return body;
    }

    public UserAccountEntity getActor() {
        return actor;
    }

    public AnnouncementEntity getAnnouncement() {
        return announcement;
    }

    public NotificationTargetType getTargetType() {
        return targetType;
    }

    public Long getTargetId() {
        return targetId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
