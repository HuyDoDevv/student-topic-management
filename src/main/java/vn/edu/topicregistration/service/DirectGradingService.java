package vn.edu.topicregistration.service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import jakarta.persistence.EntityManager;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import vn.edu.topicregistration.entity.CouncilEntity;
import vn.edu.topicregistration.entity.CouncilMode;
import vn.edu.topicregistration.entity.CouncilStatus;
import vn.edu.topicregistration.entity.CouncilTopicEntity;
import vn.edu.topicregistration.entity.NotificationEventType;
import vn.edu.topicregistration.entity.NotificationTargetType;
import vn.edu.topicregistration.entity.RegistrationEntity;
import vn.edu.topicregistration.entity.RegistrationPeriodEntity;
import vn.edu.topicregistration.entity.RegistrationStatus;
import vn.edu.topicregistration.entity.RoleCode;
import vn.edu.topicregistration.entity.TopicSupervisorEntity;
import vn.edu.topicregistration.entity.UserAccountEntity;
import vn.edu.topicregistration.exception.ApplicationErrorCode;
import vn.edu.topicregistration.exception.ApplicationException;
import vn.edu.topicregistration.repository.CouncilRepository;
import vn.edu.topicregistration.repository.CouncilTopicRepository;
import vn.edu.topicregistration.repository.ProjectReportRepository;
import vn.edu.topicregistration.repository.RegistrationPeriodRepository;
import vn.edu.topicregistration.repository.RegistrationRepository;
import vn.edu.topicregistration.repository.UserAccountRepository;
import vn.edu.topicregistration.security.AuthorizationPolicy;
import vn.edu.topicregistration.security.AuthorizationPolicy.ActorIdentity;
import vn.edu.topicregistration.service.DirectGradingRules.AssignmentContext;
import vn.edu.topicregistration.service.DirectGradingRules.DirectGradingRuleException;
import vn.edu.topicregistration.service.NotificationService.EmissionCommand;

@Service
@Profile("!test")
public class DirectGradingService {
    private static final String DIRECT_LOCATION = "Chấm độc lập";

    private final RegistrationPeriodRepository periods;
    private final RegistrationRepository registrations;
    private final UserAccountRepository accounts;
    private final CouncilRepository councils;
    private final CouncilTopicRepository councilTopics;
    private final ProjectReportRepository reports;
    private final AuthorizationPolicy authorization;
    private final NotificationService notifications;
    private final EntityManager entityManager;
    private final Clock clock;

