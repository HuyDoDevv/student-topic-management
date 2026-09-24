package vn.edu.topicregistration.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import vn.edu.topicregistration.entity.NotificationEventEntity;

public interface NotificationEventRepository extends JpaRepository<NotificationEventEntity, Long> {
    Optional<NotificationEventEntity> findByEventKey(String eventKey);
}
