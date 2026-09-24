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
@Table(name = "user_notifications")
public class UserNotificationEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    private NotificationEventEntity event;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recipient_id", nullable = false)
    private UserAccountEntity recipient;

    @Column(name = "delivered_at", nullable = false, columnDefinition = "TIMESTAMP(6)")
    private Instant deliveredAt;

    @Column(name = "read_at", columnDefinition = "TIMESTAMP(6)")
    private Instant readAt;

    protected UserNotificationEntity() {
    }

    public UserNotificationEntity(
            NotificationEventEntity event,
            UserAccountEntity recipient,
            Instant deliveredAt) {
        this.event = Objects.requireNonNull(event, "event");
        this.recipient = Objects.requireNonNull(recipient, "recipient");
        this.deliveredAt = Objects.requireNonNull(deliveredAt, "deliveredAt");
    }

    public Long getId() {
        return id;
    }

    public NotificationEventEntity getEvent() {
        return event;
    }

    public UserAccountEntity getRecipient() {
        return recipient;
    }

    public Instant getDeliveredAt() {
        return deliveredAt;
    }

    public Instant getReadAt() {
        return readAt;
    }

    public boolean isRead() {
        return readAt != null;
    }
}
