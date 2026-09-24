package vn.edu.topicregistration.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import vn.edu.topicregistration.entity.AnnouncementEntity;

public interface AnnouncementRepository extends JpaRepository<AnnouncementEntity, Long> {
    @Override
    @EntityGraph(attributePaths = {"faculty", "createdBy", "publishedBy"})
    Optional<AnnouncementEntity> findById(Long id);
}
