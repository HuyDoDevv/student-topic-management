package vn.edu.topicregistration.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.context.annotation.Profile;
import org.springframework.beans.factory.annotation.Autowired;
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
import vn.edu.topicregistration.entity.FinalResultEntity;
import vn.edu.topicregistration.entity.FinalResultStatus;
import vn.edu.topicregistration.entity.NotificationEventType;
import vn.edu.topicregistration.entity.NotificationTargetType;
import vn.edu.topicregistration.entity.RegistrationEntity;
import vn.edu.topicregistration.entity.RegistrationMemberEntity;
import vn.edu.topicregistration.entity.RegistrationPeriodEntity;
import vn.edu.topicregistration.entity.RoleCode;
import vn.edu.topicregistration.entity.ReviewerAssignmentEntity;
import vn.edu.topicregistration.entity.ReviewerEvaluationEntity;
import vn.edu.topicregistration.entity.ScoreComponentEntity;
import vn.edu.topicregistration.entity.UserAccountEntity;
import vn.edu.topicregistration.exception.ApplicationErrorCode;
import vn.edu.topicregistration.exception.ApplicationException;
import vn.edu.topicregistration.repository.CouncilMemberRepository;
import vn.edu.topicregistration.repository.CouncilRepository;
import vn.edu.topicregistration.repository.CouncilTopicRepository;
import vn.edu.topicregistration.repository.EvaluationRepository;
import vn.edu.topicregistration.repository.FinalResultRepository;
import vn.edu.topicregistration.repository.RegistrationPeriodRepository;
import vn.edu.topicregistration.repository.RegistrationMemberRepository;
import vn.edu.topicregistration.repository.UserAccountRepository;
import vn.edu.topicregistration.repository.ReviewerAssignmentRepository;
import vn.edu.topicregistration.repository.ReviewerEvaluationRepository;
import vn.edu.topicregistration.repository.ReviewerReportRepository;
import vn.edu.topicregistration.security.AuthorizationPolicy;
import vn.edu.topicregistration.security.AuthorizationPolicy.ActorIdentity;
import vn.edu.topicregistration.service.FinalScoreRules.CompletionContext;
import vn.edu.topicregistration.service.FinalScoreRules.FinalScoreRuleException;
import vn.edu.topicregistration.service.FinalScoreRules.PublicationContext;
import vn.edu.topicregistration.service.NotificationService.EmissionCommand;
import vn.edu.topicregistration.util.ScoreFormat;
import vn.edu.topicregistration.util.ProjectTime;

@Service
@Profile("!test")
public class ResultService {
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern(
            "dd/MM/yyyy HH:mm", Locale.forLanguageTag("vi-VN"));
    private final RegistrationPeriodRepository periods;
    private final CouncilRepository councils;
    private final CouncilTopicRepository assignments;
    private final CouncilMemberRepository members;
    private final RegistrationMemberRepository registrationMembers;
    private final EvaluationRepository evaluations;
    private final FinalResultRepository results;
    private final UserAccountRepository accounts;
    private final ReviewerAssignmentRepository reviewerAssignments;
    private final ReviewerEvaluationRepository reviewerEvaluations;
    private final ReviewerReportRepository reviewerReports;
    private final AuthorizationPolicy authorization;
    private final NotificationService notifications;
    private final Clock clock;
    private final ProjectTime time;

    @Autowired
    public ResultService(
            RegistrationPeriodRepository periods,
            CouncilRepository councils,
            CouncilTopicRepository assignments,
            CouncilMemberRepository members,
            RegistrationMemberRepository registrationMembers,
            EvaluationRepository evaluations,
            FinalResultRepository results,
            UserAccountRepository accounts,
            ReviewerAssignmentRepository reviewerAssignments,
            ReviewerEvaluationRepository reviewerEvaluations,
            ReviewerReportRepository reviewerReports,
            AuthorizationPolicy authorization,
            NotificationService notifications,
            Clock clock,
            ProjectTime time) {
        this.periods = periods;
        this.councils = councils;
        this.assignments = assignments;
        this.members = members;
        this.registrationMembers = registrationMembers;
        this.evaluations = evaluations;
        this.results = results;
        this.accounts = accounts;
        this.reviewerAssignments = reviewerAssignments;
        this.reviewerEvaluations = reviewerEvaluations;
        this.reviewerReports = reviewerReports;
        this.authorization = authorization;
        this.notifications = notifications;
        this.clock = clock;
        this.time = time;
    }

