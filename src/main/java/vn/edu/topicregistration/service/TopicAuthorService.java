package vn.edu.topicregistration.service;

import vn.edu.topicregistration.entity.RoleCode;
import vn.edu.topicregistration.entity.UserAccountEntity;
import vn.edu.topicregistration.repository.UserAccountRepository;
import vn.edu.topicregistration.entity.RegistrationPeriodEntity;
import vn.edu.topicregistration.repository.RegistrationPeriodRepository;
import vn.edu.topicregistration.exception.ApplicationErrorCode;
import vn.edu.topicregistration.exception.ApplicationException;
import vn.edu.topicregistration.security.AuthorizationPolicy;
import vn.edu.topicregistration.entity.TopicEntity;
import vn.edu.topicregistration.repository.TopicRepository;
import vn.edu.topicregistration.entity.TopicSupervisorEntity;

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

import vn.edu.topicregistration.security.AuthorizationPolicy.ActorIdentity;
import vn.edu.topicregistration.service.TopicRules.SupervisorCandidate;
import vn.edu.topicregistration.service.TopicRules.TopicAccess;
import vn.edu.topicregistration.service.TopicRules.TopicDraft;
import vn.edu.topicregistration.service.TopicRules.TopicRuleException;
import vn.edu.topicregistration.service.TopicRules.TopicStatus;

@Service
@Profile("!test")
public class TopicAuthorService {
    private final RegistrationPeriodRepository periods;
    private final TopicRepository topics;
    private final UserAccountRepository accounts;
    private final AuthorizationPolicy authorization;
    private final EntityManager entityManager;
    private final Clock clock;

