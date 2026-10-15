package vn.edu.topicregistration.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.springframework.context.annotation.Profile;
import org.springframework.context.annotation.Lazy;
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
import vn.edu.topicregistration.entity.RegistrationEntity;
import vn.edu.topicregistration.entity.RegistrationMemberEntity;
import vn.edu.topicregistration.entity.RegistrationPeriodEntity;
import vn.edu.topicregistration.entity.RegistrationStatus;
import vn.edu.topicregistration.entity.RoleCode;
import vn.edu.topicregistration.entity.ScoreComponentEntity;
import vn.edu.topicregistration.entity.TopicSupervisorEntity;
import vn.edu.topicregistration.entity.UserAccountEntity;
import vn.edu.topicregistration.exception.ApplicationErrorCode;
import vn.edu.topicregistration.exception.ApplicationException;
import vn.edu.topicregistration.repository.CouncilMemberRepository;
import vn.edu.topicregistration.repository.CouncilRepository;
import vn.edu.topicregistration.repository.CouncilTopicRepository;
import vn.edu.topicregistration.repository.EvaluationRepository;
import vn.edu.topicregistration.repository.RegistrationPeriodRepository;
import vn.edu.topicregistration.repository.RegistrationMemberRepository;
import vn.edu.topicregistration.repository.ScoreComponentRepository;
import vn.edu.topicregistration.security.AuthorizationPolicy;
import vn.edu.topicregistration.security.AuthorizationPolicy.ActorIdentity;
import vn.edu.topicregistration.service.ScoreRules.ScoreContext;
import vn.edu.topicregistration.service.ScoreRules.ScoreRuleException;
import vn.edu.topicregistration.util.ScoreFormat;

@Service
@Profile("!test")
public class ScoringService {
    private final RegistrationPeriodRepository periods;
    private final CouncilRepository councils;
    private final CouncilTopicRepository councilTopics;
    private final CouncilMemberRepository members;
    private final RegistrationMemberRepository registrationMembers;
    private final ScoreComponentRepository components;
    private final EvaluationRepository evaluations;
    private final DirectGradingService directGrading;
    private final AuthorizationPolicy authorization;
    private final Clock clock;

