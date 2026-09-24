package vn.edu.topicregistration.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import vn.edu.topicregistration.entity.UserNotificationEntity;

public interface UserNotificationRepository extends JpaRepository<UserNotificationEntity, Long> {
    boolean existsByEventIdAndRecipientId(long eventId, long recipientId);

    boolean existsByRecipientIdAndEventAnnouncementId(long recipientId, long announcementId);

    long countByRecipientIdAndReadAtIsNull(long recipientId);

    @EntityGraph(attributePaths = "event")
    Page<UserNotificationEntity> findByRecipientId(long recipientId, Pageable pageable);
}