    public ResultService(
            RegistrationPeriodRepository periods,
            CouncilRepository councils,
            CouncilTopicRepository assignments,
            CouncilMemberRepository members,
            RegistrationMemberRepository registrationMembers,
            EvaluationRepository evaluations,
            FinalResultRepository results,
            UserAccountRepository accounts,
            AuthorizationPolicy authorization,
            NotificationService notifications,
            Clock clock) {
        this(periods, councils, assignments, members, registrationMembers, evaluations,
                results, accounts, null, null, null, authorization, notifications,
                clock, new ProjectTime(clock));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public BoardResultView complete(long councilId, long expectedVersion) {
        LockedBoard board = lockBoard(councilId, expectedVersion, Operation.COMPLETE);
        CouncilEntity council = board.council();
        List<CouncilTopicEntity> lockedAssignments = assignments
                .findAllByCouncilIdForUpdate(councilId);
        List<CouncilMemberEntity> lockedMembers = members
                .findAllByCouncilIdForUpdate(councilId);
        List<RegistrationMemberEntity> snapshotStudents = snapshotStudents(lockedAssignments);
        List<EvaluationEntity> lockedEvaluations = evaluations
                .findAllByCouncilIdForUpdate(councilId);
        List<FinalResultEntity> existingResults = results
                .findAllByCouncilIdForUpdate(councilId);

        boolean exactMatrix = exactSubmittedMatrix(
                board.period(), council, lockedAssignments, lockedMembers,
                snapshotStudents, lockedEvaluations);
        boolean direct = council.getMode() == CouncilMode.DIRECT;
        boolean tlcn = direct && "TLCN".equals(board.period().getTopicType());
        boolean kltn = council.getMode() == CouncilMode.COUNCIL
                && "KLTN".equals(board.period().getTopicType());
        if (kltn && !validCouncilConfiguration(
                board.period(), council, lockedMembers, lockedAssignments)) {
            throw new ApplicationException(ApplicationErrorCode.EVALUATIONS_INCOMPLETE);
        }
        applyRules(() -> FinalScoreRules.validateCompletion(new CompletionContext(
                council.getStatus(),
                direct || Objects.equals(council.getChair().getId(), board.actor().accountId()),
                lockedAssignments.size(),
                lockedMembers.size(),
                snapshotStudents.size(),
                submittedCount(lockedEvaluations),
                exactMatrix,
                council.getMode() == null ? CouncilMode.COUNCIL : council.getMode())));
        if (!existingResults.isEmpty()) {
            throw new ApplicationException(ApplicationErrorCode.CONFLICT);
        }
        Map<StudentKey, ReviewerEvaluationEntity> reviewerScores = tlcn || kltn
                ? reviewerScores(lockedAssignments, snapshotStudents,
                        kltn ? lockedMembers : List.of(), true)
                : Map.of();
        UserAccountEntity consolidator = direct
                ? accounts.findDetailedById(board.actor().accountId())
                        .orElseThrow(() -> new ApplicationException(
                                ApplicationErrorCode.RESOURCE_NOT_FOUND))
                : council.getChair();

        Instant completedAt = clock.instant();
        Map<StudentKey, List<EvaluationEntity>> byStudent = evaluationsByStudent(
                lockedEvaluations);
        List<FinalResultEntity> created = new ArrayList<>(snapshotStudents.size());
        for (CouncilTopicEntity assignment : lockedAssignments) {
            RegistrationEntity registration = assignment.getRegistration();
            snapshotStudents.stream()
                    .filter(snapshot -> Objects.equals(
                            snapshot.getRegistration().getId(), registration.getId()))
                    .forEach(snapshot -> {
                        StudentKey key = new StudentKey(
                                registration.getId(), snapshot.getStudent().getId());
                        List<EvaluationEntity> studentEvaluations = byStudent
                                .getOrDefault(key, List.of());
                        List<BigDecimal> sources = new ArrayList<>();
                        if (tlcn || kltn) {
                            sources.add(reviewerScores.get(key).getScore());
                        }
                        studentEvaluations.stream().map(EvaluationEntity::getScore)
                                .forEach(sources::add);
                        BigDecimal finalScore = FinalScoreRules.mean(sources);
                        created.add(new FinalResultEntity(
                                registration,
                                snapshot.getStudent(),
                                council,
                                consolidator,
                                completedAt,
                                summary(sources.size(), finalScore),
                                finalScore));
                    });
        }

        results.saveAll(created);
        results.flush();
        council.transitionTo(CouncilStatus.COMPLETED);
        councils.flush();
        return new BoardResultView(
                councilId,
                CouncilStatus.COMPLETED,
                created.size(),
                expectedVersion + 1);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public BoardResultView publish(long councilId, long expectedVersion) {
        LockedBoard board = lockBoard(councilId, expectedVersion, Operation.PUBLISH);
        CouncilEntity council = board.council();
        List<CouncilTopicEntity> lockedAssignments = assignments
                .findAllByCouncilIdForUpdate(councilId);
        List<CouncilMemberEntity> lockedMembers = members
                .findAllByCouncilIdForUpdate(councilId);
        List<RegistrationMemberEntity> snapshotStudents = snapshotStudents(lockedAssignments);
        List<EvaluationEntity> lockedEvaluations = evaluations
                .findAllByCouncilIdForUpdate(councilId);
        List<FinalResultEntity> lockedResults = results
                .findAllByCouncilIdForUpdate(councilId);

        boolean exactReadyResults = exactReadyResults(
                council, snapshotStudents, lockedResults);
        applyRules(() -> FinalScoreRules.validatePublication(new PublicationContext(
                council.getStatus(),
                snapshotStudents.size(),
                lockedResults.size(),
                exactReadyResults)));
        boolean tlcn = council.getMode() == CouncilMode.DIRECT
                && "TLCN".equals(board.period().getTopicType());
        boolean kltn = council.getMode() == CouncilMode.COUNCIL
                && "KLTN".equals(board.period().getTopicType());
        if (tlcn || kltn) {
            if (tlcn && lockedAssignments.size() != 1) {
                throw new ApplicationException(ApplicationErrorCode.RESULT_NOT_READY);
            }
            if (kltn && !validCouncilConfiguration(
                    board.period(), council, lockedMembers, lockedAssignments)) {
                throw new ApplicationException(ApplicationErrorCode.RESULT_NOT_READY);
            }
            if (!exactSubmittedMatrix(board.period(), council, lockedAssignments,
                    lockedMembers, snapshotStudents, lockedEvaluations)) {
                throw new ApplicationException(ApplicationErrorCode.RESULT_NOT_READY);
            }
            Map<StudentKey, ReviewerEvaluationEntity> reviewerScores = reviewerScores(
                    lockedAssignments, snapshotStudents,
                    kltn ? lockedMembers : List.of(), true);
            Map<StudentKey, List<EvaluationEntity>> graderScores = evaluationsByStudent(
                    lockedEvaluations);
            for (FinalResultEntity result : lockedResults) {
                StudentKey key = new StudentKey(result.getRegistrationId(), result.getStudentId());
                List<EvaluationEntity> studentGraderScores = graderScores.getOrDefault(
                        key, List.of());
                if (studentGraderScores.size() != lockedMembers.size()
                        || studentGraderScores.stream().anyMatch(score ->
                                score.getStatus() != EvaluationStatus.SUBMITTED)) {
                    throw new ApplicationException(ApplicationErrorCode.RESULT_NOT_READY);
                }
                List<BigDecimal> sources = new ArrayList<>();
                sources.add(reviewerScores.get(key).getScore());
                studentGraderScores.stream().map(EvaluationEntity::getScore)
                        .forEach(sources::add);
                if (result.getFinalScore().compareTo(FinalScoreRules.mean(sources)) != 0) {
                    throw new ApplicationException(ApplicationErrorCode.RESULT_NOT_READY);
                }
            }
        }

        UserAccountEntity dean = accounts.findDetailedById(board.actor().accountId())
                .orElseThrow(() -> new ApplicationException(
                        ApplicationErrorCode.RESOURCE_NOT_FOUND));
        Instant publishedAt = clock.instant();
        lockedResults.forEach(result -> result.publish(dean, publishedAt));
        council.transitionTo(CouncilStatus.PUBLISHED);
        results.flush();
        councils.flush();
        Map<Long, List<FinalResultEntity>> resultsByRegistration = lockedResults.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        FinalResultEntity::getRegistrationId,
                        LinkedHashMap::new,
                        java.util.stream.Collectors.toList()));
        for (Map.Entry<Long, List<FinalResultEntity>> entry
                : resultsByRegistration.entrySet()) {
            long registrationId = entry.getKey();
            List<Long> recipientIds = entry.getValue().stream()
                    .map(FinalResultEntity::getStudentId)
                    .distinct()
                    .toList();
            notifications.emit(new EmissionCommand(
                    NotificationEventType.RESULT_PUBLISHED,
                    registrationId,
                    dean.getId(),
                    recipientIds,
                    "Kết quả chấm điểm đã được công bố",
                    "Kết quả cá nhân của bạn đã được công bố. Vui lòng xem chi tiết.",
                    null,
                    NotificationTargetType.RESULT,
                    registrationId));
        }
        return new BoardResultView(
                councilId,
                CouncilStatus.PUBLISHED,
                lockedResults.size(),
                expectedVersion + 1);
    }