    public TopicAuthorService(
            RegistrationPeriodRepository periods,
            TopicRepository topics,
            UserAccountRepository accounts,
            AuthorizationPolicy authorization,
            EntityManager entityManager,
            Clock clock) {
        this.periods = periods;
        this.topics = topics;
        this.accounts = accounts;
        this.authorization = authorization;
        this.entityManager = entityManager;
        this.clock = clock;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public TopicView createDraft(long periodId, TopicDraftCommand command) {
        Objects.requireNonNull(command, "command");
        RegistrationPeriodEntity period = lockedPeriod(periodId);
        ActorIdentity actor = currentLecturerFor(period);
        requireLecturerWindow(period, clock.instant());
        UserAccountEntity author = requiredEntity(UserAccountEntity.class, actor.accountId());
        LoadedDraft loaded = loadDraft(command, author);
        TopicDraft valid = applyRules(() -> TopicRules.validateForSave(
                loaded.draft(), access(actor, actor.accountId(), actor.facultyId(), "DRAFT")));

        TopicEntity topic = new TopicEntity(period, author);
        applyDraft(topic, valid, loaded.lecturers());
        return toView(topics.saveAndFlush(topic));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public TopicView saveDraft(long topicId, long expectedVersion, TopicDraftCommand command) {
        Objects.requireNonNull(command, "command");
        LockedTopic locked = lockedTopic(topicId, expectedVersion);
        ActorIdentity actor = requireAuthor(locked.topic());
        LoadedDraft loaded = loadDraft(command, locked.topic().getAuthor());
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

    @Transactional(readOnly = true)
    public TopicWorkspace listOwnForPeriod(long periodId) {
        TopicFormContext context = formContext(periodId);
        ActorIdentity actor = currentLecturer();
        List<TopicView> ownTopics = topics
                .findAllByPeriodIdAndAuthorIdAndHiddenAtIsNullOrderByIdDesc(
                        periodId, actor.accountId())
                .stream()
                .map(this::toView)
                .toList();
        return new TopicWorkspace(context.period(), ownTopics);
    }

    @Transactional(readOnly = true)
    public List<TopicPeriodView> listPeriodsForAuthor() {
        ActorIdentity actor = currentLecturer();
        Instant now = clock.instant();
        return periods.findAllByFacultyIdOrderByLecturerStartDesc(actor.facultyId()).stream()
                .map(period -> new TopicPeriodView(
                        period.getId(),
                        period.getName(),
                        period.getLecturerStart(),
                        period.getLecturerEnd(),
                        !now.isBefore(period.getLecturerStart())
                                && !now.isAfter(period.getLecturerEnd()),
                        !now.isAfter(period.getStudentEnd())))
                .toList();
    }

    @Transactional(readOnly = true)
    public TopicFormContext createFormContext(long periodId) {
        TopicFormContext context = formContext(periodId);
        if (!context.period().lecturerOpen()) {
            throw new ApplicationException(ApplicationErrorCode.WINDOW_CLOSED);
        }
        return context;
    }

    @Transactional(readOnly = true)
    public TopicView getOwn(long topicId) {
        ActorIdentity actor = currentLecturer();
        TopicEntity topic = topics
                .findByIdAndAuthorIdAndPeriodFacultyIdAndHiddenAtIsNull(
                        topicId, actor.accountId(), actor.facultyId())
                .orElseThrow(() -> new ApplicationException(ApplicationErrorCode.RESOURCE_NOT_FOUND));
        if (!Objects.equals(actor.facultyId(), topic.getPeriod().getFaculty().getId())) {
            throw new ApplicationException(ApplicationErrorCode.RESOURCE_NOT_FOUND);
        }
        return toView(topic);
    }

    @Transactional(readOnly = true)
    public TopicView getOwnDraft(long topicId) {
        TopicView topic = getOwn(topicId);
        if (topic.status() != TopicStatus.DRAFT) {
            throw new ApplicationException(ApplicationErrorCode.INVALID_TOPIC_STATE);
        }
        return topic;
    }

    @Transactional(readOnly = true)
    public TopicFormContext formContext(long periodId) {
        ActorIdentity actor = currentLecturer();
        RegistrationPeriodEntity period = periods
                .findByIdAndFacultyId(periodId, actor.facultyId())
                .orElseThrow(() -> new ApplicationException(ApplicationErrorCode.RESOURCE_NOT_FOUND));
        UserAccountEntity author = requiredEntity(UserAccountEntity.class, actor.accountId());
        List<LecturerOption> coSupervisors = accounts
                .findEnabledByFacultyAndRole(actor.facultyId(), RoleCode.LECTURER)
                .stream()
                .filter(account -> !Objects.equals(account.getId(), actor.accountId()))
                .map(account -> new LecturerOption(
                        account.getId(), displayName(account), account.getUsername()))
                .toList();
        Instant now = clock.instant();
        return new TopicFormContext(
                new TopicPeriodView(
                        period.getId(),
                        period.getName(),
                        period.getLecturerStart(),
                        period.getLecturerEnd(),
                        !now.isBefore(period.getLecturerStart())
                                && !now.isAfter(period.getLecturerEnd()),
                        !now.isAfter(period.getStudentEnd())),
                period.getFaculty().getId(),
                period.getFaculty().getName(),
                new LecturerOption(author.getId(), displayName(author), author.getUsername()),
                coSupervisors);
    }

    private RegistrationPeriodEntity lockedPeriod(long periodId) {
        return periods.findByIdForUpdate(periodId)
                .orElseThrow(() -> new ApplicationException(ApplicationErrorCode.RESOURCE_NOT_FOUND));
    }

    private static void requireLecturerWindow(RegistrationPeriodEntity period, Instant now) {
        if (now.isBefore(period.getLecturerStart()) || now.isAfter(period.getLecturerEnd())) {
            throw new ApplicationException(ApplicationErrorCode.WINDOW_CLOSED);
        }
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
        ActorIdentity actor = currentLecturer();
        if (actor.facultyId() == null
                || !Objects.equals(actor.facultyId(), period.getFaculty().getId())) {
            throw new AccessDeniedException("Lecturer scope does not match the period");
        }
        return actor;
    }

    private ActorIdentity currentLecturer() {
        ActorIdentity actor = authorization.requireTopicAuthoringLecturer();
        if (actor.accountId() == null || actor.facultyId() == null) {
            throw new AccessDeniedException("Lecturer organization scope is missing");
        }
        return actor;
    }

    private ActorIdentity requireAuthor(TopicEntity topic) {
        ActorIdentity actor = authorization.requireTopicAuthor(
                topic.getAuthor().getId(), topic.getPeriod().getFaculty().getId());
        if (actor.facultyId() == null
                || !Objects.equals(actor.facultyId(), topic.getPeriod().getFaculty().getId())) {
            throw new AccessDeniedException("Topic is outside the lecturer faculty");
        }
        return actor;
    }

    private LoadedDraft loadDraft(
            TopicDraftCommand command,
            UserAccountEntity author) {
        if (command.coSupervisorIds().size() > 1) {
            throw new ApplicationException(ApplicationErrorCode.VALIDATION_FAILED);
        }
        List<UserAccountEntity> lecturers = new ArrayList<>(List.of(author));
        List<SupervisorCandidate> candidates = new ArrayList<>(List.of(candidateFrom(author)));
        for (Long lecturerId : command.coSupervisorIds()) {
            if (lecturerId == null || Objects.equals(lecturerId, author.getId())) {
                throw new ApplicationException(ApplicationErrorCode.VALIDATION_FAILED);
            }
            UserAccountEntity lecturer = entityManager.find(UserAccountEntity.class, lecturerId);
            if (lecturer == null) {
                throw new ApplicationException(ApplicationErrorCode.VALIDATION_FAILED);
            }
            candidates.add(candidateFrom(lecturer));
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

    private SupervisorCandidate candidateFrom(UserAccountEntity lecturer) {
        Long facultyId = lecturer.getFaculty() == null
                ? null
                : lecturer.getFaculty().getId();
        return new SupervisorCandidate(
                lecturer.getId(),
                facultyId == null ? Long.MIN_VALUE : facultyId,
                lecturer.isEnabled(),
                lecturer.getRoleCodes().contains(RoleCode.LECTURER));
    }

    private TopicAccess access(ActorIdentity actor, TopicEntity topic) {
        return access(
                actor,
                topic.getAuthor().getId(),
                topic.getPeriod().getFaculty().getId(),
                topic.getStatus());
    }

    private TopicAccess access(
            ActorIdentity actor,
            long authorId,
            long topicFacultyId,
            String status) {
        if (actor.facultyId() == null) {
            throw new AccessDeniedException("Lecturer faculty is missing");
        }
        try {
            return new TopicAccess(
                    actor.accountId(),
                    authorId,
                    actor.facultyId(),
                    topicFacultyId,
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
                        lecturer.getFaculty() == null
                                ? Long.MIN_VALUE
                                : lecturer.getFaculty().getId(),
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
                topic.getPeriod().getFaculty().getId(),
                topic.getPeriod().getFaculty().getName(),
                topic.getAuthor().getId(),
                displayName(topic.getAuthor()),
                topic.getTitle(),
                topic.getDescription(),
                topic.getObjectives(),
                topic.getStudentRequirements(),
                topic.getExpectedResults(),
                TopicStatus.valueOf(topic.getStatus()),
                topic.getVersion(),
                supervisors);
    }

    private static String displayName(UserAccountEntity account) {
        return account.getFullName() == null || account.getFullName().isBlank()
                ? account.getUsername()
                : account.getFullName();
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
            List<Long> coSupervisorIds) {
        public TopicDraftCommand {
            coSupervisorIds = coSupervisorIds == null ? List.of() : List.copyOf(coSupervisorIds);
        }
    }

    public record SupervisorView(Long id, String fullName, int slot) {
    }

    public record LecturerOption(Long id, String fullName, String username) {
    }

    public record TopicPeriodView(
            Long id,
            String name,
            Instant lecturerStart,
            Instant lecturerEnd,
            boolean lecturerOpen,
            boolean active) {
    }

    public record TopicFormContext(
            TopicPeriodView period,
            Long facultyId,
            String facultyName,
            LecturerOption primarySupervisor,
            List<LecturerOption> coSupervisors) {
        public TopicFormContext {
            Objects.requireNonNull(primarySupervisor, "primarySupervisor");
            coSupervisors = List.copyOf(coSupervisors);
        }
    }

    public record TopicWorkspace(TopicPeriodView period, List<TopicView> topics) {
        public TopicWorkspace {
            topics = List.copyOf(topics);
        }
    }

    public record TopicView(
            Long id,
            Long periodId,
            Long facultyId,
            String facultyName,
            Long authorId,
            String authorName,
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
