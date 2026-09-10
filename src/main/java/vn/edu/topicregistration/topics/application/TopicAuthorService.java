package vn.edu.topicregistration.topics.application;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import jakarta.persistence.EntityManager;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import vn.edu.topicregistration.identity.domain.RoleCode;
import vn.edu.topicregistration.identity.persistence.DepartmentEntity;
import vn.edu.topicregistration.identity.persistence.UserAccountEntity;
import vn.edu.topicregistration.periods.persistence.RegistrationPeriodEntity;
import vn.edu.topicregistration.periods.persistence.RegistrationPeriodRepository;
import vn.edu.topicregistration.shared.error.ApplicationErrorCode;
import vn.edu.topicregistration.shared.error.ApplicationException;
import vn.edu.topicregistration.shared.security.AuthorizationPolicy;
import vn.edu.topicregistration.shared.security.AuthorizationPolicy.ActorIdentity;
import vn.edu.topicregistration.topics.domain.TopicRules;
import vn.edu.topicregistration.topics.domain.TopicRules.SupervisorCandidate;
import vn.edu.topicregistration.topics.domain.TopicRules.TopicAccess;
import vn.edu.topicregistration.topics.domain.TopicRules.TopicDraft;
import vn.edu.topicregistration.topics.domain.TopicRules.TopicRuleException;
import vn.edu.topicregistration.topics.domain.TopicRules.TopicStatus;
import vn.edu.topicregistration.topics.persistence.TopicEntity;
import vn.edu.topicregistration.topics.persistence.TopicRepository;
import vn.edu.topicregistration.topics.persistence.TopicSupervisorEntity;

@Service
@Profile("!preview & !test")
public class TopicAuthorService {
    private final RegistrationPeriodRepository periods;
    private final TopicRepository topics;
    private final AuthorizationPolicy authorization;
    private final EntityManager entityManager;
    private final Clock clock;

