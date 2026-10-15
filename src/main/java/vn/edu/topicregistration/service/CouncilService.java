package vn.edu.topicregistration.service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import vn.edu.topicregistration.entity.CouncilEntity;
import vn.edu.topicregistration.entity.CouncilMemberEntity;
import vn.edu.topicregistration.entity.CouncilMode;
import vn.edu.topicregistration.entity.CouncilStatus;
import vn.edu.topicregistration.entity.CouncilTopicEntity;
import vn.edu.topicregistration.entity.EvaluationEntity;
import vn.edu.topicregistration.entity.EvaluationStatus;
import vn.edu.topicregistration.entity.NotificationEventType;
import vn.edu.topicregistration.entity.NotificationTargetType;
import vn.edu.topicregistration.entity.RegistrationEntity;
import vn.edu.topicregistration.entity.RegistrationPeriodEntity;
import vn.edu.topicregistration.entity.RegistrationStatus;
import vn.edu.topicregistration.entity.ReviewerAssignmentEntity;
import vn.edu.topicregistration.entity.RoleCode;
import vn.edu.topicregistration.entity.TopicSupervisorEntity;
import vn.edu.topicregistration.entity.UserAccountEntity;
import vn.edu.topicregistration.exception.ApplicationErrorCode;
import vn.edu.topicregistration.exception.ApplicationException;
import vn.edu.topicregistration.repository.CouncilMemberRepository;
import vn.edu.topicregistration.repository.CouncilRepository;
import vn.edu.topicregistration.repository.CouncilTopicRepository;
import vn.edu.topicregistration.repository.EvaluationRepository;
import vn.edu.topicregistration.repository.ProjectReportRepository;
import vn.edu.topicregistration.repository.RegistrationPeriodRepository;
import vn.edu.topicregistration.repository.RegistrationMemberRepository;
import vn.edu.topicregistration.repository.RegistrationRepository;
import vn.edu.topicregistration.repository.ReviewerAssignmentRepository;
import vn.edu.topicregistration.repository.UserAccountRepository;
import vn.edu.topicregistration.security.AuthorizationPolicy;
import vn.edu.topicregistration.security.AuthorizationPolicy.ActorIdentity;
import vn.edu.topicregistration.service.CouncilRules.AssignmentContext;
import vn.edu.topicregistration.service.CouncilRules.CouncilRuleException;
import vn.edu.topicregistration.service.CouncilRules.MemberCandidate;
import vn.edu.topicregistration.service.CouncilRules.TransitionContext;
import vn.edu.topicregistration.service.NotificationService.EmissionCommand;

@Service
@Profile("!test")
public class CouncilService {
    private final RegistrationPeriodRepository periods;
    private final UserAccountRepository accounts;
    private final CouncilRepository councils;
    private final CouncilMemberRepository members;
    private final CouncilTopicRepository councilTopics;
    private final RegistrationRepository registrations;
    private final RegistrationMemberRepository registrationMembers;
    private final ProjectReportRepository reports;
    private final ReviewerAssignmentRepository reviewerAssignments;
    private final EvaluationRepository evaluations;
    private final AuthorizationPolicy authorization;
    private final NotificationService notifications;
    private final EntityManager entityManager;
    private final Clock clock;

