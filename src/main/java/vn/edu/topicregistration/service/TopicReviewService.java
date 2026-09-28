package vn.edu.topicregistration.service;

import vn.edu.topicregistration.entity.RoleCode;
import vn.edu.topicregistration.entity.UserAccountEntity;
import vn.edu.topicregistration.entity.RegistrationPeriodEntity;
import vn.edu.topicregistration.repository.RegistrationPeriodRepository;
import vn.edu.topicregistration.exception.ApplicationErrorCode;
import vn.edu.topicregistration.exception.ApplicationException;
import vn.edu.topicregistration.security.AuthorizationPolicy;
import vn.edu.topicregistration.entity.TopicEntity;
import vn.edu.topicregistration.repository.TopicRepository;
import vn.edu.topicregistration.entity.TopicSupervisorEntity;

import java.util.List;
import java.util.Objects;

import org.springframework.context.annotation.Profile;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import vn.edu.topicregistration.service.TopicReviewRules.InvalidTopicReviewStateException;
import vn.edu.topicregistration.service.TopicReviewRules.ReviewDecision;
import vn.edu.topicregistration.service.TopicRules.TopicStatus;
import vn.edu.topicregistration.entity.NotificationEventType;
import vn.edu.topicregistration.entity.NotificationTargetType;
import vn.edu.topicregistration.service.NotificationService.EmissionCommand;

@Service
@Profile("!test")
public class TopicReviewService {
    private final RegistrationPeriodRepository periods;
    private final TopicRepository topics;
    private final AuthorizationPolicy authorization;
    private final NotificationService notifications;

