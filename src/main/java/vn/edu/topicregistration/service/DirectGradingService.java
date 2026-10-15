package vn.edu.topicregistration.service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.HashSet;

import jakarta.persistence.EntityManager;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.dao.OptimisticLockingFailureException;
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
import vn.edu.topicregistration.entity.ReviewerAssignmentEntity;
import vn.edu.topicregistration.entity.ReviewerEvaluationEntity;
import vn.edu.topicregistration.entity.EvaluationStatus;
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
import vn.edu.topicregistration.repository.RegistrationMemberRepository;
import vn.edu.topicregistration.repository.ReviewerAssignmentRepository;
import vn.edu.topicregistration.repository.ReviewerEvaluationRepository;
import vn.edu.topicregistration.repository.ReviewerReportRepository;
import vn.edu.topicregistration.repository.UserAccountRepository;
import vn.edu.topicregistration.security.AuthorizationPolicy;
import vn.edu.topicregistration.security.AuthorizationPolicy.ActorIdentity;
import vn.edu.topicregistration.service.DirectGradingRules.AssignmentContext;
import vn.edu.topicregistration.service.DirectGradingRules.DirectGradingRuleException;
import vn.edu.topicregistration.service.NotificationService.EmissionCommand;
import vn.edu.topicregistration.util.ProjectTime;

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
    private final RegistrationMemberRepository registrationMembers;
    private final ReviewerAssignmentRepository reviewerAssignments;
    private final ReviewerEvaluationRepository reviewerEvaluations;
    private final ReviewerReportRepository reviewerReports;
    private final AuthorizationPolicy authorization;
    private final NotificationService notifications;
    private final EntityManager entityManager;
    private final Clock clock;
    private final ProjectTime time;

    public DirectGradingService(
            RegistrationPeriodRepository periods,
            RegistrationRepository registrations,
            UserAccountRepository accounts,
            CouncilRepository councils,
            CouncilTopicRepository councilTopics,
            ProjectReportRepository reports,
            RegistrationMemberRepository registrationMembers,
            ReviewerAssignmentRepository reviewerAssignments,
            ReviewerEvaluationRepository reviewerEvaluations,
            ReviewerReportRepository reviewerReports,
            AuthorizationPolicy authorization,
            NotificationService notifications,
            EntityManager entityManager,
            Clock clock,
            ProjectTime time) {
        this.periods = periods;
        this.registrations = registrations;
        this.accounts = accounts;
        this.councils = councils;
        this.councilTopics = councilTopics;
        this.reports = reports;
        this.registrationMembers = registrationMembers;
        this.reviewerAssignments = reviewerAssignments;
        this.reviewerEvaluations = reviewerEvaluations;
        this.reviewerReports = reviewerReports;
        this.authorization = authorization;
        this.notifications = notifications;
        this.entityManager = entityManager;
        this.clock = clock;
        this.time = time;
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
        if (!Objects.equals(registration.getTopic().getPeriod().getId(), period.getId())) {
            throw new ApplicationException(ApplicationErrorCode.INVALID_COUNCIL_ASSIGNMENT);
        }
        if (registration.getVersion() != command.version()) {
            throw new OptimisticLockingFailureException("Registration version is stale");
        }

        boolean tlcn = "TLCN".equals(period.getTopicType());
        ReviewerAssignmentEntity reviewer = tlcn
                ? reviewerAssignments.findActiveByRegistrationIdForUpdate(
                        command.registrationId(), List.of(
                                ReviewerAssignmentEntity.Status.ASSIGNED,
                                ReviewerAssignmentEntity.Status.SUBMITTED))
                        .orElse(null)
                : null;
        boolean reportPresent = tlcn
                ? reviewerComplete(registration, reviewer, true)
                : reports.findCurrentByRegistrationIdForUpdate(command.registrationId())
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
        boolean reviewerConflict = tlcn && reviewer != null
                && Objects.equals(reviewer.getReviewer().getId(), grader.getId());

        applyRules(new AssignmentContext(
                period.getTopicType(),
                registration.getStatus(),
                reportPresent,
                alreadyAssigned,
                grader.isEnabled(),
                grader.getRoleCodes().contains(RoleCode.LECTURER)
                        && !grader.getRoleCodes().contains(RoleCode.DEAN),
                sameFaculty,
                supervisor || reviewerConflict));

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
        List<BlockedRegistrationView> blocked = new ArrayList<>();
        for (RegistrationPeriodEntity period : periods
                .findAllByFacultyIdOrderByLecturerStartDesc(dean.facultyId())) {
            if (!DirectGradingRules.isSupportedTopicType(period.getTopicType())) {
                continue;
            }
            for (RegistrationEntity registration : registrations
                    .findAllByPeriodIdOrderBySubmittedAtDescIdDesc(period.getId())) {
                boolean tlcn = "TLCN".equals(period.getTopicType());
                ReviewerAssignmentEntity reviewer = tlcn
                        ? reviewerAssignments.findActiveByRegistrationId(
                                registration.getId(), List.of(
                                        ReviewerAssignmentEntity.Status.ASSIGNED,
                                        ReviewerAssignmentEntity.Status.SUBMITTED))
                                .orElse(null)
                        : null;
                if (registration.getStatus() != RegistrationStatus.ACCEPTED
                        || councilTopics.findByRegistrationId(registration.getId()).isPresent()) {
                    continue;
                }
                boolean sourceReady = tlcn ? reviewerComplete(registration, reviewer, false)
                        : reports.findByRegistrationIdAndReplacedAtIsNull(
                                registration.getId()).isPresent();
                if (!sourceReady) {
                    if (tlcn) {
                        blocked.add(new BlockedRegistrationView(
                                registration.getId(), registration.getTopic().getTitle(),
                                "Chờ GVPB nộp PDF và đủ điểm đúng hạn cho mọi sinh viên."));
                    }
                    continue;
                }
                List<LecturerOption> graders = eligibleGraders(
                        registration, reviewer == null ? null : reviewer.getReviewer().getId());
                if (!graders.isEmpty()) {
                    available.add(toAvailable(period, registration, graders));
                } else if (tlcn) {
                    blocked.add(new BlockedRegistrationView(
                            registration.getId(), registration.getTopic().getTitle(),
                            "Không còn giảng viên đủ điều kiện chấm lần hai trong Khoa."));
                }
            }
        }

        List<DirectAssignmentView> existing = councils
                .findAllByPeriodFacultyIdAndModeOrderByCreatedAtDescIdDesc(
                        dean.facultyId(), CouncilMode.DIRECT)
                .stream()
                .map(this::existingAssignment)
                .toList();
        return new DirectGradingWorkspace(available, existing, blocked);
    }

    private DirectAssignmentView existingAssignment(CouncilEntity council) {
        List<CouncilTopicEntity> assignments = councilTopics
                .findAllByCouncilIdOrderByRegistrationId(council.getId());
        if (assignments.size() != 1 || council.getMode() != CouncilMode.DIRECT) {
            throw new ApplicationException(ApplicationErrorCode.CONFLICT);
        }
        return toView(council, assignments.getFirst());
    }

    private List<LecturerOption> eligibleGraders(
            RegistrationEntity registration, Long reviewerId) {
        Set<Long> supervisorIds = registration.getTopic().getSupervisors().stream()
                .map(TopicSupervisorEntity::getLecturer)
                .map(UserAccountEntity::getId)
                .collect(java.util.stream.Collectors.toSet());
        return accounts.findEnabledByFacultyAndRole(
                        registration.getPeriod().getFaculty().getId(),
                        RoleCode.LECTURER)
                .stream()
                .filter(account -> account.getRoleCodes().contains(RoleCode.LECTURER)
                        && !account.getRoleCodes().contains(RoleCode.DEAN))
                .filter(account -> !supervisorIds.contains(account.getId()))
                .filter(account -> !Objects.equals(account.getId(), reviewerId))
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
                registration.getVersion(),
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

    private boolean reviewerComplete(
            RegistrationEntity registration,
            ReviewerAssignmentEntity reviewer,
            boolean lockScores) {
        if (reviewer == null
                || reviewer.getStatus() != ReviewerAssignmentEntity.Status.SUBMITTED
                || reviewer.getSubmittedAt() == null
                || reviewer.getDeadlineDate() == null
                || reviewer.getSubmittedAt().isAfter(time.toEndOfDay(reviewer.getDeadlineDate()))
                || reviewerReports.findByAssignmentId(reviewer.getId())
                        .filter(report -> !report.getSubmittedAt().isAfter(
                                time.toEndOfDay(reviewer.getDeadlineDate())))
                        .isEmpty()) {
            return false;
        }
        Set<Long> expected = new HashSet<>();
        registrationMembers.findAllByRegistrationId(registration.getId())
                .forEach(member -> expected.add(member.getStudent().getId()));
        if (expected.isEmpty()) {
            return false;
        }
        List<ReviewerEvaluationEntity> scores = lockScores
                ? reviewerEvaluations.findAllByAssignmentIdForUpdate(reviewer.getId())
                : reviewerEvaluations.findAllByAssignmentIdOrderByStudentId(reviewer.getId());
        Set<Long> actual = new HashSet<>();
        for (ReviewerEvaluationEntity score : scores) {
            if (score.getStatus() != EvaluationStatus.SUBMITTED
                    || score.getSubmittedAt() == null
                    || score.getSubmittedAt().isAfter(time.toEndOfDay(reviewer.getDeadlineDate()))
                    || !Objects.equals(score.getRegistration().getId(), registration.getId())
                    || !actual.add(score.getStudent().getId())) {
                return false;
            }
        }
        return expected.equals(actual);
    }

    void requireSubmittedReviewerForScoring(RegistrationEntity registration) {
        ReviewerAssignmentEntity reviewer = reviewerAssignments
                .findActiveByRegistrationIdForUpdate(
                        registration.getId(), List.of(
                                ReviewerAssignmentEntity.Status.SUBMITTED))
                .orElse(null);
        if (!reviewerComplete(registration, reviewer, true)) {
            throw new ApplicationException(ApplicationErrorCode.EVALUATIONS_INCOMPLETE);
        }
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

    public record AssignCommand(long registrationId, long graderId, long version) {
        public AssignCommand(long registrationId, long graderId) {
            this(registrationId, graderId, 0);
        }
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
            long version,
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
            List<DirectAssignmentView> assignments,
            List<BlockedRegistrationView> blockedRegistrations) {
        public DirectGradingWorkspace {
            availableRegistrations = List.copyOf(availableRegistrations);
            assignments = List.copyOf(assignments);
            blockedRegistrations = List.copyOf(blockedRegistrations);
        }
        public DirectGradingWorkspace(
                List<AvailableRegistrationView> availableRegistrations,
                List<DirectAssignmentView> assignments) {
            this(availableRegistrations, assignments, List.of());
        }
    }

    public record BlockedRegistrationView(Long registrationId, String topicTitle, String reason) {
    }
}