    public DirectGradingService(
            RegistrationPeriodRepository periods,
            RegistrationRepository registrations,
            UserAccountRepository accounts,
            CouncilRepository councils,
            CouncilTopicRepository councilTopics,
            ProjectReportRepository reports,
            AuthorizationPolicy authorization,
            NotificationService notifications,
            EntityManager entityManager,
            Clock clock) {
        this.periods = periods;
        this.registrations = registrations;
        this.accounts = accounts;
        this.councils = councils;
        this.councilTopics = councilTopics;
        this.reports = reports;
        this.authorization = authorization;
        this.notifications = notifications;
        this.entityManager = entityManager;
        this.clock = clock;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public DirectAssignmentView assign(AssignCommand command) {
        Objects.requireNonNull(command, "command");
        long periodId = registrations.findPeriodIdByRegistrationId(command.registrationId())
                .orElseThrow(() -> new ApplicationException(
                        ApplicationErrorCode.RESOURCE_NOT_FOUND));
        RegistrationPeriodEntity period = periods.findByIdForUpdate(periodId)
                .orElseThrow(() -> new ApplicationException(
                        ApplicationErrorCode.RESOURCE_NOT_FOUND));
        ActorIdentity dean = authorization.requireDeanOfFaculty(requiredFacultyId(period));
        RegistrationEntity registration = registrations
                .findByIdForUpdate(command.registrationId())
                .orElseThrow(() -> new ApplicationException(
                        ApplicationErrorCode.RESOURCE_NOT_FOUND));
        if (!Objects.equals(registration.getPeriod().getId(), period.getId())) {
            throw new ApplicationException(ApplicationErrorCode.CONFLICT);
        }

        boolean reportPresent = reports
                .findCurrentByRegistrationIdForUpdate(command.registrationId())
                .isPresent();
        boolean alreadyAssigned = councilTopics
                .findByRegistrationId(command.registrationId())
                .isPresent();
        UserAccountEntity grader = accounts.findByIdForUpdate(command.graderId())
                .orElseThrow(() -> new ApplicationException(
                        ApplicationErrorCode.INVALID_COUNCIL_ASSIGNMENT));
        if (!Objects.equals(
                registration.getTopic().getPeriod().getFaculty().getId(),
                period.getFaculty().getId())) {
            throw new ApplicationException(ApplicationErrorCode.CONFLICT);
        }
        boolean sameFaculty = grader.getFaculty() != null
                && Objects.equals(grader.getFaculty().getId(), period.getFaculty().getId());
        boolean supervisor = isSupervisor(registration, grader.getId());

        applyRules(new AssignmentContext(
                period.getTopicType(),
                registration.getStatus(),
                reportPresent,
                alreadyAssigned,
                grader.isEnabled(),
                grader.getRoleCodes().contains(RoleCode.LECTURER),
                sameFaculty,
                supervisor));

        Instant assignedAt = clock.instant();
        CouncilEntity directAssignment = councils.saveAndFlush(
                CouncilEntity.directGrading(
                        period,
                        directName(registration),
                        grader,
                        assignedAt));
        CouncilTopicEntity assignment = new CouncilTopicEntity(
                directAssignment, registration);
        assignment.schedule(assignedAt, DIRECT_LOCATION);
        councilTopics.saveAndFlush(assignment);

        directAssignment.transitionTo(CouncilStatus.ASSIGNED);
        directAssignment.transitionTo(CouncilStatus.SCHEDULED);
        directAssignment.transitionTo(CouncilStatus.ACTIVE);
        councils.flush();
        entityManager.flush();
        notifications.emit(new EmissionCommand(
                NotificationEventType.DIRECT_GRADER_ASSIGNED,
                directAssignment.getId(),
                dean.accountId(),
                List.of(grader.getId()),
                "Bạn được phân công chấm đề tài",
                "Trưởng khoa đã phân công bạn chấm trực tiếp một đề tài.",
                null,
                NotificationTargetType.DIRECT_GRADING,
                directAssignment.getId()));
        return toView(directAssignment, assignment);
    }

    @Transactional(readOnly = true)
    public DirectGradingWorkspace workspaceForCurrentDean() {
        ActorIdentity dean = currentDean();
        List<AvailableRegistrationView> available = new ArrayList<>();
        for (RegistrationPeriodEntity period : periods
                .findAllByFacultyIdOrderByLecturerStartDesc(dean.facultyId())) {
            if (!DirectGradingRules.isSupportedTopicType(period.getTopicType())) {
                continue;
            }
            for (RegistrationEntity registration : registrations
                    .findAllByPeriodIdOrderBySubmittedAtDescIdDesc(period.getId())) {
                if (registration.getStatus() != RegistrationStatus.ACCEPTED
                        || reports.findByRegistrationIdAndReplacedAtIsNull(
                                registration.getId()).isEmpty()
                        || councilTopics.findByRegistrationId(registration.getId()).isPresent()) {
                    continue;
                }
                List<LecturerOption> graders = eligibleGraders(registration);
                if (!graders.isEmpty()) {
                    available.add(toAvailable(period, registration, graders));
                }
            }
        }

        List<DirectAssignmentView> existing = councils
                .findAllByPeriodFacultyIdAndModeOrderByCreatedAtDescIdDesc(
                        dean.facultyId(), CouncilMode.DIRECT)
                .stream()
                .map(this::existingAssignment)
                .toList();
        return new DirectGradingWorkspace(available, existing);
    }

    private DirectAssignmentView existingAssignment(CouncilEntity council) {
        List<CouncilTopicEntity> assignments = councilTopics
                .findAllByCouncilIdOrderByRegistrationId(council.getId());
        if (assignments.size() != 1 || council.getMode() != CouncilMode.DIRECT) {
            throw new ApplicationException(ApplicationErrorCode.CONFLICT);
        }
        return toView(council, assignments.getFirst());
    }

    private List<LecturerOption> eligibleGraders(RegistrationEntity registration) {
        Set<Long> supervisorIds = registration.getTopic().getSupervisors().stream()
                .map(TopicSupervisorEntity::getLecturer)
                .map(UserAccountEntity::getId)
                .collect(java.util.stream.Collectors.toSet());
        return accounts.findEnabledByFacultyAndRole(
                        registration.getPeriod().getFaculty().getId(),
                        RoleCode.LECTURER)
                .stream()
                .filter(account -> !supervisorIds.contains(account.getId()))
                .map(account -> new LecturerOption(
                        account.getId(), displayName(account), account.getUsername()))
                .toList();
    }

    private static AvailableRegistrationView toAvailable(
            RegistrationPeriodEntity period,
            RegistrationEntity registration,
            List<LecturerOption> graders) {
        return new AvailableRegistrationView(
                registration.getId(),
                registration.getGroup().getId(),
                registration.getTopic().getTitle(),
                period.getName(),
                period.getTopicType(),
                period.getFaculty().getName(),
                graders);
    }

    private static DirectAssignmentView toView(
            CouncilEntity council,
            CouncilTopicEntity assignment) {
        RegistrationEntity registration = assignment.getRegistration();
        return new DirectAssignmentView(
                council.getId(),
                registration.getId(),
                registration.getGroup().getId(),
                council.getPeriod().getId(),
                registration.getTopic().getTitle(),
                council.getPeriod().getName(),
                council.getPeriod().getTopicType(),
                council.getPeriod().getFaculty().getName(),
                council.getChair().getId(),
                displayName(council.getChair()),
                council.getStatus(),
                council.getVersion());
    }

    private static boolean isSupervisor(RegistrationEntity registration, Long graderId) {
        return registration.getTopic().getSupervisors().stream()
                .map(TopicSupervisorEntity::getLecturer)
                .map(UserAccountEntity::getId)
                .anyMatch(id -> Objects.equals(id, graderId));
    }

    private static String directName(RegistrationEntity registration) {
        String value = "Chấm độc lập · " + registration.getTopic().getTitle().strip();
        return value.length() <= 255 ? value : value.substring(0, 255);
    }

    private ActorIdentity currentDean() {
        ActorIdentity dean = authorization.requireRole(RoleCode.DEAN);
        if (dean.accountId() == null || dean.facultyId() == null) {
            throw new AccessDeniedException("Dean scope is missing");
        }
        return dean;
    }

    private static long requiredFacultyId(RegistrationPeriodEntity period) {
        if (period.getFaculty() == null || period.getFaculty().getId() == null) {
            throw new ApplicationException(ApplicationErrorCode.CONFLICT);
        }
        return period.getFaculty().getId();
    }

    private static String displayName(UserAccountEntity account) {
        return account.getFullName() == null || account.getFullName().isBlank()
                ? account.getUsername()
                : account.getFullName();
    }

    private static void applyRules(AssignmentContext context) {
        try {
            DirectGradingRules.validate(context);
        } catch (DirectGradingRuleException exception) {
            throw switch (exception.failure()) {
                case UNSUPPORTED_TYPE -> new ApplicationException(
                        ApplicationErrorCode.INVALID_COUNCIL_STATE);
                case INVALID_REGISTRATION, REPORT_REQUIRED, ALREADY_ASSIGNED,
                        INVALID_GRADER, SUPERVISOR_CONFLICT -> new ApplicationException(
                                ApplicationErrorCode.INVALID_COUNCIL_ASSIGNMENT);
            };
        }
    }

    public record AssignCommand(long registrationId, long graderId) {
    }

    public record LecturerOption(Long id, String fullName, String username) {
    }

    public record AvailableRegistrationView(
            Long registrationId,
            Long groupId,
            String topicTitle,
            String periodName,
            String topicType,
            String facultyName,
            List<LecturerOption> eligibleGraders) {
        public AvailableRegistrationView {
            eligibleGraders = List.copyOf(eligibleGraders);
        }
    }

    public record DirectAssignmentView(
            Long id,
            Long registrationId,
            Long groupId,
            Long periodId,
            String topicTitle,
            String periodName,
            String topicType,
            String facultyName,
            Long graderId,
            String graderName,
            CouncilStatus status,
            long version) {
    }

    public record DirectGradingWorkspace(
            List<AvailableRegistrationView> availableRegistrations,
            List<DirectAssignmentView> assignments) {
        public DirectGradingWorkspace {
            availableRegistrations = List.copyOf(availableRegistrations);
            assignments = List.copyOf(assignments);
        }
    }
}
