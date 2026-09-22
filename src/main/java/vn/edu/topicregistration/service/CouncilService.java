package vn.edu.topicregistration.service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

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
import vn.edu.topicregistration.entity.DepartmentEntity;
import vn.edu.topicregistration.entity.RegistrationEntity;
import vn.edu.topicregistration.entity.RegistrationPeriodEntity;
import vn.edu.topicregistration.entity.RegistrationStatus;
import vn.edu.topicregistration.entity.RoleCode;
import vn.edu.topicregistration.entity.TopicSupervisorEntity;
import vn.edu.topicregistration.entity.UserAccountEntity;
import vn.edu.topicregistration.exception.ApplicationErrorCode;
import vn.edu.topicregistration.exception.ApplicationException;
import vn.edu.topicregistration.repository.CouncilMemberRepository;
import vn.edu.topicregistration.repository.CouncilRepository;
import vn.edu.topicregistration.repository.CouncilTopicRepository;
import vn.edu.topicregistration.repository.DepartmentRepository;
import vn.edu.topicregistration.repository.ProjectReportRepository;
import vn.edu.topicregistration.repository.RegistrationPeriodRepository;
import vn.edu.topicregistration.repository.RegistrationRepository;
import vn.edu.topicregistration.repository.UserAccountRepository;
import vn.edu.topicregistration.security.AuthorizationPolicy;
import vn.edu.topicregistration.security.AuthorizationPolicy.ActorIdentity;
import vn.edu.topicregistration.service.CouncilRules.AssignmentContext;
import vn.edu.topicregistration.service.CouncilRules.CouncilRuleException;
import vn.edu.topicregistration.service.CouncilRules.MemberCandidate;
import vn.edu.topicregistration.service.CouncilRules.TransitionContext;

@Service
@Profile("!preview & !test")
public class CouncilService {
    private final RegistrationPeriodRepository periods;
    private final DepartmentRepository departments;
    private final UserAccountRepository accounts;
    private final CouncilRepository councils;
    private final CouncilMemberRepository members;
    private final CouncilTopicRepository councilTopics;
    private final RegistrationRepository registrations;
    private final ProjectReportRepository reports;
    private final AuthorizationPolicy authorization;
    private final EntityManager entityManager;
    private final Clock clock;

