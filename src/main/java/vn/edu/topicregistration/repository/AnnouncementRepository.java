package vn.edu.topicregistration.repository;

import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import vn.edu.topicregistration.entity.AnnouncementEntity;

public interface AnnouncementRepository extends JpaRepository<AnnouncementEntity, Long> {
    @Override
    @EntityGraph(attributePaths = {"faculty", "createdBy", "publishedBy"})
    Optional<AnnouncementEntity> findById(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"faculty", "createdBy", "publishedBy"})
    @Query("select announcement from AnnouncementEntity announcement where announcement.id = :id")
    Optional<AnnouncementEntity> findByIdForUpdate(@Param("id") long id);

    Page<AnnouncementEntity> findByFacultyIsNull(Pageable pageable);

    Page<AnnouncementEntity> findByFacultyIsNullAndPublishedAtIsNull(Pageable pageable);

    Page<AnnouncementEntity> findByFacultyIsNullAndPublishedAtIsNotNull(Pageable pageable);

    Page<AnnouncementEntity> findByFacultyId(long facultyId, Pageable pageable);

    Page<AnnouncementEntity> findByFacultyIdAndPublishedAtIsNull(long facultyId, Pageable pageable);

    Page<AnnouncementEntity> findByFacultyIdAndPublishedAtIsNotNull(long facultyId, Pageable pageable);
}