    @Transactional(readOnly = true)
    public List<FacultyResultSummaryView> listForCurrentDean() {
        ActorIdentity dean = currentDean();
        Map<Long, List<FacultyResultLecturerView>> lecturersByCouncil = new LinkedHashMap<>();
        return results
                .findAllByCouncilPeriodFacultyIdOrderByConsolidatedAtDescIdRegistrationIdDesc(
                        dean.facultyId())
                .stream()
                .map(result -> toSummary(
                        result,
                        lecturersByCouncil.computeIfAbsent(
                                result.getCouncil().getId(),
                                ignored -> lecturersFor(result.getCouncil()))))
                .toList();
    }

    @Transactional(readOnly = true)
    RegistrationProgress progressForVisibleRegistration(long registrationId) {
        List<RegistrationMemberEntity> students = registrationMembers
                .findAllByRegistrationId(registrationId);
        CouncilTopicEntity gradingAssignment = assignments.findByRegistrationId(registrationId)
                .orElse(null);
        ReviewerAssignmentEntity reviewer = reviewerAssignments
                .findActiveByRegistrationId(registrationId,
                        List.of(ReviewerAssignmentEntity.Status.ASSIGNED,
                                ReviewerAssignmentEntity.Status.SUBMITTED))
                .orElse(null);
        boolean reviewerSubmitted = reviewer != null
                && reviewer.getStatus() == ReviewerAssignmentEntity.Status.SUBMITTED
                && reviewerReports.existsByAssignmentId(reviewer.getId())
                && !students.isEmpty()
                && reviewerEvaluations.findAllByAssignmentIdOrderByStudentId(reviewer.getId())
                        .stream()
                        .filter(score -> score.getStatus() == EvaluationStatus.SUBMITTED)
                        .map(score -> score.getStudent().getId())
                        .collect(java.util.stream.Collectors.toSet())
                        .equals(students.stream()
                                .map(student -> student.getStudent().getId())
                                .collect(java.util.stream.Collectors.toSet()));
        boolean ready = !students.isEmpty();
        boolean published = !students.isEmpty();
        for (RegistrationMemberEntity student : students) {
            FinalResultEntity result = results.findDetailedByRegistrationIdAndStudentId(
                    registrationId, student.getStudent().getId()).orElse(null);
            ready &= result != null && result.getStatus() == FinalResultStatus.READY;
            published &= result != null && result.getStatus() == FinalResultStatus.PUBLISHED;
        }
        boolean gradingStarted = gradingAssignment != null
                && gradingAssignment.getCouncil().getStatus().ordinal()
                        >= CouncilStatus.ACTIVE.ordinal();
        return new RegistrationProgress(reviewer != null, reviewerSubmitted,
                gradingStarted, ready, published);
    }

    record RegistrationProgress(
            boolean reviewerAssigned,
            boolean reviewerSubmitted,
            boolean gradingStarted,
            boolean ready,
            boolean published) {
    }