    public TopicAuthorService(
            RegistrationPeriodRepository periods,
            TopicRepository topics,
            AuthorizationPolicy authorization,
            EntityManager entityManager,
            Clock clock) {
        this.periods = periods;
        this.topics = topics;
        this.authorization = authorization;
        this.entityManager = entityManager;
        this.clock = clock;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public TopicView createDraft(long periodId, TopicDraftCommand command) {
        Objects.requireNonNull(command, "command");
        RegistrationPeriodEntity period = lockedPeriod(periodId);
        ActorIdentity actor = currentLecturerFor(period);
        DepartmentEntity department = requiredEntity(DepartmentEntity.class, actor.departmentId());
        UserAccountEntity author = requiredEntity(UserAccountEntity.class, actor.accountId());
        LoadedDraft loaded = loadDraft(command);
        TopicDraft valid = applyRules(() -> TopicRules.validateForSave(
                loaded.draft(), access(actor, actor.accountId(), actor.departmentId(), "DRAFT")));

        TopicEntity topic = new TopicEntity(period, department, author);
        applyDraft(topic, valid, loaded.lecturers());
        return toView(topics.saveAndFlush(topic));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public TopicView saveDraft(long topicId, long expectedVersion, TopicDraftCommand command) {
        Objects.requireNonNull(command, "command");
        LockedTopic locked = lockedTopic(topicId, expectedVersion);
        ActorIdentity actor = requireAuthor(locked.topic());
        LoadedDraft loaded = loadDraft(command);
        TopicDraft valid = applyRules(() -> TopicRules.validateForSave(
                loaded.draft(), access(actor, locked.topic())));

        applyDraft(locked.topic(), valid, loaded.lecturers());
        topics.flush();
        return toView(locked.topic());
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public TopicView submit(long topicId, long expectedVersion) {
        LockedTopic locked = lockedTopic(topicId, expectedVersion);
        TopicEntity topic = locked.topic();
        ActorIdentity actor = requireAuthor(topic);
        TopicDraft current = draftFrom(topic);
        TopicDraft valid = applyRules(() -> TopicRules.validateForSubmit(
                current,
                access(actor, topic),
                locked.now(),
                locked.period().getLecturerStart(),
                locked.period().getLecturerEnd()));

        topic.updateDraft(
                valid.title(),
                valid.description(),
                valid.objectives(),
                valid.studentRequirements(),
                valid.expectedResults());
        topic.changeStatus(TopicStatus.PENDING_APPROVAL.name());
        topics.flush();
        return toView(topic);
    }

    private RegistrationPeriodEntity lockedPeriod(long periodId) {
        return periods.findByIdForUpdate(periodId)
                .orElseThrow(() -> new ApplicationException(ApplicationErrorCode.RESOURCE_NOT_FOUND));
    }

    private LockedTopic lockedTopic(long topicId, long expectedVersion) {
        long periodId = topics.findPeriodIdByTopicId(topicId)
                .orElseThrow(() -> new ApplicationException(ApplicationErrorCode.RESOURCE_NOT_FOUND));
        RegistrationPeriodEntity period = lockedPeriod(periodId);
        TopicEntity topic = topics.findByIdForUpdate(topicId)
                .orElseThrow(() -> new ApplicationException(ApplicationErrorCode.RESOURCE_NOT_FOUND));
        if (!Objects.equals(topic.getPeriod().getId(), period.getId())) {
            throw new ApplicationException(ApplicationErrorCode.CONFLICT);
        }
        if (topic.getVersion() != expectedVersion) {
            throw new OptimisticLockingFailureException("Topic version is stale");
        }
        return new LockedTopic(period, topic, clock.instant());
    }

    private ActorIdentity currentLecturerFor(RegistrationPeriodEntity period) {
        ActorIdentity actor = authorization.requireRole(RoleCode.LECTURER);
        if (actor.departmentId() == null
                || actor.facultyId() == null
                || !Objects.equals(actor.facultyId(), period.getFaculty().getId())) {
            throw new AccessDeniedException("Lecturer scope does not match the period");
        }
        return actor;
    }

    private ActorIdentity requireAuthor(TopicEntity topic) {
        ActorIdentity actor = authorization.requireTopicAuthor(
                topic.getAuthor().getId(), topic.getDepartment().getId());
        if (actor.facultyId() == null
                || !Objects.equals(actor.facultyId(), topic.getPeriod().getFaculty().getId())) {
            throw new AccessDeniedException("Topic is outside the lecturer faculty");
        }
        return actor;
    }

    private LoadedDraft loadDraft(TopicDraftCommand command) {
        if (command.supervisorIds().size() > 2) {
            throw new ApplicationException(ApplicationErrorCode.VALIDATION_FAILED);
        }
        List<UserAccountEntity> lecturers = new ArrayList<>();
        List<SupervisorCandidate> candidates = new ArrayList<>();
        for (Long lecturerId : command.supervisorIds()) {
            if (lecturerId == null) {
                throw new ApplicationException(ApplicationErrorCode.VALIDATION_FAILED);
            }
            UserAccountEntity lecturer = entityManager.find(UserAccountEntity.class, lecturerId);
            if (lecturer == null) {
                throw new ApplicationException(ApplicationErrorCode.VALIDATION_FAILED);
            }
            Long departmentId = lecturer.getDepartment() == null
                    ? null
                    : lecturer.getDepartment().getId();
            candidates.add(new SupervisorCandidate(
                    lecturer.getId(),
                    departmentId == null ? Long.MIN_VALUE : departmentId,
                    lecturer.isEnabled(),
                    lecturer.getRoleCodes().contains(RoleCode.LECTURER)));
            lecturers.add(lecturer);
        }
        TopicDraft draft = new TopicDraft(
                command.title(),
                command.description(),
                command.objectives(),
                command.studentRequirements(),
                command.expectedResults(),
                candidates);
        return new LoadedDraft(draft, List.copyOf(lecturers));
    }

    private TopicAccess access(ActorIdentity actor, TopicEntity topic) {
        return access(
                actor,
                topic.getAuthor().getId(),
                topic.getDepartment().getId(),
                topic.getStatus());
    }

    private TopicAccess access(
            ActorIdentity actor,
            long authorId,
            long topicDepartmentId,
            String status) {
        if (actor.departmentId() == null) {
            throw new AccessDeniedException("Lecturer department is missing");
        }
        try {
            return new TopicAccess(
                    actor.accountId(),
                    authorId,
                    actor.departmentId(),
                    topicDepartmentId,
                    TopicStatus.valueOf(status));
        } catch (IllegalArgumentException exception) {
            throw new ApplicationException(ApplicationErrorCode.CONFLICT);
        }
    }

    private TopicDraft draftFrom(TopicEntity topic) {
        List<SupervisorCandidate> supervisors = topic.getSupervisors().stream()
                .map(TopicSupervisorEntity::getLecturer)
                .map(lecturer -> new SupervisorCandidate(
                        lecturer.getId(),
                        lecturer.getDepartment() == null
                                ? Long.MIN_VALUE
                                : lecturer.getDepartment().getId(),
                        lecturer.isEnabled(),
                        lecturer.getRoleCodes().contains(RoleCode.LECTURER)))
                .toList();
        return new TopicDraft(
                topic.getTitle(),
                topic.getDescription(),
                topic.getObjectives(),
                topic.getStudentRequirements(),
                topic.getExpectedResults(),
                supervisors);
    }

    private void applyDraft(
            TopicEntity topic,
            TopicDraft draft,
            List<UserAccountEntity> lecturers) {
        topic.updateDraft(
                draft.title(),
                draft.description(),
                draft.objectives(),
                draft.studentRequirements(),
                draft.expectedResults());
        topic.replaceSupervisors(lecturers);
    }

    private <T> T requiredEntity(Class<T> type, Long id) {
        T entity = id == null ? null : entityManager.find(type, id);
        if (entity == null) {
            throw new AccessDeniedException("Authorized identity data is missing");
        }
        return entity;
    }

    private <T> T applyRules(RuleOperation<T> operation) {
        try {
            return operation.run();
        } catch (TopicRuleException exception) {
            switch (exception.failure()) {
                case ACCESS_DENIED -> throw new AccessDeniedException("Access denied");
                case INVALID_STATE -> throw new ApplicationException(
                        ApplicationErrorCode.INVALID_TOPIC_STATE);
                case VALIDATION_FAILED -> throw new ApplicationException(
                        ApplicationErrorCode.VALIDATION_FAILED);
                case WINDOW_CLOSED -> throw new ApplicationException(
                        ApplicationErrorCode.WINDOW_CLOSED);
            }
            throw new IllegalStateException("Unknown topic rule failure", exception);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new ApplicationException(ApplicationErrorCode.VALIDATION_FAILED);
        }
    }

    private TopicView toView(TopicEntity topic) {
        List<SupervisorView> supervisors = topic.getSupervisors().stream()
                .map(value -> new SupervisorView(
                        value.getLecturer().getId(),
                        value.getLecturer().getFullName(),
                        value.getSlot()))
                .toList();
        return new TopicView(
                topic.getId(),
                topic.getPeriod().getId(),
                topic.getDepartment().getId(),
                topic.getDepartment().getName(),
                topic.getAuthor().getId(),
                topic.getTitle(),
                topic.getDescription(),
                topic.getObjectives(),
                topic.getStudentRequirements(),
                topic.getExpectedResults(),
                TopicStatus.valueOf(topic.getStatus()),
                topic.getVersion(),
                supervisors);
    }

    @FunctionalInterface
    private interface RuleOperation<T> {
        T run();
    }

    private record LoadedDraft(TopicDraft draft, List<UserAccountEntity> lecturers) {
    }

    private record LockedTopic(
            RegistrationPeriodEntity period,
            TopicEntity topic,
            Instant now) {
    }

    public record TopicDraftCommand(
            String title,
            String description,
            String objectives,
            String studentRequirements,
            String expectedResults,
            List<Long> supervisorIds) {
        public TopicDraftCommand {
            supervisorIds = supervisorIds == null ? List.of() : List.copyOf(supervisorIds);
        }
    }

    public record SupervisorView(Long id, String fullName, int slot) {
    }

    public record TopicView(
            Long id,
            Long periodId,
            Long departmentId,
            String departmentName,
            Long authorId,
            String title,
            String description,
            String objectives,
            String studentRequirements,
            String expectedResults,
            TopicStatus status,
            long version,
            List<SupervisorView> supervisors) {
        public TopicView {
            supervisors = List.copyOf(supervisors);
        }
    }
}