    public CouncilService(
            RegistrationPeriodRepository periods,
            DepartmentRepository departments,
            UserAccountRepository accounts,
            CouncilRepository councils,
            CouncilMemberRepository members,
            CouncilTopicRepository councilTopics,
            RegistrationRepository registrations,
            ProjectReportRepository reports,
            AuthorizationPolicy authorization,
            EntityManager entityManager,
            Clock clock) {
        this.periods = periods;
        this.departments = departments;
        this.accounts = accounts;
        this.councils = councils;
        this.members = members;
        this.councilTopics = councilTopics;
        this.registrations = registrations;
        this.reports = reports;
        this.authorization = authorization;
        this.entityManager = entityManager;
        this.clock = clock;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public CouncilSummaryView create(CreateCouncilCommand command) {
        Objects.requireNonNull(command, "command");
        RegistrationPeriodEntity period = lockedPeriod(command.periodId());
        long facultyId = requiredFacultyId(period);
        authorization.requireDeanOfFaculty(facultyId);
        DepartmentEntity department = departments
                .findByIdAndFacultyId(command.departmentId(), facultyId)
                .orElseThrow(() -> new AccessDeniedException("Department is outside faculty"));

        String name = normalizedName(command.name());
        List<Long> requestedIds = requestedMemberIds(command);
        List<UserAccountEntity> lecturers = requestedIds.stream()
                .map(this::detailedAccount)
                .toList();
        applyRules(() -> CouncilRules.validateCreation(
                period.getTopicType(),
                department.getId(),
                command.chairId(),
                command.secretaryId(),
                lecturers.stream().map(CouncilService::candidate).toList()));

        UserAccountEntity chair = findRequested(lecturers, command.chairId());
        UserAccountEntity secretary = findRequested(lecturers, command.secretaryId());
        CouncilEntity council = councils.saveAndFlush(new CouncilEntity(
                period, department, name, chair, secretary, clock.instant()));

        List<CouncilMemberEntity> additionalMembers = new ArrayList<>();
        int slot = 3;
        for (Long memberId : command.memberIds()) {
            additionalMembers.add(new CouncilMemberEntity(
                    council, findRequested(lecturers, memberId), slot++));
        }
        members.saveAll(additionalMembers);
        members.flush();
        return toSummary(council);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public CouncilSummaryView saveAssignment(long councilId, AssignmentCommand command) {
        Objects.requireNonNull(command, "command");
        LockedCouncil locked = lockedCouncil(councilId, command.expectedVersion());
        CouncilEntity council = locked.council();

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

        applyRules(() -> CouncilRules.validateAssignment(new AssignmentContext(
                council.getStatus(),
                locked.period().getTopicType(),
                registration.getStatus(),
                Objects.equals(registration.getPeriod().getId(), locked.period().getId()),
                Objects.equals(
                        registration.getTopic().getDepartment().getId(),
                        council.getDepartment().getId()),
                assignedElsewhere,
                supervisorConflict)));

        String location = command.location() == null ? null : command.location().strip();
        if (command.scheduledAt() == null || location == null || location.isEmpty()
                || location.length() > 500) {
            throw new ApplicationException(ApplicationErrorCode.INVALID_COUNCIL_ASSIGNMENT);
        }
        CouncilTopicEntity assignment = existing.orElseGet(
                () -> new CouncilTopicEntity(council, registration));
        assignment.schedule(command.scheduledAt(), location);
        councilTopics.saveAndFlush(assignment);
        forceVersionIncrement(council);
        return toSummary(council);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public CouncilSummaryView transition(
            long councilId,
            long expectedVersion,
            CouncilStatus target) {
        LockedCouncil locked = lockedCouncil(councilId, expectedVersion);
        CouncilEntity council = locked.council();
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
        List<CouncilDepartmentOption> departmentOptions = departments
                .findAllByFacultyIdOrderByName(dean.facultyId())
                .stream()
                .map(department -> new CouncilDepartmentOption(
                        department.getId(), department.getName()))
                .toList();
        return new CouncilCreationOptions(periodOptions, departmentOptions);
    }

    @Transactional(readOnly = true)
    public CreationWorkspace creationWorkspace(long periodId, long departmentId) {
        RegistrationPeriodEntity period = periods.findById(periodId)
                .orElseThrow(() -> new ApplicationException(
                        ApplicationErrorCode.RESOURCE_NOT_FOUND));
        long facultyId = requiredFacultyId(period);
        authorization.requireDeanOfFaculty(facultyId);
        if (!CouncilRules.isSupportedTopicType(period.getTopicType())) {
            throw new ApplicationException(ApplicationErrorCode.INVALID_COUNCIL_STATE);
        }
        DepartmentEntity department = departments.findByIdAndFacultyId(departmentId, facultyId)
                .orElseThrow(() -> new AccessDeniedException("Department is outside faculty"));
        List<LecturerOption> lecturerOptions = accounts
                .findEnabledByDepartmentAndRole(departmentId, RoleCode.LECTURER)
                .stream()
                .map(CouncilService::toLecturerOption)
                .toList();
        return new CreationWorkspace(
                period.getId(),
                period.getName(),
                department.getId(),
                department.getName(),
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
                && Objects.equals(actor.accountId(), council.getChair().getId());
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
                        member.getSlot() == 1 ? "Chủ tịch"
                                : member.getSlot() == 2 ? "Thư ký" : "Thành viên"))
                .toList();
        List<CouncilAssignmentView> assignmentViews = councilTopics
                .findAllByCouncilIdOrderByRegistrationId(councilId)
                .stream()
                .map(CouncilService::toAssignmentView)
                .toList();
        List<RegistrationOption> available = scopedDean
                ? availableRegistrations(council) : List.of();
        return new CouncilDetailView(
                toSummary(council),
                memberViews,
                assignmentViews,
                available,
                currentChair,
                scopedDean);
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
        if (!Objects.equals(
                council.getDepartment().getFaculty().getId(),
                period.getFaculty().getId())) {
            throw new ApplicationException(ApplicationErrorCode.CONFLICT);
        }
        if (council.getVersion() != expectedVersion) {
            throw new OptimisticLockingFailureException("Council version is stale");
        }
        return new LockedCouncil(period, council);
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
                .filter(registration -> Objects.equals(
                        registration.getTopic().getDepartment().getId(),
                        council.getDepartment().getId()))
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
        if (!CouncilRules.isSupportedTopicType(period.getTopicType())
                || !Objects.equals(council.getDepartment().getFaculty().getId(),
                        period.getFaculty().getId())) {
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
                    && Objects.equals(
                            registration.getTopic().getDepartment().getId(),
                            council.getDepartment().getId());
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

    private static void validateCurrentMembers(
            RegistrationPeriodEntity period,
            CouncilEntity council,
            List<CouncilMemberEntity> currentMembers) {
        CouncilRules.validateCreation(
                period.getTopicType(),
                council.getDepartment().getId(),
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
        Long departmentId = account.getDepartment() == null
                ? null : account.getDepartment().getId();
        return new MemberCandidate(
                account.getId(),
                departmentId,
                account.isEnabled(),
                account.getRoleCodes().contains(RoleCode.LECTURER));
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
                council.getDepartment().getId(),
                council.getDepartment().getName(),
                council.getName(),
                council.getStatus(),
                council.getVersion());
    }

    private static LecturerOption toLecturerOption(UserAccountEntity lecturer) {
        return new LecturerOption(
                lecturer.getId(), displayName(lecturer), lecturer.getUsername());
    }

    private static CouncilAssignmentView toAssignmentView(CouncilTopicEntity assignment) {
        return new CouncilAssignmentView(
                assignment.getRegistration().getId(),
                assignment.getRegistration().getGroup().getId(),
                assignment.getRegistration().getTopic().getTitle(),
                assignment.getScheduledAt(),
                assignment.getLocation());
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
            long departmentId,
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

    public record CouncilSummaryView(
            Long id,
            Long periodId,
            String periodName,
            Long departmentId,
            String departmentName,
            String name,
            CouncilStatus status,
            long version) {
    }

    public record LecturerOption(Long id, String fullName, String username) {
    }

    public record CouncilPeriodOption(Long id, String name, String topicType) {
    }

    public record CouncilDepartmentOption(Long id, String name) {
    }

    public record CouncilCreationOptions(
            List<CouncilPeriodOption> periods,
            List<CouncilDepartmentOption> departments) {
    }

    public record CreationWorkspace(
            Long periodId,
            String periodName,
            Long departmentId,
            String departmentName,
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
            String location) {
    }

    public record RegistrationOption(Long id, Long groupId, String topicTitle) {
    }

    public record CouncilDetailView(
            CouncilSummaryView council,
            List<CouncilMemberView> members,
            List<CouncilAssignmentView> assignments,
            List<RegistrationOption> availableRegistrations,
            boolean canComplete,
            boolean canPublish) {
        public CouncilDetailView {
            members = List.copyOf(members);
            assignments = List.copyOf(assignments);
            availableRegistrations = List.copyOf(availableRegistrations);
        }

        public CouncilDetailView(
                CouncilSummaryView council,
                List<CouncilMemberView> members,
                List<CouncilAssignmentView> assignments,
                List<RegistrationOption> availableRegistrations) {
            this(council, members, assignments, availableRegistrations, false, false);
        }
    }

    private record LockedCouncil(
            RegistrationPeriodEntity period,
            CouncilEntity council) {
    }
}