    @Transactional(readOnly = true)
    public List<DirectPreviewRow> previewDirectForCurrentDean(long councilId) {
        CouncilEntity council = councils.findDetailedById(councilId)
                .orElseThrow(() -> new ApplicationException(
                        ApplicationErrorCode.RESOURCE_NOT_FOUND));
        authorization.requireDeanOfFaculty(requiredFacultyId(council.getPeriod()));
        if (council.getMode() != CouncilMode.DIRECT
                || !"TLCN".equals(council.getPeriod().getTopicType())) {
            throw new ApplicationException(ApplicationErrorCode.RESOURCE_NOT_FOUND);
        }
        List<CouncilTopicEntity> councilAssignments = assignments
                .findAllByCouncilIdOrderByRegistrationId(councilId);
        List<RegistrationMemberEntity> snapshots = snapshotStudents(councilAssignments);
        Map<StudentKey, ReviewerEvaluationEntity> reviewer;
        try {
            reviewer = reviewerScores(councilAssignments, snapshots, false);
        } catch (ApplicationException exception) {
            if (exception.code() != ApplicationErrorCode.EVALUATIONS_INCOMPLETE) {
                throw exception;
            }
            return List.of();
        }
        long registrationId = councilAssignments.getFirst().getRegistration().getId();
        ReviewerAssignmentEntity reviewerAssignment = reviewerAssignments
                .findActiveByRegistrationId(registrationId,
                        List.of(ReviewerAssignmentEntity.Status.SUBMITTED))
                .orElseThrow(() -> new ApplicationException(
                        ApplicationErrorCode.EVALUATIONS_INCOMPLETE));
        Map<StudentKey, List<EvaluationEntity>> grader = evaluationsByStudent(
                evaluations.findAllByCouncilIdAndRegistrationIdAndEvaluatorIdOrderByStudentId(
                        councilId, registrationId, council.getChair().getId()));
        List<DirectPreviewRow> rows = new ArrayList<>();
        for (RegistrationMemberEntity snapshot : snapshots) {
            StudentKey key = new StudentKey(registrationId, snapshot.getStudent().getId());
            ReviewerEvaluationEntity reviewerScore = reviewer.get(key);
            List<EvaluationEntity> submitted = grader.getOrDefault(key, List.of()).stream()
                    .filter(score -> score.getStatus() == EvaluationStatus.SUBMITTED)
                    .toList();
            EvaluationEntity graderScore = submitted.size() == 1 ? submitted.getFirst() : null;
            rows.add(new DirectPreviewRow(
                    displayName(snapshot.getStudent()),
                    displayName(reviewerAssignment.getReviewer()),
                    reviewerScore.getScore(), formatDateTime(reviewerScore.getSubmittedAt()),
                    displayName(council.getChair()),
                    graderScore == null ? null : graderScore.getScore(),
                    graderScore == null ? null : formatDateTime(graderScore.getSubmittedAt()),
                    graderScore == null ? null : FinalScoreRules.mean(List.of(
                            reviewerScore.getScore(), graderScore.getScore()))));
        }
        return rows;
    }

    @Transactional(readOnly = true)
    public FacultyResultDetailView detailForCurrentDean(
            long registrationId, long studentId) {
        FinalResultEntity result = results
                .findDetailedByRegistrationIdAndStudentId(registrationId, studentId)
                .orElseThrow(() -> new ApplicationException(
                        ApplicationErrorCode.RESOURCE_NOT_FOUND));
        long facultyId = requiredFacultyId(result.getCouncil().getPeriod());
        authorization.requireDeanOfFaculty(facultyId);
        List<EvaluationResultView> evaluationViews = evaluations
                .findAllByCouncilIdAndRegistrationIdAndStudentIdOrderByEvaluatorId(
                        result.getCouncil().getId(), registrationId, studentId)
                .stream()
                .map(ResultService::toEvaluationView)
                .toList();
        ReviewerResultView reviewerView = null;
        if ("TLCN".equals(result.getRegistration().getPeriod().getTopicType())
                && result.getCouncil().getMode() == CouncilMode.DIRECT) {
            List<RegistrationMemberEntity> singleSnapshot = registrationMembers
                    .findAllByRegistrationId(registrationId);
            Map<StudentKey, ReviewerEvaluationEntity> sources = reviewerScores(
                    assignments.findAllByCouncilIdOrderByRegistrationId(
                            result.getCouncil().getId()), singleSnapshot, false);
            ReviewerAssignmentEntity reviewerAssignment = reviewerAssignments
                    .findActiveByRegistrationId(registrationId,
                            List.of(ReviewerAssignmentEntity.Status.SUBMITTED))
                    .orElseThrow(() -> new ApplicationException(
                            ApplicationErrorCode.EVALUATIONS_INCOMPLETE));
            ReviewerEvaluationEntity score = sources.get(new StudentKey(
                    registrationId, studentId));
            reviewerView = new ReviewerResultView(
                    reviewerAssignment.getReviewer().getId(),
                    displayName(reviewerAssignment.getReviewer()),
                    score.getScore(), score.getFeedback(), score.getSubmittedAt());
        }
        return new FacultyResultDetailView(
                toSummary(result, lecturersFor(result.getCouncil())),
                result.getEvaluationSummary(),
                displayName(result.getConsolidatedBy()),
                result.getPublishedBy() == null
                        ? null : displayName(result.getPublishedBy()),
                evaluationViews,
                reviewerView);
    }

    @Transactional(readOnly = true)
    public List<StudentResultSummaryView> listForCurrentStudent() {
        ActorIdentity student = currentStudent();
        return results.findAllVisibleToStudent(
                        student.accountId(), FinalResultStatus.PUBLISHED)
                .stream()
                .map(ResultService::toStudentSummary)
                .toList();
    }