    public CouncilService(
            RegistrationPeriodRepository periods,
            UserAccountRepository accounts,
            CouncilRepository councils,
            CouncilMemberRepository members,
            CouncilTopicRepository councilTopics,
            RegistrationRepository registrations,
            RegistrationMemberRepository registrationMembers,
            ProjectReportRepository reports,
            ReviewerAssignmentRepository reviewerAssignments,
            EvaluationRepository evaluations,
            AuthorizationPolicy authorization,
            NotificationService notifications,
            EntityManager entityManager,
            Clock clock) {
        this.periods = periods;
        this.accounts = accounts;
        this.councils = councils;
        this.members = members;
        this.councilTopics = councilTopics;
        this.registrations = registrations;
        this.registrationMembers = registrationMembers;
        this.reports = reports;
        this.reviewerAssignments = reviewerAssignments;
        this.evaluations = evaluations;
        this.authorization = authorization;
        this.notifications = notifications;
        this.entityManager = entityManager;
        this.clock = clock;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public CouncilSummaryView create(CreateCouncilCommand command) {
        Objects.requireNonNull(command, "command");
        RegistrationPeriodEntity period = lockedPeriod(command.periodId());
        long facultyId = requiredFacultyId(period);
        ActorIdentity dean = authorization.requireDeanOfFaculty(facultyId);
        String name = normalizedName(command.name());
        List<Long> requestedIds = requestedMemberIds(command);
        List<UserAccountEntity> lecturers = requestedIds.stream()
                .map(this::detailedAccount)
                .toList();
        applyRules(() -> CouncilRules.validateCreation(
                period.getTopicType(),
                facultyId,
                command.chairId(),
                command.secretaryId(),
                lecturers.stream().map(CouncilService::candidate).toList()));

        UserAccountEntity chair = findRequested(lecturers, command.chairId());
        UserAccountEntity secretary = findRequested(lecturers, command.secretaryId());
        CouncilEntity council = councils.saveAndFlush(new CouncilEntity(
                period, name, chair, secretary, clock.instant()));

        List<CouncilMemberEntity> additionalMembers = new ArrayList<>();
        int slot = 3;
        for (Long memberId : command.memberIds()) {
            additionalMembers.add(new CouncilMemberEntity(
                    council, findRequested(lecturers, memberId), slot++));
        }
        members.saveAll(additionalMembers);
        members.flush();
        notifications.emit(new EmissionCommand(
                NotificationEventType.COUNCIL_MEMBER_ASSIGNED,
                council.getId(),
                dean.accountId(),
                requestedIds,
                "Bạn được phân công vào hội đồng",
                "Trưởng khoa đã thêm bạn vào một hội đồng chấm đề tài.",
                null,
                NotificationTargetType.COUNCIL,
                council.getId()));
        return toSummary(council);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public CouncilSummaryView saveAssignment(long councilId, AssignmentCommand command) {
        Objects.requireNonNull(command, "command");
        LockedCouncil locked = lockedCouncil(councilId, command.expectedVersion());
        CouncilEntity council = locked.council();
        requireCurrentCouncilWorkflow(locked);

        List<CouncilTopicEntity> currentAssignments = councilTopics
                .findAllByCouncilIdForUpdate(councilId);
        List<CouncilMemberEntity> currentMembers = members
                .findAllByCouncilIdForUpdate(councilId);
        applyRules(() -> validateCurrentMembers(locked.period(), council, currentMembers));
        RegistrationEntity registration = registrations.findByIdForUpdate(command.registrationId())
                .orElseThrow(() -> new ApplicationException(
                        ApplicationErrorCode.RESOURCE_NOT_FOUND));
        Optional<CouncilTopicEntity> existing = currentAssignments.stream()
                .filter(assignment -> Objects.equals(
                        assignment.getRegistration().getId(), command.registrationId()))
                .findFirst()
                .or(() -> councilTopics.findByRegistrationId(command.registrationId()));
        boolean assignedElsewhere = existing
                .map(assignment -> !Objects.equals(
                        assignment.getCouncil().getId(), councilId))
                .orElse(false);
        boolean supervisorConflict = hasSupervisorConflict(currentMembers, registration);
        boolean reviewerConflict = invalidReviewer(registration, currentMembers);

        applyRules(() -> CouncilRules.validateAssignment(new AssignmentContext(
                council.getStatus(),
                locked.period().getTopicType(),
                registration.getStatus(),
                Objects.equals(registration.getPeriod().getId(), locked.period().getId())
                        && Objects.equals(registration.getTopic().getPeriod().getId(),
                                locked.period().getId()),
                Objects.equals(
                        registration.getTopic().getPeriod().getFaculty().getId(),
                        council.getPeriod().getFaculty().getId()),
                assignedElsewhere,
                supervisorConflict,
                reviewerConflict)));

        String location = command.location() == null ? null : command.location().strip();
        if (command.scheduledAt() == null || location == null || location.isEmpty()
                || location.length() > 500) {
            throw new ApplicationException(ApplicationErrorCode.INVALID_COUNCIL_ASSIGNMENT);
        }
        if (!CouncilRules.isFutureSchedule(command.scheduledAt(), clock.instant())) {
            throw new InvalidScheduleException();
        }
        CouncilTopicEntity assignment = existing.orElseGet(
                () -> new CouncilTopicEntity(council, registration));
        assignment.schedule(command.scheduledAt(), location);
        councilTopics.saveAndFlush(assignment);
        forceVersionIncrement(council);
        return toSummary(council);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public CouncilSummaryView updateMembers(long councilId, UpdateMembersCommand command) {
        Objects.requireNonNull(command, "command");
        LockedCouncil locked = lockedCouncil(councilId, command.expectedVersion());
        requireCurrentCouncilWorkflow(locked);
        CouncilEntity council = locked.council();
        List<CouncilTopicEntity> assignments = councilTopics.findAllByCouncilIdForUpdate(councilId);
        List<CouncilMemberEntity> current = members.findAllByCouncilIdForUpdate(councilId);
        if ((council.getStatus() != CouncilStatus.DRAFT
                && council.getStatus() != CouncilStatus.ASSIGNED)
                || !evaluations.findAllByCouncilIdForUpdate(councilId).isEmpty()) {
            throw new ApplicationException(ApplicationErrorCode.INVALID_COUNCIL_STATE);
        }

        List<Long> requestedIds = requestedMemberIds(new CreateCouncilCommand(
                locked.period().getId(), council.getName(), command.chairId(),
                command.secretaryId(), command.memberIds()));
        List<UserAccountEntity> requested = requestedIds.stream().map(this::detailedAccount).toList();
        applyRules(() -> CouncilRules.validateCreation(locked.period().getTopicType(),
                requiredFacultyId(locked.period()), command.chairId(), command.secretaryId(),
                requested.stream().map(CouncilService::candidate).toList()));
        List<CouncilMemberEntity> proposed = new ArrayList<>();
        for (int index = 0; index < requested.size(); index++) {
            proposed.add(new CouncilMemberEntity(council, requested.get(index), index + 1));
        }
        for (CouncilTopicEntity assignment : assignments) {
            RegistrationEntity registration = assignment.getRegistration();
            if (hasSupervisorConflict(proposed, registration)
                    || invalidReviewer(registration, proposed)) {
                throw new ApplicationException(ApplicationErrorCode.INVALID_COUNCIL_MEMBERS);
            }
        }

        Set<Long> desiredIds = Set.copyOf(requestedIds);
        Map<Long, CouncilMemberEntity> retained = new LinkedHashMap<>();
        for (CouncilMemberEntity member : current) {
            retained.put(member.getLecturer().getId(), member);
        }
        Long chairId = council.getChair().getId();
        Long secretaryId = council.getSecretary().getId();
        for (CouncilMemberEntity member : current) {
            Long id = member.getLecturer().getId();
            if (!desiredIds.contains(id) && !id.equals(chairId) && !id.equals(secretaryId)) {
                members.delete(member);
                retained.remove(id);
            }
        }
        members.flush();

        for (UserAccountEntity lecturer : requested) {
            if (retained.containsKey(lecturer.getId())) {
                continue;
            }
            if (retained.size() == 5) {
                Long outgoing = !desiredIds.contains(chairId) ? chairId : secretaryId;
                if (desiredIds.contains(outgoing)) {
                    throw new ApplicationException(ApplicationErrorCode.INVALID_COUNCIL_MEMBERS);
                }
                Long otherOfficerId = outgoing.equals(chairId) ? secretaryId : chairId;
                CouncilMemberEntity temporary = retained.values().stream()
                        .filter(member -> !member.getLecturer().getId().equals(outgoing)
                                && !member.getLecturer().getId().equals(otherOfficerId))
                        .findFirst()
                        .orElseThrow(() -> new ApplicationException(
                                ApplicationErrorCode.INVALID_COUNCIL_MEMBERS));
                if (outgoing.equals(chairId)) {
                    chairId = temporary.getLecturer().getId();
                } else {
                    secretaryId = temporary.getLecturer().getId();
                }
                council.changeOfficers(retained.get(chairId).getLecturer(),
                        retained.get(secretaryId).getLecturer());
                councils.flush();
                members.delete(retained.remove(outgoing));
                members.flush();
            }
            int slot = freeSlot(retained.values());
            retained.put(lecturer.getId(), members.saveAndFlush(
                    new CouncilMemberEntity(council, lecturer, slot)));
        }

        council.changeOfficers(findRequested(requested, command.chairId()),
                findRequested(requested, command.secretaryId()));
        councils.flush();
        for (CouncilMemberEntity member : List.copyOf(retained.values())) {
            if (!desiredIds.contains(member.getLecturer().getId())) {
                members.delete(member);
                retained.remove(member.getLecturer().getId());
            }
        }
        members.flush();
        forceVersionIncrement(council);
        return toSummary(council);
    }

    private static int freeSlot(java.util.Collection<CouncilMemberEntity> members) {
        for (int slot = 1; slot <= 5; slot++) {
            int candidate = slot;
            if (members.stream().noneMatch(member -> member.getSlot() == candidate)) {
                return slot;
            }
        }
        throw new ApplicationException(ApplicationErrorCode.INVALID_COUNCIL_MEMBERS);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public CouncilSummaryView transition(
            long councilId,
            long expectedVersion,
            CouncilStatus target) {
        LockedCouncil locked = lockedCouncil(councilId, expectedVersion);
        CouncilEntity council = locked.council();
        requireCurrentCouncilWorkflow(locked);
        List<CouncilTopicEntity> assignments = councilTopics
                .findAllByCouncilIdForUpdate(councilId);
        List<CouncilMemberEntity> currentMembers = members
                .findAllByCouncilIdForUpdate(councilId);

        boolean allScheduled = assignments.stream().allMatch(CouncilService::isScheduled);
        boolean configurationValid = target != CouncilStatus.ACTIVE
                || activationConfigurationValid(locked.period(), council, currentMembers, assignments);
        boolean supervisorConflict = target == CouncilStatus.ACTIVE
                && assignments.stream().anyMatch(
                        assignment -> hasSupervisorConflict(
                                currentMembers, assignment.getRegistration()));
        boolean reviewerConflict = target == CouncilStatus.ACTIVE
                && assignments.stream().anyMatch(assignment -> invalidReviewer(
                        assignment.getRegistration(), currentMembers));
        boolean allReportsPresent = target != CouncilStatus.ACTIVE
                || assignments.stream().allMatch(assignment -> reports
                        .findCurrentByRegistrationIdForUpdate(
                                assignment.getRegistration().getId())
                        .isPresent());

        applyRules(() -> CouncilRules.validateTransition(new TransitionContext(
                council.getStatus(),
                target,
                currentMembers.size(),
                assignments.size(),
                allScheduled,
                configurationValid,
                supervisorConflict,
                reviewerConflict,
                allReportsPresent)));

        council.transitionTo(target);
        councils.flush();
        return toSummary(council);
    }

    @Transactional(readOnly = true)
    public List<CouncilSummaryView> listForCurrentDean() {
        ActorIdentity dean = currentDean();
        return councils.findAllByPeriodFacultyIdAndModeOrderByCreatedAtDescIdDesc(
                        dean.facultyId(), CouncilMode.COUNCIL)
                .stream()
                .map(CouncilService::toSummary)
                .toList();
    }

    @Transactional(readOnly = true)
    public CouncilCreationOptions creationOptionsForCurrentDean() {
        ActorIdentity dean = currentDean();
        List<CouncilPeriodOption> periodOptions = periods
                .findAllByFacultyIdOrderByLecturerStartDesc(dean.facultyId())
                .stream()
                .filter(period -> CouncilRules.isSupportedTopicType(period.getTopicType()))
                .map(period -> new CouncilPeriodOption(
                        period.getId(), period.getName(), period.getTopicType()))
                .toList();
        return new CouncilCreationOptions(periodOptions);
    }

    @Transactional(readOnly = true)
    public CreationWorkspace creationWorkspace(long periodId) {
        RegistrationPeriodEntity period = periods.findById(periodId)
                .orElseThrow(() -> new ApplicationException(
                        ApplicationErrorCode.RESOURCE_NOT_FOUND));
        long facultyId = requiredFacultyId(period);
        authorization.requireDeanOfFaculty(facultyId);
        if (!CouncilRules.isSupportedTopicType(period.getTopicType())) {
            throw new ApplicationException(ApplicationErrorCode.INVALID_COUNCIL_STATE);
        }
        List<LecturerOption> lecturerOptions = accounts
                .findEnabledByFacultyAndRole(facultyId, RoleCode.LECTURER)
                .stream()
                .filter(account -> account.getRoleCodes().contains(RoleCode.LECTURER)
                        && !account.getRoleCodes().contains(RoleCode.DEAN))
                .map(CouncilService::toLecturerOption)
                .toList();
        return new CreationWorkspace(
                period.getId(),
                period.getName(),
                period.getFaculty().getId(),
                period.getFaculty().getName(),
                lecturerOptions);
    }

    @Transactional(readOnly = true)
    public CouncilDetailView detailForCurrentDean(long councilId) {
        CouncilEntity council = councils.findDetailedById(councilId)
                .orElseThrow(() -> new ApplicationException(
                        ApplicationErrorCode.RESOURCE_NOT_FOUND));
        if (council.getMode() != CouncilMode.COUNCIL) {
            throw new ApplicationException(ApplicationErrorCode.RESOURCE_NOT_FOUND);
        }
        ActorIdentity actor = authorization.currentActor();
        long facultyId = requiredFacultyId(council.getPeriod());
        boolean scopedDean = actor.roles().contains(RoleCode.DEAN)
                && Objects.equals(actor.facultyId(), facultyId);
        boolean currentChair = actor.roles().contains(RoleCode.LECTURER)
                && Objects.equals(actor.facultyId(), facultyId)
                && Objects.equals(actor.accountId(), council.getChair().getId())
                && council.getStatus() == CouncilStatus.ACTIVE
                && members.existsByCouncilIdAndLecturerId(councilId, actor.accountId());
        if (!scopedDean && !currentChair) {
            throw new AccessDeniedException("Council is outside actor scope");
        }
        List<CouncilMemberView> memberViews = members
                .findAllByCouncilIdOrderBySlot(councilId)
                .stream()
                .map(member -> new CouncilMemberView(
                        member.getLecturer().getId(),
                        displayName(member.getLecturer()),
                        member.getLecturer().getUsername(),
                        member.getSlot(),
                        Objects.equals(member.getLecturer().getId(), council.getChair().getId())
                                ? "Chủ tịch"
                                : Objects.equals(member.getLecturer().getId(),
                                        council.getSecretary().getId())
                                        ? "Thư ký" : "Thành viên"))
                .toList();
        List<CouncilAssignmentView> assignmentViews = councilTopics
                .findAllByCouncilIdOrderByRegistrationId(councilId)
                .stream()
                .map(this::toAssignmentView)
                .toList();
        List<CouncilTopicProgressView> progress = assignmentViews.stream()
                .map(assignment -> topicProgress(councilId, assignment.registrationId(),
                        assignment.topicTitle(), memberViews))
                .toList();
        boolean manageable = scopedDean
                && CouncilRules.isSupportedTopicType(council.getPeriod().getTopicType());
        List<RegistrationOption> available = manageable
                ? availableRegistrations(council) : List.of();
        return new CouncilDetailView(
                toSummary(council),
                memberViews,
                assignmentViews,
                available,
                progress,
                currentChair,
                manageable,
                council.getChair().getId(),
                council.getSecretary().getId(),
                manageable && (council.getStatus() == CouncilStatus.DRAFT
                        || council.getStatus() == CouncilStatus.ASSIGNED)
                        && !evaluations.existsByCouncilId(councilId));
    }

    private LockedCouncil lockedCouncil(long councilId, long expectedVersion) {
        long periodId = councils.findPeriodIdByCouncilId(councilId)
                .orElseThrow(() -> new ApplicationException(
                        ApplicationErrorCode.RESOURCE_NOT_FOUND));
        RegistrationPeriodEntity period = lockedPeriod(periodId);
        authorization.requireDeanOfFaculty(requiredFacultyId(period));
        CouncilEntity council = councils.findByIdForUpdate(councilId)
                .orElseThrow(() -> new ApplicationException(
                        ApplicationErrorCode.RESOURCE_NOT_FOUND));
        if (!Objects.equals(council.getPeriod().getId(), periodId)) {
            throw new ApplicationException(ApplicationErrorCode.CONFLICT);
        }
        if (council.getVersion() != expectedVersion) {
            throw new OptimisticLockingFailureException("Council version is stale");
        }
        return new LockedCouncil(period, council);
    }

    private static void requireCurrentCouncilWorkflow(LockedCouncil locked) {
        if (locked.council().getMode() != CouncilMode.COUNCIL
                || !CouncilRules.isSupportedTopicType(locked.period().getTopicType())) {
            throw new ApplicationException(ApplicationErrorCode.INVALID_COUNCIL_STATE);
        }
    }

    private RegistrationPeriodEntity lockedPeriod(long periodId) {
        return periods.findByIdForUpdate(periodId)
                .orElseThrow(() -> new ApplicationException(
                        ApplicationErrorCode.RESOURCE_NOT_FOUND));
    }

    private List<RegistrationOption> availableRegistrations(CouncilEntity council) {
        return registrations.findAllByPeriodIdOrderBySubmittedAtDescIdDesc(
                        council.getPeriod().getId())
                .stream()
                .filter(registration -> registration.getStatus() == RegistrationStatus.ACCEPTED)
                .filter(registration -> councilTopics.findByRegistrationId(
                        registration.getId()).isEmpty())
                .map(registration -> new RegistrationOption(
                        registration.getId(),
                        registration.getGroup().getId(),
                        registration.getTopic().getTitle()))
                .toList();
    }

    private boolean activationConfigurationValid(
            RegistrationPeriodEntity period,
            CouncilEntity council,
            List<CouncilMemberEntity> currentMembers,
            List<CouncilTopicEntity> assignments) {
        if (!CouncilRules.isSupportedTopicType(period.getTopicType())) {
            return false;
        }
        try {
            validateCurrentMembers(period, council, currentMembers);
        } catch (CouncilRuleException exception) {
            return false;
        }
        return assignments.stream().allMatch(assignment -> {
            RegistrationEntity registration = assignment.getRegistration();
            return registration.getStatus() == RegistrationStatus.ACCEPTED
                    && Objects.equals(registration.getPeriod().getId(), period.getId())
                    && Objects.equals(registration.getTopic().getPeriod().getId(), period.getId())
                    && Objects.equals(
                            registration.getTopic().getPeriod().getFaculty().getId(),
                            council.getPeriod().getFaculty().getId());
        });
    }

    private static boolean hasSupervisorConflict(
            List<CouncilMemberEntity> currentMembers,
            RegistrationEntity registration) {
        var memberIds = currentMembers.stream()
                .map(CouncilMemberEntity::getLecturer)
                .map(UserAccountEntity::getId)
                .collect(java.util.stream.Collectors.toSet());
        return registration.getTopic().getSupervisors().stream()
                .map(TopicSupervisorEntity::getLecturer)
                .map(UserAccountEntity::getId)
                .anyMatch(memberIds::contains);
    }

    private boolean invalidReviewer(
            RegistrationEntity registration,
            List<CouncilMemberEntity> currentMembers) {
        ReviewerAssignmentEntity assignment = reviewerAssignments
                .findActiveByRegistrationIdForUpdate(registration.getId(), List.of(
                        ReviewerAssignmentEntity.Status.ASSIGNED,
                        ReviewerAssignmentEntity.Status.SUBMITTED))
                .orElse(null);
        if (assignment == null || assignment.getReviewer() == null) {
            return true;
        }
        UserAccountEntity reviewer = assignment.getReviewer();
        Long reviewerId = reviewer.getId();
        return !Objects.equals(assignment.getRegistration().getId(), registration.getId())
                || reviewerId == null
                || !reviewer.isEnabled()
                || !reviewer.getRoleCodes().contains(RoleCode.LECTURER)
                || reviewer.getRoleCodes().contains(RoleCode.DEAN)
                || reviewer.getFaculty() == null
                || !Objects.equals(reviewer.getFaculty().getId(),
                        registration.getPeriod().getFaculty().getId())
                || registration.getTopic().getSupervisors().stream()
                        .anyMatch(supervisor -> Objects.equals(
                                supervisor.getLecturer().getId(), reviewerId))
                || currentMembers.stream().anyMatch(member -> Objects.equals(
                        member.getLecturer().getId(), reviewerId));
    }

    private static void validateCurrentMembers(
            RegistrationPeriodEntity period,
            CouncilEntity council,
            List<CouncilMemberEntity> currentMembers) {
        CouncilRules.validateCreation(
                period.getTopicType(),
                period.getFaculty().getId(),
                council.getChair().getId(),
                council.getSecretary().getId(),
                currentMembers.stream()
                        .map(CouncilMemberEntity::getLecturer)
                        .map(CouncilService::candidate)
                        .toList());
    }

    private static boolean isScheduled(CouncilTopicEntity assignment) {
        return assignment.getScheduledAt() != null
                && assignment.getLocation() != null
                && !assignment.getLocation().isBlank();
    }

    private static MemberCandidate candidate(UserAccountEntity account) {
        Long facultyId = account.getFaculty() == null
                ? null : account.getFaculty().getId();
        return new MemberCandidate(
                account.getId(),
                facultyId,
                account.isEnabled(),
                account.getRoleCodes().contains(RoleCode.LECTURER)
                        && !account.getRoleCodes().contains(RoleCode.DEAN));
    }

    private List<Long> requestedMemberIds(CreateCouncilCommand command) {
        List<Long> ids = new ArrayList<>();
        ids.add(command.chairId());
        ids.add(command.secretaryId());
        if (command.memberIds() == null) {
            throw new ApplicationException(ApplicationErrorCode.INVALID_COUNCIL_MEMBERS);
        }
        ids.addAll(command.memberIds());
        if (ids.stream().anyMatch(Objects::isNull)
                || new LinkedHashSet<>(ids).size() != ids.size()) {
            throw new ApplicationException(ApplicationErrorCode.INVALID_COUNCIL_MEMBERS);
        }
        return List.copyOf(ids);
    }

    private UserAccountEntity detailedAccount(long id) {
        return accounts.findDetailedById(id)
                .orElseThrow(() -> new ApplicationException(
                        ApplicationErrorCode.INVALID_COUNCIL_MEMBERS));
    }

    private static UserAccountEntity findRequested(
            List<UserAccountEntity> lecturers,
            long id) {
        return lecturers.stream()
                .filter(account -> Objects.equals(account.getId(), id))
                .findFirst()
                .orElseThrow(() -> new ApplicationException(
                        ApplicationErrorCode.INVALID_COUNCIL_MEMBERS));
    }

    private static String normalizedName(String name) {
        String normalized = name == null ? null : name.strip();
        if (normalized == null || normalized.isEmpty() || normalized.length() > 255) {
            throw new ApplicationException(ApplicationErrorCode.VALIDATION_FAILED);
        }
        return normalized;
    }

    private ActorIdentity currentDean() {
        ActorIdentity dean = authorization.requireRole(RoleCode.DEAN);
        if (dean.facultyId() == null) {
            throw new AccessDeniedException("Dean faculty scope is missing");
        }
        return dean;
    }

    private static long requiredFacultyId(RegistrationPeriodEntity period) {
        if (period.getFaculty() == null || period.getFaculty().getId() == null) {
            throw new ApplicationException(ApplicationErrorCode.CONFLICT);
        }
        return period.getFaculty().getId();
    }

    private void forceVersionIncrement(CouncilEntity council) {
        entityManager.lock(council, LockModeType.PESSIMISTIC_FORCE_INCREMENT);
        entityManager.flush();
    }

    private static CouncilSummaryView toSummary(CouncilEntity council) {
        return new CouncilSummaryView(
                council.getId(),
                council.getPeriod().getId(),
                council.getPeriod().getName(),
                council.getPeriod().getFaculty().getId(),
                council.getPeriod().getFaculty().getName(),
                council.getName(),
                council.getStatus(),
                council.getVersion());
    }

    private static LecturerOption toLecturerOption(UserAccountEntity lecturer) {
        return new LecturerOption(
                lecturer.getId(), displayName(lecturer), lecturer.getUsername());
    }

    private CouncilAssignmentView toAssignmentView(CouncilTopicEntity assignment) {
        ReviewerAssignmentEntity reviewer = reviewerAssignments
                .findActiveByRegistrationId(assignment.getRegistration().getId(), List.of(
                        ReviewerAssignmentEntity.Status.ASSIGNED,
                        ReviewerAssignmentEntity.Status.SUBMITTED))
                .orElse(null);
        return new CouncilAssignmentView(
                assignment.getRegistration().getId(),
                assignment.getRegistration().getGroup().getId(),
                assignment.getRegistration().getTopic().getTitle(),
                assignment.getScheduledAt(),
                assignment.getLocation(),
                reviewer == null ? null : displayName(reviewer.getReviewer()),
                reviewer == null ? "Chưa phân công"
                        : reviewer.getStatus() == ReviewerAssignmentEntity.Status.SUBMITTED
                                ? "Đã nộp" : "Chưa nộp");
    }

    private CouncilTopicProgressView topicProgress(
            long councilId, long registrationId, String topicTitle,
            List<CouncilMemberView> memberViews) {
        Set<Long> studentIds = registrationMembers.findAllByRegistrationId(registrationId)
                .stream().map(snapshot -> snapshot.getStudent().getId())
                .collect(java.util.stream.Collectors.toSet());
        return new CouncilTopicProgressView(registrationId, topicTitle, memberViews.stream()
                .map(member -> memberProgress(councilId, registrationId, member,
                        studentIds)).toList());
    }

    private CouncilMemberProgressView memberProgress(
            long councilId, long registrationId, CouncilMemberView member,
            Set<Long> studentIds) {
        List<EvaluationEntity> rows = evaluations
                .findAllByCouncilIdAndRegistrationIdAndEvaluatorIdOrderByStudentId(
                        councilId, registrationId, member.id()).stream()
                .filter(row -> studentIds.contains(row.getStudent().getId()))
                .toList();
        List<EvaluationEntity> submitted = rows.stream()
                .filter(row -> row.getStatus() == EvaluationStatus.SUBMITTED)
                .toList();
        int submittedCount = (int) submitted.stream()
                .map(row -> row.getStudent().getId()).distinct().count();
        String status = !studentIds.isEmpty() && submittedCount == studentIds.size()
                ? "SUBMITTED" : rows.isEmpty() ? "NOT_STARTED" : "DRAFT";
        Instant submittedAt = "SUBMITTED".equals(status)
                ? submitted.stream().map(EvaluationEntity::getSubmittedAt)
                        .filter(Objects::nonNull).max(Instant::compareTo).orElse(null)
                : null;
        return new CouncilMemberProgressView(member.fullName(), member.role(),
                status, submittedCount, studentIds.size(), submittedAt);
    }

    private static String displayName(UserAccountEntity account) {
        return account.getFullName() == null || account.getFullName().isBlank()
                ? account.getUsername()
                : account.getFullName();
    }

    private static void applyRules(Runnable operation) {
        try {
            operation.run();
        } catch (CouncilRuleException exception) {
            throw switch (exception.failure()) {
                case INVALID_MEMBERS -> new ApplicationException(
                        ApplicationErrorCode.INVALID_COUNCIL_MEMBERS);
                case INVALID_ASSIGNMENT -> new ApplicationException(
                        ApplicationErrorCode.INVALID_COUNCIL_ASSIGNMENT);
                case INVALID_STATE -> new ApplicationException(
                        ApplicationErrorCode.INVALID_COUNCIL_STATE);
            };
        }
    }

    public record CreateCouncilCommand(
            long periodId,
            String name,
            long chairId,
            long secretaryId,
            List<Long> memberIds) {
        public CreateCouncilCommand {
            memberIds = memberIds == null
                    ? null
                    : java.util.Collections.unmodifiableList(new ArrayList<>(memberIds));
        }
    }

    public record AssignmentCommand(
            long registrationId,
            Instant scheduledAt,
            String location,
            long expectedVersion) {
    }

    public static final class InvalidScheduleException extends RuntimeException {
    }

    public record UpdateMembersCommand(
            long chairId,
            long secretaryId,
            List<Long> memberIds,
            long expectedVersion) {
        public UpdateMembersCommand {
            memberIds = memberIds == null ? null
                    : java.util.Collections.unmodifiableList(new ArrayList<>(memberIds));
        }
    }

    public record CouncilSummaryView(
            Long id,
            Long periodId,
            String periodName,
            Long facultyId,
            String facultyName,
            String name,
            CouncilStatus status,
            long version) {
    }

    public record LecturerOption(Long id, String fullName, String username) {
    }

    public record CouncilPeriodOption(Long id, String name, String topicType) {
    }

    public record CouncilCreationOptions(
            List<CouncilPeriodOption> periods) {
    }

    public record CreationWorkspace(
            Long periodId,
            String periodName,
            Long facultyId,
            String facultyName,
            List<LecturerOption> lecturers) {
        public CreationWorkspace {
            lecturers = List.copyOf(lecturers);
        }
    }

    public record CouncilMemberView(
            Long id,
            String fullName,
            String username,
            int slot,
            String role) {
    }

    public record CouncilAssignmentView(
            Long registrationId,
            Long groupId,
            String topicTitle,
            Instant scheduledAt,
            String location,
            String reviewerName,
            String reviewerStatus) {
        public CouncilAssignmentView(
                Long registrationId, Long groupId, String topicTitle,
                Instant scheduledAt, String location) {
            this(registrationId, groupId, topicTitle, scheduledAt, location,
                    null, "Chưa phân công");
        }
    }

    public record RegistrationOption(Long id, Long groupId, String topicTitle) {
    }

    public record CouncilDetailView(
            CouncilSummaryView council,
            List<CouncilMemberView> members,
            List<CouncilAssignmentView> assignments,
            List<RegistrationOption> availableRegistrations,
            List<CouncilTopicProgressView> progress,
            boolean canComplete,
            boolean canPublish,
            Long chairId,
            Long secretaryId,
            boolean canEditMembers) {
        public CouncilDetailView {
            members = List.copyOf(members);
            assignments = List.copyOf(assignments);
            availableRegistrations = List.copyOf(availableRegistrations);
            progress = List.copyOf(progress);
        }

        public CouncilDetailView(
                CouncilSummaryView council,
                List<CouncilMemberView> members,
                List<CouncilAssignmentView> assignments,
                List<RegistrationOption> availableRegistrations) {
            this(council, members, assignments, availableRegistrations, List.of(),
                    false, false, null, null, false);
        }
    }

    public record CouncilTopicProgressView(
            Long registrationId, String topicTitle,
            List<CouncilMemberProgressView> members) {
        public CouncilTopicProgressView {
            members = List.copyOf(members);
        }
    }

    public record CouncilMemberProgressView(
            String memberName, String role, String status,
            int submittedCount, int totalCount, Instant submittedAt) {
    }

    private record LockedCouncil(
            RegistrationPeriodEntity period,
            CouncilEntity council) {
    }
}