    public ScoringService(
            RegistrationPeriodRepository periods,
            CouncilRepository councils,
            CouncilTopicRepository councilTopics,
            CouncilMemberRepository members,
            RegistrationMemberRepository registrationMembers,
            ScoreComponentRepository components,
            EvaluationRepository evaluations,
            @Lazy DirectGradingService directGrading,
            AuthorizationPolicy authorization,
            Clock clock) {
        this.periods = periods;
        this.councils = councils;
        this.councilTopics = councilTopics;
        this.members = members;
        this.registrationMembers = registrationMembers;
        this.components = components;
        this.evaluations = evaluations;
        this.directGrading = directGrading;
        this.authorization = authorization;
        this.clock = clock;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public EvaluationView saveDraft(
            long councilId,
            long registrationId,
            long studentId,
            ScoreCommand command) {
        return mutate(councilId, registrationId, studentId, command, false);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public EvaluationView submit(
            long councilId,
            long registrationId,
            long studentId,
            ScoreCommand command) {
        return mutate(councilId, registrationId, studentId, command, true);
    }

    @Transactional(readOnly = true)
    public List<ScoringAssignmentView> listForCurrentLecturer() {
        ActorIdentity lecturer = currentLecturer();
        return councilTopics.findAllForLecturerByCouncilStatus(
                        lecturer.accountId(), CouncilStatus.ACTIVE)
                .stream()
                .filter(assignment -> assignment.getCouncil().getStatus()
                        == CouncilStatus.ACTIVE)
                .filter(assignment -> sameFaculty(lecturer, assignment))
                .filter(ScoringService::samePeriod)
                .filter(assignment -> assignment.getRegistration().getStatus()
                        == RegistrationStatus.ACCEPTED)
                .filter(assignment -> !isSupervisor(
                        assignment.getRegistration(), lecturer.accountId()))
                .map(assignment -> toAssignmentView(assignment, lecturer.accountId()))
                .toList();
    }

    @Transactional(readOnly = true)
    public ScoreWorkspace workspace(long councilId, long registrationId) {
        ActorIdentity lecturer = currentLecturer();
        CouncilTopicEntity assignment = councilTopics
                .findByCouncilIdAndRegistrationId(councilId, registrationId)
                .orElseThrow(() -> new ApplicationException(
                        ApplicationErrorCode.RESOURCE_NOT_FOUND));
        if (!sameFaculty(lecturer, assignment)) {
            throw new AccessDeniedException("Scoring assignment is outside lecturer faculty");
        }
        if (!samePeriod(assignment)) {
            throw new ApplicationException(ApplicationErrorCode.CONFLICT);
        }
        CouncilMemberEntity member = members.findByCouncilIdAndLecturerId(
                        councilId, lecturer.accountId())
                .orElse(null);
        boolean assignedMember = member != null
                && Objects.equals(member.getLecturer().getId(), lecturer.accountId());
        boolean supervisor = isSupervisor(
                assignment.getRegistration(), lecturer.accountId());
        applyRules(new ScoreContext(
                assignment.getCouncil().getStatus(),
                assignment.getRegistration().getStatus(),
                assignedMember,
                supervisor,
                BigDecimal.ZERO));

        Map<Long, EvaluationEntity> evaluationByStudent = evaluations
                .findAllByCouncilIdAndRegistrationIdAndEvaluatorIdOrderByStudentId(
                        councilId, registrationId, lecturer.accountId())
                .stream()
                .collect(java.util.stream.Collectors.toMap(
                        evaluation -> evaluation.getStudent().getId(),
                        evaluation -> evaluation));
        List<StudentScoreView> students = registrationMembers
                .findAllByRegistrationId(registrationId)
                .stream()
                .map(snapshot -> toStudentView(
                        snapshot, evaluationByStudent.get(snapshot.getStudent().getId())))
                .toList();
        if (students.isEmpty()) {
            throw new ApplicationException(ApplicationErrorCode.CONFLICT);
        }
        return new ScoreWorkspace(
                toAssignmentView(assignment, lecturer.accountId()), students);
    }

    @Transactional(readOnly = true)
    public StudentScoreWorkspace studentWorkspace(
            long councilId, long registrationId, long studentId) {
        ScoreWorkspace workspace = workspace(councilId, registrationId);
        StudentScoreView student = workspace.students().stream()
                .filter(candidate -> candidate.studentId() == studentId)
                .findFirst()
                .orElseThrow(() -> new ApplicationException(
                        ApplicationErrorCode.RESOURCE_NOT_FOUND));
        return new StudentScoreWorkspace(workspace.assignment(), student);
    }

    private EvaluationView mutate(
            long councilId,
            long registrationId,
            long studentId,
            ScoreCommand command,
            boolean submit) {
        Objects.requireNonNull(command, "command");
        long periodId = councils.findPeriodIdByCouncilId(councilId)
                .orElseThrow(() -> new ApplicationException(
                        ApplicationErrorCode.RESOURCE_NOT_FOUND));
        RegistrationPeriodEntity period = periods.findByIdForUpdate(periodId)
                .orElseThrow(() -> new ApplicationException(
                        ApplicationErrorCode.RESOURCE_NOT_FOUND));
        ActorIdentity actor = currentLecturer();
        CouncilEntity council = councils.findByIdForUpdate(councilId)
                .orElseThrow(() -> new ApplicationException(
                        ApplicationErrorCode.RESOURCE_NOT_FOUND));
        if (!Objects.equals(council.getPeriod().getId(), period.getId())) {
            throw new ApplicationException(ApplicationErrorCode.CONFLICT);
        }
        if (!Objects.equals(actor.facultyId(), period.getFaculty().getId())) {
            throw new AccessDeniedException("Scoring assignment is outside lecturer faculty");
        }

        List<CouncilTopicEntity> lockedAssignments = councilTopics
                .findAllByCouncilIdForUpdate(councilId);
        List<CouncilMemberEntity> lockedMembers = members
                .findAllByCouncilIdForUpdate(councilId);
        CouncilTopicEntity assignment = lockedAssignments.stream()
                .filter(candidate -> Objects.equals(
                        candidate.getRegistration().getId(), registrationId))
                .findFirst()
                .orElseThrow(() -> new ApplicationException(
                        ApplicationErrorCode.RESOURCE_NOT_FOUND));
        if (!Objects.equals(assignment.getCouncil().getId(), councilId)
                || !samePeriod(assignment)) {
            throw new ApplicationException(ApplicationErrorCode.CONFLICT);
        }
        CouncilMemberEntity member = lockedMembers.stream()
                .filter(candidate -> Objects.equals(
                        candidate.getLecturer().getId(), actor.accountId()))
                .findFirst()
                .orElse(null);
        RegistrationEntity registration = assignment.getRegistration();
        if (council.getMode() == CouncilMode.DIRECT
                && "TLCN".equals(period.getTopicType())) {
            directGrading.requireSubmittedReviewerForScoring(registration);
        }
        RegistrationMemberEntity snapshotStudent = registrationMembers
                .findAllByRegistrationId(registrationId)
                .stream()
                .filter(candidate -> Objects.equals(
                        candidate.getStudent().getId(), studentId))
                .findFirst()
                .orElseThrow(() -> new ApplicationException(
                        ApplicationErrorCode.RESOURCE_NOT_FOUND));
        boolean assignedMember = member != null
                && Objects.equals(member.getLecturer().getId(), actor.accountId());
        boolean supervisor = isSupervisor(registration, actor.accountId());
        applyRules(new ScoreContext(
                council.getStatus(),
                registration.getStatus(),
                assignedMember,
                supervisor,
                command.score()));
        List<EvaluationEntity> lockedEvaluations = evaluations
                .findAllByCouncilIdForUpdate(councilId);
        ScoreComponentEntity component = components
                .findByPeriodIdAndName(periodId, ScoreComponentEntity.COUNCIL_SCORE_NAME)
                .orElseGet(() -> components.saveAndFlush(new ScoreComponentEntity(period)));
        Optional<EvaluationEntity> existing = lockedEvaluations.stream()
                .filter(evaluation -> Objects.equals(
                        evaluation.getRegistration().getId(), registrationId))
                .filter(evaluation -> Objects.equals(
                        evaluation.getStudent().getId(), studentId))
                .filter(evaluation -> Objects.equals(
                        evaluation.getEvaluator().getId(), actor.accountId()))
                .filter(evaluation -> Objects.equals(
                        evaluation.getComponent().getId(), component.getId()))
                .findFirst();
        validateVersion(existing, command.expectedVersion());

        Instant now = clock.instant();
        if (existing.isPresent()) {
            EvaluationEntity evaluation = existing.get();
            if (submit) {
                evaluation.submit(command.score(), command.feedback(), now);
            } else {
                evaluation.saveDraft(command.score(), command.feedback(), now);
            }
            evaluations.flush();
            return toView(evaluation);
        }

        UserAccountEntity evaluator = Objects.requireNonNull(member, "member").getLecturer();
        EvaluationEntity evaluation = new EvaluationEntity(
                registration,
                snapshotStudent.getStudent(),
                evaluator,
                component,
                council,
                command.score(),
                command.feedback(),
                now);
        if (submit) {
            evaluation.submit(command.score(), command.feedback(), now);
        }
        return toView(evaluations.saveAndFlush(evaluation));
    }

    private static void validateVersion(
            Optional<EvaluationEntity> existing,
            Long expectedVersion) {
        if (existing.isPresent()) {
            if (expectedVersion == null
                    || existing.get().getVersion() != expectedVersion) {
                throw new OptimisticLockingFailureException(
                        "Evaluation version is stale");
            }
        } else if (expectedVersion != null) {
            throw new OptimisticLockingFailureException(
                    "Evaluation no longer exists");
        }
    }

    private ActorIdentity currentLecturer() {
        ActorIdentity actor = authorization.requireRole(RoleCode.LECTURER);
        if (actor.accountId() == null) {
            throw new AccessDeniedException("Lecturer account is missing");
        }
        return actor;
    }

    private static boolean isSupervisor(RegistrationEntity registration, long actorId) {
        return registration.getTopic().getSupervisors().stream()
                .map(TopicSupervisorEntity::getLecturer)
                .map(UserAccountEntity::getId)
                .anyMatch(id -> Objects.equals(id, actorId));
    }

    private static boolean sameFaculty(
            ActorIdentity actor,
            CouncilTopicEntity assignment) {
        return Objects.equals(actor.facultyId(),
                assignment.getCouncil().getPeriod().getFaculty().getId());
    }

    private static boolean samePeriod(CouncilTopicEntity assignment) {
        Long periodId = assignment.getCouncil().getPeriod().getId();
        return Objects.equals(assignment.getRegistration().getPeriod().getId(), periodId)
                && Objects.equals(
                        assignment.getRegistration().getTopic().getPeriod().getId(),
                        periodId);
    }

    private static ScoringAssignmentView toAssignmentView(
            CouncilTopicEntity assignment,
            long actorId) {
        return new ScoringAssignmentView(
                assignment.getCouncil().getId(),
                assignment.getCouncil().getName(),
                assignment.getRegistration().getId(),
                assignment.getRegistration().getGroup().getId(),
                assignment.getRegistration().getTopic().getTitle(),
                assignment.getScheduledAt(),
                assignment.getLocation(),
                Objects.equals(assignment.getCouncil().getChair().getId(), actorId),
                assignment.getCouncil().getVersion(),
                assignment.getCouncil().getMode() == CouncilMode.DIRECT);
    }

    private static EvaluationView toView(EvaluationEntity evaluation) {
        return new EvaluationView(
                evaluation.getId(),
                ScoreFormat.normalize(evaluation.getScore()),
                evaluation.getFeedback(),
                evaluation.getStatus(),
                evaluation.getEvaluatedAt(),
                evaluation.getSubmittedAt(),
                evaluation.getVersion());
    }

    private static StudentScoreView toStudentView(
            RegistrationMemberEntity snapshot,
            EvaluationEntity evaluation) {
        return new StudentScoreView(
                snapshot.getStudent().getId(),
                displayName(snapshot.getStudent()),
                snapshot.getSlot(),
                snapshot.isLeaderAtSubmission(),
                evaluation == null ? null : toView(evaluation));
    }

    private static String displayName(UserAccountEntity account) {
        return account.getFullName() == null || account.getFullName().isBlank()
                ? account.getUsername()
                : account.getFullName();
    }

    private static void applyRules(ScoreContext context) {
        try {
            ScoreRules.validate(context);
        } catch (ScoreRuleException exception) {
            throw switch (exception.failure()) {
                case ACCESS_DENIED -> new AccessDeniedException("Access denied");
                case INVALID_STATE -> new ApplicationException(
                        ApplicationErrorCode.INVALID_COUNCIL_STATE);
                case INVALID_SCORE -> new ApplicationException(
                        ApplicationErrorCode.INVALID_SCORE);
            };
        }
    }

    public record ScoreCommand(
            BigDecimal score,
            String feedback,
            Long expectedVersion) {
    }

    public record ScoringAssignmentView(
            Long councilId,
            String councilName,
            Long registrationId,
            Long groupId,
            String topicTitle,
            Instant scheduledAt,
            String location,
            boolean currentChair,
            long councilVersion,
            boolean directGrading) {
        public ScoringAssignmentView(
                Long councilId,
                String councilName,
                Long registrationId,
                Long groupId,
                String topicTitle,
                Instant scheduledAt,
                String location,
                boolean currentChair,
                long councilVersion) {
            this(councilId, councilName, registrationId, groupId, topicTitle,
                    scheduledAt, location, currentChair, councilVersion, false);
        }

        public ScoringAssignmentView(
                Long councilId,
                String councilName,
                Long registrationId,
                Long groupId,
                String topicTitle,
                Instant scheduledAt,
                String location) {
            this(councilId, councilName, registrationId, groupId, topicTitle,
                    scheduledAt, location, false, 0L, false);
        }
    }

    public record EvaluationView(
            Long id,
            BigDecimal score,
            String feedback,
            EvaluationStatus status,
            Instant evaluatedAt,
            Instant submittedAt,
            long version) {
    }

    public record ScoreWorkspace(
            ScoringAssignmentView assignment,
            List<StudentScoreView> students) {
        public ScoreWorkspace {
            students = List.copyOf(students);
        }
    }

    public record StudentScoreView(
            long studentId,
            String studentName,
            int slot,
            boolean leaderAtSubmission,
            EvaluationView evaluation) {
    }

    public record StudentScoreWorkspace(
            ScoringAssignmentView assignment,
            StudentScoreView student) {
    }
}