    @Transactional(readOnly = true)
    public StudentResultDetailView detailForCurrentStudent(long registrationId) {
        ActorIdentity student = currentStudent();
        FinalResultEntity result = results.findVisibleToStudent(
                        registrationId,
                        student.accountId(),
                        FinalResultStatus.PUBLISHED)
                .orElseThrow(() -> new ApplicationException(
                        ApplicationErrorCode.RESOURCE_NOT_FOUND));
        List<EvaluationResultView> evaluationViews = evaluations
                .findAllByCouncilIdAndRegistrationIdAndStudentIdOrderByEvaluatorId(
                        result.getCouncil().getId(), registrationId, student.accountId())
                .stream()
                .filter(evaluation -> evaluation.getStatus() == EvaluationStatus.SUBMITTED)
                .map(ResultService::toEvaluationView)
                .toList();
        return new StudentResultDetailView(
                toStudentSummary(result),
                result.getEvaluationSummary(),
                evaluationViews);
    }

    private LockedBoard lockBoard(
            long councilId,
            long expectedVersion,
            Operation operation) {
        if (expectedVersion < 0) {
            throw new ApplicationException(ApplicationErrorCode.VALIDATION_FAILED);
        }
        long periodId = councils.findPeriodIdByCouncilId(councilId)
                .orElseThrow(() -> new ApplicationException(
                        ApplicationErrorCode.RESOURCE_NOT_FOUND));
        RegistrationPeriodEntity period = periods.findByIdForUpdate(periodId)
                .orElseThrow(() -> new ApplicationException(
                        ApplicationErrorCode.RESOURCE_NOT_FOUND));
        CouncilEntity council = councils.findByIdForUpdate(councilId)
                .orElseThrow(() -> new ApplicationException(
                        ApplicationErrorCode.RESOURCE_NOT_FOUND));
        if (!Objects.equals(council.getPeriod().getId(), periodId)
                || requiredFacultyId(council.getPeriod()) != requiredFacultyId(period)) {
            throw new ApplicationException(ApplicationErrorCode.CONFLICT);
        }
        if (council.getMode() != CouncilMode.DIRECT
                && !"KLTN".equals(period.getTopicType())
                && council.getStatus() != CouncilStatus.PUBLISHED) {
            throw new ApplicationException(ApplicationErrorCode.INVALID_COUNCIL_STATE);
        }
        ActorIdentity actor = operation == Operation.PUBLISH
                || council.getMode() == CouncilMode.DIRECT
                ? authorization.requireDeanOfFaculty(requiredFacultyId(period))
                : currentLecturer();
        if (council.getVersion() != expectedVersion) {
            throw new OptimisticLockingFailureException("Council version is stale");
        }
        return new LockedBoard(period, council, actor);
    }

    private ActorIdentity currentLecturer() {
        ActorIdentity lecturer = authorization.requireRole(RoleCode.LECTURER);
        if (lecturer.accountId() == null) {
            throw new AccessDeniedException("Lecturer account is missing");
        }
        return lecturer;
    }

    private ActorIdentity currentDean() {
        ActorIdentity dean = authorization.requireRole(RoleCode.DEAN);
        if (dean.accountId() == null || dean.facultyId() == null) {
            throw new AccessDeniedException("Dean scope is missing");
        }
        return dean;
    }

    private ActorIdentity currentStudent() {
        ActorIdentity student = authorization.requireRole(RoleCode.STUDENT);
        if (student.accountId() == null) {
            throw new AccessDeniedException("Student account is missing");
        }
        return student;
    }

    private static boolean exactSubmittedMatrix(
            RegistrationPeriodEntity period,
            CouncilEntity council,
            List<CouncilTopicEntity> assignments,
            List<CouncilMemberEntity> members,
            List<RegistrationMemberEntity> snapshotStudents,
            List<EvaluationEntity> evaluations) {
        Set<Long> registrationIds = new HashSet<>();
        for (CouncilTopicEntity assignment : assignments) {
            if (assignment.getRegistration() == null
                    || assignment.getRegistration().getId() == null
                    || assignment.getCouncil() == null
                    || !Objects.equals(assignment.getCouncil().getId(), council.getId())
                    || !Objects.equals(
                            assignment.getRegistration().getPeriod().getId(), period.getId())
                    || !registrationIds.add(assignment.getRegistration().getId())) {
                return false;
            }
        }
        Set<Long> memberIds = new HashSet<>();
        for (CouncilMemberEntity member : members) {
            if (member.getLecturer() == null
                    || member.getLecturer().getId() == null
                    || !memberIds.add(member.getLecturer().getId())) {
                return false;
            }
        }
        Set<StudentKey> studentKeys = new HashSet<>();
        for (RegistrationMemberEntity snapshot : snapshotStudents) {
            if (snapshot.getRegistration() == null
                    || snapshot.getRegistration().getId() == null
                    || !registrationIds.contains(snapshot.getRegistration().getId())
                    || snapshot.getStudent() == null
                    || snapshot.getStudent().getId() == null
                    || !studentKeys.add(new StudentKey(
                            snapshot.getRegistration().getId(),
                            snapshot.getStudent().getId()))) {
                return false;
            }
        }
        if (studentKeys.size() < registrationIds.size()) {
            return false;
        }
        Set<EvaluationKey> keys = new HashSet<>();
        for (EvaluationEntity evaluation : evaluations) {
            if (evaluation.getCouncil() == null
                    || !Objects.equals(evaluation.getCouncil().getId(), council.getId())
                    || evaluation.getRegistration() == null
                    || !registrationIds.contains(evaluation.getRegistration().getId())
                    || evaluation.getStudent() == null
                    || !studentKeys.contains(new StudentKey(
                            evaluation.getRegistration().getId(),
                            evaluation.getStudent().getId()))
                    || evaluation.getEvaluator() == null
                    || !memberIds.contains(evaluation.getEvaluator().getId())
                    || evaluation.getComponent() == null
                    || !ScoreComponentEntity.COUNCIL_SCORE_NAME.equals(
                            evaluation.getComponent().getName())
                    || evaluation.getComponent().getPeriod() == null
                    || !Objects.equals(
                            evaluation.getComponent().getPeriod().getId(), period.getId())
                    || evaluation.getStatus() != EvaluationStatus.SUBMITTED
                    || evaluation.getSubmittedAt() == null
                    || !keys.add(new EvaluationKey(
                            evaluation.getRegistration().getId(),
                            evaluation.getStudent().getId(),
                            evaluation.getEvaluator().getId()))) {
                return false;
            }
        }
        return keys.size() == (long) studentKeys.size() * memberIds.size();
    }