    public TopicReviewService(
            RegistrationPeriodRepository periods,
            TopicRepository topics,
            AuthorizationPolicy authorization,
            NotificationService notifications) {
        this.periods = periods;
        this.topics = topics;
        this.authorization = authorization;
        this.notifications = notifications;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ReviewResult approve(long topicId, long expectedVersion) {
        return review(topicId, expectedVersion, ReviewDecision.APPROVE);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ReviewResult reject(long topicId, long expectedVersion) {
        return review(topicId, expectedVersion, ReviewDecision.REJECT);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public PublicationResult publishApproved(long periodId) {
        RegistrationPeriodEntity period = lockedPeriod(periodId);
        authorization.requireDeanOfFaculty(period.getFaculty().getId());

        List<TopicEntity> approved = topics.findAllApprovedByPeriodIdForUpdate(periodId);
        for (TopicEntity topic : approved) {
            TopicStatus nextStatus = applyRule(() -> TopicReviewRules.publish(statusOf(topic)));
            topic.changeStatus(nextStatus.name());
        }
        if (!approved.isEmpty()) {
            topics.flush();
        }
        return new PublicationResult(period.getId(), approved.size());
    }

    @Transactional(readOnly = true)
    public ReviewWorkspace reviewWorkspace(long periodId) {
        var actor = authorization.requireRole(RoleCode.DEAN);
        if (actor.facultyId() == null) {
            throw new AccessDeniedException("Dean faculty scope is missing");
        }
        RegistrationPeriodEntity period = periods
                .findByIdAndFacultyId(periodId, actor.facultyId())
                .orElseThrow(() -> new ApplicationException(
                        ApplicationErrorCode.RESOURCE_NOT_FOUND));
        authorization.requireDeanOfFaculty(period.getFaculty().getId());

        List<ReviewTopicView> visibleTopics = topics
                .findAllVisibleByPeriodIdAndStatusIn(
                        periodId,
                        List.of(
                                TopicStatus.PENDING_APPROVAL.name(),
                                TopicStatus.APPROVED.name(),
                                TopicStatus.PUBLISHED.name()))
                .stream()
                .map(this::toReviewView)
                .toList();
        return new ReviewWorkspace(period.getId(), visibleTopics);
    }

    private ReviewResult review(
            long topicId,
            long expectedVersion,
            ReviewDecision decision) {
        LockedTopic locked = lockedTopic(topicId, expectedVersion);
        TopicStatus nextStatus = applyRule(
                () -> TopicReviewRules.review(statusOf(locked.topic()), decision));
        locked.topic().changeStatus(nextStatus.name());
        topics.flush();
        NotificationEventType eventType = decision == ReviewDecision.APPROVE
                ? NotificationEventType.TOPIC_PROPOSAL_APPROVED
                : NotificationEventType.TOPIC_PROPOSAL_REJECTED;
        notifications.emit(new EmissionCommand(
                eventType,
                locked.topic().getId(),
                locked.actor().accountId(),
                List.of(locked.topic().getAuthor().getId()),
                decision == ReviewDecision.APPROVE
                        ? "Đề tài đã được duyệt"
                        : "Đề tài chưa được duyệt",
                decision == ReviewDecision.APPROVE
                        ? "Đề tài của bạn đã được Trưởng khoa chấp nhận."
                        : "Đề tài của bạn đã bị từ chối. Vui lòng xem lại thông tin đề tài.",
                null,
                NotificationTargetType.TOPIC,
                locked.topic().getId()));
        return new ReviewResult(
                locked.topic().getId(),
                locked.period().getId(),
                nextStatus,
                locked.topic().getVersion());
    }

    private LockedTopic lockedTopic(long topicId, long expectedVersion) {
        long periodId = topics.findPeriodIdByTopicId(topicId)
                .orElseThrow(() -> new ApplicationException(
                        ApplicationErrorCode.RESOURCE_NOT_FOUND));
        RegistrationPeriodEntity period = lockedPeriod(periodId);
        var dean = authorization.requireDeanOfFaculty(period.getFaculty().getId());

        TopicEntity topic = topics.findByIdForUpdate(topicId)
                .orElseThrow(() -> new ApplicationException(
                        ApplicationErrorCode.RESOURCE_NOT_FOUND));
        if (!Objects.equals(topic.getPeriod().getId(), period.getId())) {
            throw new ApplicationException(ApplicationErrorCode.CONFLICT);
        }
        if (topic.getVersion() != expectedVersion) {
            throw new OptimisticLockingFailureException("Topic version is stale");
        }
        return new LockedTopic(period, topic, dean);
    }

    private RegistrationPeriodEntity lockedPeriod(long periodId) {
        return periods.findByIdForUpdate(periodId)
                .orElseThrow(() -> new ApplicationException(
                        ApplicationErrorCode.RESOURCE_NOT_FOUND));
    }

    private TopicStatus statusOf(TopicEntity topic) {
        try {
            return TopicStatus.valueOf(topic.getStatus());
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new ApplicationException(ApplicationErrorCode.CONFLICT);
        }
    }

    private ReviewTopicView toReviewView(TopicEntity topic) {
        return new ReviewTopicView(
                topic.getId(),
                topic.getDepartment().getName(),
                displayName(topic.getAuthor()),
                topic.getTitle(),
                topic.getDescription(),
                topic.getObjectives(),
                topic.getStudentRequirements(),
                topic.getExpectedResults(),
                statusOf(topic),
                topic.getVersion(),
                topic.getSupervisors().stream()
                        .map(TopicSupervisorEntity::getLecturer)
                        .map(TopicReviewService::displayName)
                        .toList());
    }

    private static String displayName(UserAccountEntity account) {
        return account.getFullName() == null || account.getFullName().isBlank()
                ? account.getUsername()
                : account.getFullName();
    }

    private <T> T applyRule(RuleOperation<T> operation) {
        try {
            return operation.run();
        } catch (InvalidTopicReviewStateException exception) {
            throw new ApplicationException(ApplicationErrorCode.INVALID_TOPIC_STATE);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new ApplicationException(ApplicationErrorCode.VALIDATION_FAILED);
        }
    }

    @FunctionalInterface
    private interface RuleOperation<T> {
        T run();
    }

    private record LockedTopic(
            RegistrationPeriodEntity period,
            TopicEntity topic,
            vn.edu.topicregistration.security.AuthorizationPolicy.ActorIdentity actor) {
    }

    public record ReviewResult(
            long topicId,
            long periodId,
            TopicStatus status,
            long version) {
    }

    public record PublicationResult(
            long periodId,
            int publishedCount) {
    }

    public record ReviewWorkspace(
            long periodId,
            List<ReviewTopicView> topics) {
        public ReviewWorkspace {
            topics = List.copyOf(topics);
        }

        public long pendingCount() {
            return topics.stream()
                    .filter(topic -> topic.status() == TopicStatus.PENDING_APPROVAL)
                    .count();
        }

        public long approvedCount() {
            return topics.stream()
                    .filter(topic -> topic.status() == TopicStatus.APPROVED)
                    .count();
        }

        public long publishedCount() {
            return topics.stream()
                    .filter(topic -> topic.status() == TopicStatus.PUBLISHED)
                    .count();
        }
    }

    public record ReviewTopicView(
            long id,
            String department,
            String author,
            String title,
            String description,
            String objectives,
            String studentRequirements,
            String expectedResults,
            TopicStatus status,
            long version,
            List<String> supervisors) {
        public ReviewTopicView {
            supervisors = List.copyOf(supervisors);
        }
    }
}
