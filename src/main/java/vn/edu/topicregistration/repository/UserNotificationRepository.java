package vn.edu.topicregistration.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import vn.edu.topicregistration.entity.UserNotificationEntity;

public interface UserNotificationRepository extends JpaRepository<UserNotificationEntity, Long> {
    boolean existsByEventIdAndRecipientId(long eventId, long recipientId);
}