    private static boolean validCouncilConfiguration(
            RegistrationPeriodEntity period,
            CouncilEntity council,
            List<CouncilMemberEntity> councilMembers,
            List<CouncilTopicEntity> councilAssignments) {
        try {
            CouncilRules.validateCreation(
                    period.getTopicType(), requiredFacultyId(period),
                    council.getChair().getId(), council.getSecretary().getId(),
                    councilMembers.stream().map(member -> {
                        UserAccountEntity lecturer = member.getLecturer();
                        return new CouncilRules.MemberCandidate(
                                lecturer.getId(),
                                lecturer.getFaculty() == null
                                        ? null : lecturer.getFaculty().getId(),
                                lecturer.isEnabled(),
                                lecturer.getRoleCodes().contains(RoleCode.LECTURER)
                                        && !lecturer.getRoleCodes().contains(RoleCode.DEAN));
                    }).toList());
        } catch (CouncilRules.CouncilRuleException exception) {
            return false;
        }
        Set<Long> memberIds = councilMembers.stream()
                .map(member -> member.getLecturer().getId())
                .collect(java.util.stream.Collectors.toSet());
        return councilAssignments.stream().noneMatch(assignment ->
                assignment.getRegistration().getTopic().getSupervisors().stream()
                        .anyMatch(supervisor -> memberIds.contains(
                                supervisor.getLecturer().getId())));
    }

    private static boolean exactReadyResults(
            CouncilEntity council,
            List<RegistrationMemberEntity> snapshotStudents,
            List<FinalResultEntity> results) {
        Set<StudentKey> expectedKeys = snapshotStudents.stream()
                .filter(snapshot -> snapshot.getRegistration() != null)
                .filter(snapshot -> snapshot.getStudent() != null)
                .map(snapshot -> new StudentKey(
                        snapshot.getRegistration().getId(), snapshot.getStudent().getId()))
                .collect(java.util.stream.Collectors.toSet());
        Set<StudentKey> resultKeys = new HashSet<>();
        for (FinalResultEntity result : results) {
            if (result.getRegistration() == null
                    || result.getRegistration().getId() == null
                    || result.getStudent() == null
                    || result.getStudent().getId() == null
                    || !expectedKeys.contains(new StudentKey(
                            result.getRegistration().getId(), result.getStudent().getId()))
                    || result.getCouncil() == null
                    || !Objects.equals(result.getCouncil().getId(), council.getId())
                    || result.getStatus() != FinalResultStatus.READY
                    || !resultKeys.add(new StudentKey(
                            result.getRegistration().getId(), result.getStudent().getId()))) {
                return false;
            }
        }
        return !expectedKeys.isEmpty() && resultKeys.equals(expectedKeys);
    }

    private static int submittedCount(List<EvaluationEntity> evaluations) {
        return Math.toIntExact(evaluations.stream()
                .filter(evaluation -> evaluation.getStatus() == EvaluationStatus.SUBMITTED)
                .count());
    }

    private static Map<StudentKey, List<EvaluationEntity>> evaluationsByStudent(
            List<EvaluationEntity> evaluations) {
        Map<StudentKey, List<EvaluationEntity>> grouped = new LinkedHashMap<>();
        for (EvaluationEntity evaluation : evaluations) {
            grouped.computeIfAbsent(
                    new StudentKey(
                            evaluation.getRegistration().getId(),
                            evaluation.getStudent().getId()),
                    ignored -> new ArrayList<>())
                    .add(evaluation);
        }
        return grouped;
    }

    private List<RegistrationMemberEntity> snapshotStudents(
            List<CouncilTopicEntity> councilAssignments) {
        List<RegistrationMemberEntity> snapshots = new ArrayList<>();
        for (CouncilTopicEntity assignment : councilAssignments) {
            snapshots.addAll(registrationMembers.findAllByRegistrationId(
                    assignment.getRegistration().getId()));
        }
        return snapshots;
    }

    private Map<StudentKey, ReviewerEvaluationEntity> reviewerScores(
            List<CouncilTopicEntity> councilAssignments,
            List<RegistrationMemberEntity> snapshots,
            boolean lock) {
        if (councilAssignments.size() != 1) {
            throw new ApplicationException(ApplicationErrorCode.EVALUATIONS_INCOMPLETE);
        }
        return reviewerScores(councilAssignments, snapshots, List.of(), lock);
    }

