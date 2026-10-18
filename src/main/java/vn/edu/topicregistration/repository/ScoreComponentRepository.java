package vn.edu.topicregistration.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import vn.edu.topicregistration.entity.ScoreComponentEntity;

public interface ScoreComponentRepository extends JpaRepository<ScoreComponentEntity, Long> {
    Optional<ScoreComponentEntity> findByPeriodIdAndName(long periodId, String name);
}
