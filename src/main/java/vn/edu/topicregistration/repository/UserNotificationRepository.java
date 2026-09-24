package vn.edu.topicregistration.repository;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import vn.edu.topicregistration.entity.NotificationCategory;
import vn.edu.topicregistration.entity.UserNotificationEntity;

public interface UserNotificationRepository extends JpaRepository<UserNotificationEntity, Long> {
    boolean existsByEventIdAndRecipientId(long eventId, long recipientId);

    boolean existsByRecipientIdAndEventAnnouncementId(long recipientId, long announcementId);

    long countByRecipientIdAndReadAtIsNull(long recipientId);

    @EntityGraph(attributePaths = "event")
    Page<UserNotificationEntity> findByRecipientId(long recipientId, Pageable pageable);

    @EntityGraph(attributePaths = "event")
    Optional<UserNotificationEntity> findByIdAndRecipientId(long id, long recipientId);

    @EntityGraph(attributePaths = "event")
    @Query("""
            select delivery from UserNotificationEntity delivery
            where delivery.recipient.id = :recipientId
            and (:unreadOnly = false or delivery.readAt is null)
            and (:category is null or delivery.event.category = :category)
            """)
    Page<UserNotificationEntity> findInbox(
            @Param("recipientId") long recipientId,
            @Param("unreadOnly") boolean unreadOnly,
            @Param("category") NotificationCategory category,
            Pageable pageable);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update UserNotificationEntity delivery
            set delivery.readAt = :readAt
            where delivery.id = :id
            and delivery.recipient.id = :recipientId
            and delivery.readAt is null
            """)
    int markRead(
            @Param("id") long id,
            @Param("recipientId") long recipientId,
            @Param("readAt") Instant readAt);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update UserNotificationEntity delivery
            set delivery.readAt = :readAt
            where delivery.recipient.id = :recipientId
            and delivery.readAt is null
            and delivery.deliveredAt <= :cutoff
            """)
    int markAllReadThrough(
            @Param("recipientId") long recipientId,
            @Param("cutoff") Instant cutoff,
            @Param("readAt") Instant readAt);
}