    private Map<StudentKey, ReviewerEvaluationEntity> reviewerScores(
            List<CouncilTopicEntity> councilAssignments,
            List<RegistrationMemberEntity> snapshots,
            List<CouncilMemberEntity> councilMembers,
            boolean lock) {
        if (councilAssignments.isEmpty()) {
            throw new ApplicationException(ApplicationErrorCode.EVALUATIONS_INCOMPLETE);
        }
        Set<StudentKey> expected = new HashSet<>();
        for (RegistrationMemberEntity snapshot : snapshots) {
            expected.add(new StudentKey(
                    snapshot.getRegistration().getId(), snapshot.getStudent().getId()));
        }
        Map<StudentKey, ReviewerEvaluationEntity> found = new LinkedHashMap<>();
        Set<Long> registrationIds = new HashSet<>();
        for (CouncilTopicEntity councilAssignment : councilAssignments) {
            RegistrationEntity registration = councilAssignment.getRegistration();
            long registrationId = registration.getId();
            if (!registrationIds.add(registrationId)) {
                throw new ApplicationException(ApplicationErrorCode.EVALUATIONS_INCOMPLETE);
            }
            ReviewerAssignmentEntity reviewer = (lock
                    ? reviewerAssignments.findActiveByRegistrationIdForUpdate(
                            registrationId, List.of(ReviewerAssignmentEntity.Status.SUBMITTED))
                    : reviewerAssignments.findActiveByRegistrationId(
                            registrationId, List.of(ReviewerAssignmentEntity.Status.SUBMITTED)))
                    .orElseThrow(() -> new ApplicationException(
                            ApplicationErrorCode.EVALUATIONS_INCOMPLETE));
            if (reviewer.getSubmittedAt() == null || reviewer.getDeadlineDate() == null
                    || reviewer.getSubmittedAt().isAfter(time.toEndOfDay(reviewer.getDeadlineDate()))
                    || reviewerReports.findByAssignmentId(reviewer.getId())
                            .filter(report -> report.getSubmittedAt() != null
                                    && !report.getSubmittedAt().isAfter(
                                            time.toEndOfDay(reviewer.getDeadlineDate())))
                            .isEmpty()
                    || (!councilMembers.isEmpty()
                            && invalidCouncilReviewer(registration, reviewer, councilMembers))) {
                throw new ApplicationException(ApplicationErrorCode.EVALUATIONS_INCOMPLETE);
            }
            List<ReviewerEvaluationEntity> source = lock
                    ? reviewerEvaluations.findAllByAssignmentIdForUpdate(reviewer.getId())
                    : reviewerEvaluations.findAllByAssignmentIdOrderByStudentId(reviewer.getId());
            for (ReviewerEvaluationEntity score : source) {
                StudentKey key = new StudentKey(
                        score.getRegistration().getId(), score.getStudent().getId());
                if (key.registrationId() != registrationId
                        || !expected.contains(key)
                        || score.getStatus() != EvaluationStatus.SUBMITTED
                        || score.getSubmittedAt() == null
                        || score.getSubmittedAt().isAfter(time.toEndOfDay(reviewer.getDeadlineDate()))
                        || found.putIfAbsent(key, score) != null) {
                    throw new ApplicationException(ApplicationErrorCode.EVALUATIONS_INCOMPLETE);
                }
            }
        }
        if (expected.isEmpty() || !found.keySet().equals(expected)
                || snapshots.stream().anyMatch(snapshot ->
                        !registrationIds.contains(snapshot.getRegistration().getId()))) {
            throw new ApplicationException(ApplicationErrorCode.EVALUATIONS_INCOMPLETE);
        }
        return found;
    }

    private static boolean invalidCouncilReviewer(
            RegistrationEntity registration,
            ReviewerAssignmentEntity reviewerAssignment,
            List<CouncilMemberEntity> councilMembers) {
        UserAccountEntity reviewer = reviewerAssignment.getReviewer();
        if (reviewer == null || reviewer.getId() == null || !reviewer.isEnabled()
                || !reviewer.getRoleCodes().contains(RoleCode.LECTURER)
                || reviewer.getRoleCodes().contains(RoleCode.DEAN)
                || reviewer.getFaculty() == null
                || !Objects.equals(reviewer.getFaculty().getId(),
                        registration.getPeriod().getFaculty().getId())) {
            return true;
        }
        long reviewerId = reviewer.getId();
        return councilMembers.stream().anyMatch(member -> Objects.equals(
                member.getLecturer().getId(), reviewerId))
                || registration.getTopic().getSupervisors().stream().anyMatch(
                        supervisor -> Objects.equals(
                                supervisor.getLecturer().getId(), reviewerId));
    }

    private static String summary(int evaluationCount, BigDecimal mean) {
        return evaluationCount + " điểm đã nộp; trung bình " + ScoreFormat.display(mean);
    }

    private String formatDateTime(Instant instant) {
        return instant == null ? null : DATE_TIME.format(time.toBusinessTime(instant));
    }

    private List<FacultyResultLecturerView> lecturersFor(CouncilEntity council) {
        Map<Long, FacultyResultLecturerView> lecturers = new LinkedHashMap<>();
        addLecturer(lecturers, council.getChair());
        addLecturer(lecturers, council.getSecretary());
        members.findAllByCouncilIdOrderBySlot(council.getId()).stream()
                .map(CouncilMemberEntity::getLecturer)
                .forEach(lecturer -> addLecturer(lecturers, lecturer));
        return List.copyOf(lecturers.values());
    }

    private static void addLecturer(
            Map<Long, FacultyResultLecturerView> lecturers,
            UserAccountEntity lecturer) {
        if (lecturer != null && lecturer.getId() != null) {
            lecturers.putIfAbsent(lecturer.getId(), new FacultyResultLecturerView(
                    lecturer.getId(), displayName(lecturer)));
        }
    }

