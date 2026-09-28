package vn.edu.topicregistration.service;

import vn.edu.topicregistration.entity.RoleCode;
import vn.edu.topicregistration.entity.UserAccountEntity;
import vn.edu.topicregistration.repository.RegistrationPeriodRepository;
import vn.edu.topicregistration.exception.ApplicationErrorCode;
import vn.edu.topicregistration.exception.ApplicationException;
import vn.edu.topicregistration.security.AuthorizationPolicy;
import vn.edu.topicregistration.entity.TopicEntity;
import vn.edu.topicregistration.repository.TopicRepository;
import vn.edu.topicregistration.entity.TopicSupervisorEntity;

import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.edu.topicregistration.security.AuthorizationPolicy.ActorIdentity;
import vn.edu.topicregistration.service.TopicRules.TopicStatus;

@Service
@Profile("!test")
public class PublishedTopicQueryService {
    private final RegistrationPeriodRepository periods;
    private final TopicRepository topics;
    private final AuthorizationPolicy authorization;

    public PublishedTopicQueryService(
            RegistrationPeriodRepository periods,
            TopicRepository topics,
            AuthorizationPolicy authorization) {
        this.periods = periods;
        this.topics = topics;
        this.authorization = authorization;
    }

    @Transactional(readOnly = true)
    public List<PublishedTopicView> listForCurrentStudent(long periodId) {
        ActorIdentity student = authorization.requireRole(RoleCode.STUDENT);
        if (student.facultyId() == null) {
            throw new AccessDeniedException("Student faculty scope is missing");
        }
        periods.findByIdAndFacultyId(periodId, student.facultyId())
                .orElseThrow(() -> new ApplicationException(
                        ApplicationErrorCode.RESOURCE_NOT_FOUND));

        return topics.findAllVisibleByPeriodIdAndStatusIn(
                        periodId, List.of(TopicStatus.PUBLISHED.name()))
                .stream()
                .map(PublishedTopicQueryService::toView)
                .toList();
    }

    private static PublishedTopicView toView(TopicEntity topic) {
        return new PublishedTopicView(
                topic.getId(),
                topic.getPeriod().getId(),
                topic.getDepartment().getName(),
                displayName(topic.getAuthor()),
                topic.getTitle(),
                topic.getDescription(),
                topic.getObjectives(),
                topic.getStudentRequirements(),
                topic.getExpectedResults(),
                topic.getSupervisors().stream()
                        .map(TopicSupervisorEntity::getLecturer)
                        .map(PublishedTopicQueryService::displayName)
                        .toList());
    }

    private static String displayName(UserAccountEntity account) {
        return account.getFullName() == null || account.getFullName().isBlank()
                ? account.getUsername()
                : account.getFullName();
    }

    public record PublishedTopicView(
            long id,
            long periodId,
            String department,
            String author,
            String title,
            String description,
            String objectives,
            String studentRequirements,
            String expectedResults,
            List<String> supervisors) {
        public PublishedTopicView {
            supervisors = List.copyOf(supervisors);
        }
    }
}
