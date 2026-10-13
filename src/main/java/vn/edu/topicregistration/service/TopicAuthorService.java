package vn.edu.topicregistration.service;

import vn.edu.topicregistration.entity.RoleCode;
import vn.edu.topicregistration.entity.DepartmentEntity;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
        LoadedDraft loaded = loadDraft(command, actor.accountId());
        UserAccountEntity author = loaded.lecturers().getFirst();
        DepartmentEntity department = requirePeriodDepartment(period, author, actor.facultyId());
        requireDepartmentAlignment(period, department.getId(), department.getId());
        TopicDraft valid = applyRules(() -> TopicRules.validateForSave(
                loaded.draft(), access(actor, actor.accountId(), actor.facultyId(),
                        department.getId(), "DRAFT")));

        TopicEntity topic = new TopicEntity(period, author, department);
        applyDraft(topic, valid, loaded.lecturers());
        return toView(topics.saveAndFlush(topic));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public TopicView saveDraft(long topicId, long expectedVersion, TopicDraftCommand command) {
        Objects.requireNonNull(command, "command");
        LockedTopic locked = lockedTopic(topicId, expectedVersion);
        ActorIdentity actor = requireAuthor(locked.topic());
        LoadedDraft loaded = loadDraft(command, locked.topic().getAuthor().getId());
        DepartmentEntity department = requirePeriodDepartment(
                locked.period(), loaded.lecturers().getFirst(), actor.facultyId());
        requireDepartmentAlignment(locked.period(),
                departmentId(locked.topic().getDepartment()), department.getId());
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
        List<Long> supervisorIds = topic.getSupervisors().stream()
                .map(supervisor -> supervisor.getLecturer().getId())
                .toList();
        List<Long> participantIds = new ArrayList<>(supervisorIds);
        participantIds.add(topic.getAuthor().getId());
        Map<Long, UserAccountEntity> lockedAccounts = lockAccounts(participantIds);
        DepartmentEntity department = requirePeriodDepartment(
                locked.period(), lockedAccounts.get(topic.getAuthor().getId()), actor.facultyId());
        requireDepartmentAlignment(locked.period(),
                departmentId(topic.getDepartment()), department.getId());
        TopicDraft current = draftFrom(topic, lockedAccounts);
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
        UserAccountEntity author = requiredEntity(UserAccountEntity.class, actor.accountId());
        DepartmentEntity department = requireAuthorDepartment(author, actor.facultyId());
        Instant now = clock.instant();
        return periods.findAllByFacultyIdAndDepartmentIdOrderByLecturerStartDesc(
                        actor.facultyId(), department.getId()).stream()
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
        ActorIdentity actor = currentLecturer();
        UserAccountEntity author = requiredEntity(UserAccountEntity.class, actor.accountId());
        requireAuthorDepartment(author, context.facultyId());
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
        requirePeriodDepartment(topic.getPeriod(), topic.getAuthor(), actor.facultyId());
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
        UserAccountEntity author = requiredEntity(UserAccountEntity.class, actor.accountId());
        DepartmentEntity department = requireAuthorDepartment(author, actor.facultyId());
        RegistrationPeriodEntity period = periods
                .findByIdAndFacultyIdAndDepartmentId(
                        periodId, actor.facultyId(), department.getId())
                .orElseThrow(() -> new ApplicationException(ApplicationErrorCode.RESOURCE_NOT_FOUND));
        List<UserAccountEntity> eligibleLecturers =
                accounts.findEnabledByFacultyAndDepartmentAndRole(
                        actor.facultyId(), department.getId(), RoleCode.LECTURER);
        List<LecturerOption> coSupervisors = eligibleLecturers
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

    private static DepartmentEntity requireAuthorDepartment(
            UserAccountEntity author, Long facultyId) {
        DepartmentEntity department = author.getDepartment();
        if (author.getFaculty() == null
                || !Objects.equals(author.getFaculty().getId(), facultyId)
                || department == null
                || department.getId() == null
                || department.getFaculty() == null
                || !Objects.equals(department.getFaculty().getId(), facultyId)) {
            throw new ApplicationException(ApplicationErrorCode.TOPIC_DEPARTMENT_REQUIRED);
        }
        return department;
    }

    private static DepartmentEntity requirePeriodDepartment(
            RegistrationPeriodEntity period, UserAccountEntity author, Long facultyId) {
        DepartmentEntity department = requireAuthorDepartment(author, facultyId);
        if (period.getDepartment() == null
                || !Objects.equals(period.getDepartment().getId(), department.getId())) {
            throw new ApplicationException(ApplicationErrorCode.RESOURCE_NOT_FOUND);
        }
        return department;
    }

    private LoadedDraft loadDraft(TopicDraftCommand command, long authorId) {
        if (command.coSupervisorIds().size() > 1) {
            throw new ApplicationException(ApplicationErrorCode.VALIDATION_FAILED);
        }
        List<Long> ids = new ArrayList<>(List.of(authorId));
        for (Long lecturerId : command.coSupervisorIds()) {
            if (lecturerId == null || Objects.equals(lecturerId, authorId)) {
                throw new ApplicationException(ApplicationErrorCode.VALIDATION_FAILED);
            }
            ids.add(lecturerId);
        }
        Map<Long, UserAccountEntity> locked = lockAccounts(ids);
        List<UserAccountEntity> lecturers = ids.stream().map(locked::get).toList();
        List<SupervisorCandidate> candidates = lecturers.stream()
                .map(this::candidateFrom).toList();
        TopicDraft draft = new TopicDraft(
                command.title(),
                command.description(),
                command.objectives(),
                command.studentRequirements(),
                command.expectedResults(),
                candidates);
        return new LoadedDraft(draft, List.copyOf(lecturers));
    }

    private Map<Long, UserAccountEntity> lockAccounts(List<Long> ids) {
        Map<Long, UserAccountEntity> locked = new HashMap<>();
        for (Long id : ids.stream().distinct().sorted().toList()) {
            UserAccountEntity account = accounts.findByIdForUpdate(id)
                    .orElseThrow(() -> new ApplicationException(
                            ApplicationErrorCode.VALIDATION_FAILED));
            entityManager.refresh(account);
            locked.put(id, account);
        }
        return locked;
    }

    private void requireDepartmentAlignment(
            RegistrationPeriodEntity period, Long topicDepartmentId, Long authorDepartmentId) {
        applyRules(() -> {
            TopicRules.requireDepartmentAlignment(
                    departmentId(period.getDepartment()), topicDepartmentId, authorDepartmentId);
            return null;
        });
    }

    private static Long departmentId(DepartmentEntity department) {
        return department == null ? null : department.getId();
    }

    private SupervisorCandidate candidateFrom(UserAccountEntity lecturer) {
        Long facultyId = lecturer.getFaculty() == null
                ? null
                : lecturer.getFaculty().getId();
        Long departmentId = lecturer.getDepartment() == null
                ? null
                : lecturer.getDepartment().getId();
        return new SupervisorCandidate(
                lecturer.getId(),
                facultyId == null ? Long.MIN_VALUE : facultyId,
                departmentId,
                lecturer.isEnabled(),
                lecturer.getRoleCodes().contains(RoleCode.LECTURER));
    }

    private TopicAccess access(ActorIdentity actor, TopicEntity topic) {
        return access(
                actor,
                topic.getAuthor().getId(),
                topic.getPeriod().getFaculty().getId(),
                topic.getDepartment() == null ? null : topic.getDepartment().getId(),
                topic.getStatus());
    }

    private TopicAccess access(
            ActorIdentity actor,
            long authorId,
            long topicFacultyId,
            Long topicDepartmentId,
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
                    topicDepartmentId,
                    TopicStatus.valueOf(status));
        } catch (IllegalArgumentException exception) {
            throw new ApplicationException(ApplicationErrorCode.CONFLICT);
        }
    }

    private TopicDraft draftFrom(
            TopicEntity topic, Map<Long, UserAccountEntity> lockedAccounts) {
        List<SupervisorCandidate> supervisors = topic.getSupervisors().stream()
                .map(TopicSupervisorEntity::getLecturer)
                .map(lecturer -> lockedAccounts.get(lecturer.getId()))
                .map(this::candidateFrom)
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
                case DEPARTMENT_MISMATCH -> throw new ApplicationException(
                        ApplicationErrorCode.TOPIC_DEPARTMENT_REQUIRED);
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
                topic.getDepartment() == null ? null : topic.getDepartment().getName(),
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
            String departmentName,
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