    private static FacultyResultSummaryView toSummary(
            FinalResultEntity result,
            List<FacultyResultLecturerView> lecturers) {
        return new FacultyResultSummaryView(
                result.getRegistrationId(),
                result.getStudentId(),
                displayName(result.getStudent()),
                result.getRegistration().getGroup().getId(),
                result.getRegistration().getPeriod().getId(),
                result.getRegistration().getPeriod().getName(),
                result.getRegistration().getTopic().getTitle(),
                result.getCouncil().getId(),
                result.getCouncil().getName(),
                result.getCouncil().getMode(),
                result.getRegistration().getPeriod().getTopicType(),
                lecturers,
                result.getStatus(),
                result.getFinalScore(),
                result.getConsolidatedAt(),
                result.getPublishedAt(),
                result.getVersion());
    }

    private static EvaluationResultView toEvaluationView(EvaluationEntity evaluation) {
        return new EvaluationResultView(
                evaluation.getEvaluator().getId(),
                displayName(evaluation.getEvaluator()),
                evaluation.getScore(),
                evaluation.getFeedback(),
                evaluation.getStatus(),
                evaluation.getSubmittedAt());
    }

    private static StudentResultSummaryView toStudentSummary(FinalResultEntity result) {
        return new StudentResultSummaryView(
                result.getRegistrationId(),
                result.getRegistration().getGroup().getId(),
                result.getRegistration().getTopic().getTitle(),
                result.getCouncil().getName(),
                result.getFinalScore(),
                result.getPublishedAt());
    }

    private static String displayName(UserAccountEntity account) {
        return account.getFullName() == null || account.getFullName().isBlank()
                ? account.getUsername()
                : account.getFullName();
    }

    private static long requiredFacultyId(RegistrationPeriodEntity period) {
        if (period.getFaculty() == null || period.getFaculty().getId() == null) {
            throw new ApplicationException(ApplicationErrorCode.CONFLICT);
        }
        return period.getFaculty().getId();
    }

    private static void applyRules(Runnable rules) {
        try {
            rules.run();
        } catch (FinalScoreRuleException exception) {
            throw switch (exception.failure()) {
                case ACCESS_DENIED -> new AccessDeniedException("Access denied");
                case INVALID_STATE -> new ApplicationException(
                        ApplicationErrorCode.INVALID_COUNCIL_STATE);
                case EVALUATIONS_INCOMPLETE -> new ApplicationException(
                        ApplicationErrorCode.EVALUATIONS_INCOMPLETE);
                case RESULT_NOT_READY -> new ApplicationException(
                        ApplicationErrorCode.RESULT_NOT_READY);
            };
        }
    }

    private enum Operation {
        COMPLETE,
        PUBLISH
    }

    private record LockedBoard(
            RegistrationPeriodEntity period,
            CouncilEntity council,
            ActorIdentity actor) {
    }

    private record StudentKey(Long registrationId, Long studentId) {
    }

    private record EvaluationKey(Long registrationId, Long studentId, Long evaluatorId) {
    }

    public record BoardResultView(
            Long councilId,
            CouncilStatus status,
            int resultCount,
            long version) {
    }

    public record DirectPreviewRow(
            String studentName,
            String reviewerName,
            BigDecimal reviewerScore,
            String reviewerSubmittedAt,
            String graderName,
            BigDecimal graderScore,
            String graderSubmittedAt,
            BigDecimal finalScore) {
    }

    public record FacultyResultSummaryView(
            Long registrationId,
            Long studentId,
            String studentName,
            Long groupId,
            Long periodId,
            String periodName,
            String topicTitle,
            Long councilId,
            String councilName,
            CouncilMode mode,
            String topicType,
            List<FacultyResultLecturerView> lecturers,
            FinalResultStatus status,
            BigDecimal finalScore,
            Instant consolidatedAt,
            Instant publishedAt,
            long version) {
        public FacultyResultSummaryView {
            lecturers = List.copyOf(lecturers);
        }
        public FacultyResultSummaryView(
                Long registrationId, Long studentId, String studentName, Long groupId,
                Long periodId, String periodName, String topicTitle, Long councilId,
                String councilName, List<FacultyResultLecturerView> lecturers,
                FinalResultStatus status, BigDecimal finalScore, Instant consolidatedAt,
                Instant publishedAt, long version) {
            this(registrationId, studentId, studentName, groupId, periodId, periodName,
                    topicTitle, councilId, councilName, CouncilMode.COUNCIL, "KLTN",
                    lecturers, status, finalScore, consolidatedAt, publishedAt, version);
        }
    }

    public record FacultyResultLecturerView(Long id, String name) {
    }

    public record EvaluationResultView(
            Long evaluatorId,
            String evaluatorName,
            BigDecimal score,
            String feedback,
            EvaluationStatus status,
            Instant submittedAt) {
    }

    public record FacultyResultDetailView(
            FacultyResultSummaryView result,
            String evaluationSummary,
            String consolidatedBy,
            String publishedBy,
            List<EvaluationResultView> evaluations,
            ReviewerResultView reviewer) {
        public FacultyResultDetailView {
            evaluations = List.copyOf(evaluations);
        }
        public FacultyResultDetailView(
                FacultyResultSummaryView result, String evaluationSummary,
                String consolidatedBy, String publishedBy,
                List<EvaluationResultView> evaluations) {
            this(result, evaluationSummary, consolidatedBy, publishedBy, evaluations, null);
        }
    }

    public record ReviewerResultView(
            Long reviewerId,
            String reviewerName,
            BigDecimal score,
            String feedback,
            Instant submittedAt) {
    }

    public record StudentResultSummaryView(
            Long registrationId,
            Long groupId,
            String topicTitle,
            String councilName,
            BigDecimal finalScore,
            Instant publishedAt) {
    }

    public record StudentResultDetailView(
            StudentResultSummaryView result,
            String evaluationSummary,
            List<EvaluationResultView> evaluations) {
        public StudentResultDetailView {
            evaluations = List.copyOf(evaluations);
        }
    }
}
