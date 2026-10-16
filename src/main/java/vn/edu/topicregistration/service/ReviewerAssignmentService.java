package vn.edu.topicregistration.service;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.springframework.context.annotation.Profile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import vn.edu.topicregistration.entity.CouncilTopicEntity;
import vn.edu.topicregistration.entity.NotificationEventType;
import vn.edu.topicregistration.entity.NotificationTargetType;
import vn.edu.topicregistration.entity.RegistrationEntity;
import vn.edu.topicregistration.entity.RegistrationMemberEntity;
import vn.edu.topicregistration.entity.RegistrationPeriodEntity;
import vn.edu.topicregistration.entity.RegistrationStatus;
import vn.edu.topicregistration.entity.ReviewerAssignmentEntity;
import vn.edu.topicregistration.entity.ReviewerEvaluationEntity;
import vn.edu.topicregistration.entity.ReviewerReportEntity;
import vn.edu.topicregistration.entity.RoleCode;
import vn.edu.topicregistration.entity.TopicSupervisorEntity;
import vn.edu.topicregistration.entity.UserAccountEntity;
import vn.edu.topicregistration.exception.ApplicationErrorCode;
import vn.edu.topicregistration.exception.ApplicationException;
import vn.edu.topicregistration.repository.CouncilMemberRepository;
import vn.edu.topicregistration.repository.CouncilTopicRepository;
import vn.edu.topicregistration.repository.RegistrationMemberRepository;
import vn.edu.topicregistration.repository.RegistrationPeriodRepository;
import vn.edu.topicregistration.repository.RegistrationRepository;
import vn.edu.topicregistration.repository.ReportFileRepository;
import vn.edu.topicregistration.repository.ReviewerAssignmentRepository;
import vn.edu.topicregistration.repository.ReviewerEvaluationRepository;
import vn.edu.topicregistration.repository.ReviewerReportRepository;
import vn.edu.topicregistration.repository.UserAccountRepository;
import vn.edu.topicregistration.security.AuthorizationPolicy;
import vn.edu.topicregistration.security.AuthorizationPolicy.ActorIdentity;
import vn.edu.topicregistration.util.ProjectTime;
import vn.edu.topicregistration.service.NotificationService.EmissionCommand;

@Service
@Profile("!test")
public class ReviewerAssignmentService {
    private static final List<ReviewerAssignmentEntity.Status> ACTIVE = List.of(
            ReviewerAssignmentEntity.Status.ASSIGNED,
            ReviewerAssignmentEntity.Status.SUBMITTED);
    private static final BigDecimal SCORE_STEP = new BigDecimal("0.25");

    private final RegistrationPeriodRepository periods;
    private final RegistrationRepository registrations;
    private final RegistrationMemberRepository members;
    private final UserAccountRepository accounts;
    private final CouncilTopicRepository councilTopics;
    private final CouncilMemberRepository councilMembers;
    private final ReviewerAssignmentRepository assignments;
    private final ReviewerReportRepository reports;
    private final ReviewerEvaluationRepository evaluations;
    private final ReportFileRepository files;
    private final AuthorizationPolicy authorization;
    private final ProjectTime time;
    private final NotificationService notifications;

    public ReviewerAssignmentService(
            RegistrationPeriodRepository periods,
            RegistrationRepository registrations,
            RegistrationMemberRepository members,
            UserAccountRepository accounts,
            CouncilTopicRepository councilTopics,
            CouncilMemberRepository councilMembers,
            ReviewerAssignmentRepository assignments,
            ReviewerReportRepository reports,
            ReviewerEvaluationRepository evaluations,
            ReportFileRepository files,
            AuthorizationPolicy authorization,
            ProjectTime time,
            NotificationService notifications) {
        this.periods = periods;
        this.registrations = registrations;
        this.members = members;
        this.accounts = accounts;
        this.councilTopics = councilTopics;
        this.councilMembers = councilMembers;
        this.assignments = assignments;
        this.reports = reports;
        this.evaluations = evaluations;
        this.files = files;
        this.authorization = authorization;
        this.time = time;
        this.notifications = notifications;
    }

    @Transactional(readOnly = true)
    public DeanWorkspace deanWorkspace() {
        ActorIdentity dean = authorization.requireRole(RoleCode.DEAN);
        if (dean.facultyId() == null) {
            throw new AccessDeniedException("Dean has no faculty");
        }
        List<RegistrationOption> options = new ArrayList<>();
        for (RegistrationPeriodEntity period : periods
                .findAllByFacultyIdOrderByLecturerStartDesc(dean.facultyId())) {
            if (!supported(period)) {
                continue;
            }
            for (RegistrationEntity registration : registrations
                    .findAllByPeriodIdOrderBySubmittedAtDescIdDesc(period.getId())) {
                if (registration.getStatus() != RegistrationStatus.ACCEPTED) {
                    continue;
                }
                ReviewerAssignmentEntity current = assignments
                        .findActiveByRegistrationId(registration.getId(), ACTIVE)
                        .orElse(null);
                options.add(new RegistrationOption(
                        registration.getId(), registration.getVersion(),
                        period.getName(), period.getTopicType(),
                        registration.getTopic().getTitle(),
                        current == null ? null : assignmentView(current),
                        candidates(registration)));
            }
        }
        return new DeanWorkspace(options);
    }

    @Transactional(readOnly = true)
    public List<ReviewerTask> reviewerTasks() {
        ActorIdentity reviewer = authorization.requireRole(RoleCode.LECTURER);
        if (reviewer.roles().contains(RoleCode.DEAN)) {
            throw new AccessDeniedException("Dean cannot review");
        }
        return assignments.findAllByReviewerIdAndStatusInOrderByAssignedAtDescIdDesc(
                        reviewer.accountId(), ACTIVE)
                .stream()
                .filter(assignment -> Objects.equals(
                        reviewer.facultyId(), assignment.getRegistration()
                                .getPeriod().getFaculty().getId()))
                .map(assignment -> new ReviewerTask(
                        assignment.getId(),
                        assignment.getRegistration().getTopic().getTitle(),
                        assignment.getRegistration().getPeriod().getName(),
                        assignment.getDeadlineDate(), assignment.getStatus()))
                .toList();
    }

    @Transactional(readOnly = true)
    public SubmissionWorkspace submissionWorkspace(long assignmentId) {
        ReviewerAssignmentEntity assignment = assignments.findDetailedById(assignmentId)
                .orElseThrow(ReviewerAssignmentService::missing);
        requireReviewer(assignment);
        List<StudentRow> rows = members.findAllByRegistrationId(
                        assignment.getRegistration().getId())
                .stream()
                .map(member -> new StudentRow(member.getStudent().getId(),
                        displayName(member.getStudent())))
                .toList();
        RegistrationEntity registration = assignment.getRegistration();
        return new SubmissionWorkspace(
                assignment.getId(), assignment.getVersion(),
                registration.getTopic().getTitle(), registration.getPeriod().getName(),
                assignment.getDeadlineDate(), assignment.getStatus(), rows,
                assignment.getStatus() == ReviewerAssignmentEntity.Status.ASSIGNED
                        && !time.now().isAfter(time.toEndOfDay(assignment.getDeadlineDate())));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public long assign(AssignCommand command) {
        Objects.requireNonNull(command, "command");
        LockedRegistration locked = lockRegistration(command.registrationId());
        RegistrationEntity registration = locked.registration();
        ActorIdentity dean = authorization.requireDeanOfFaculty(
                locked.period().getFaculty().getId());
        requireAssignable(registration, locked.period());
        requireVersion(registration.getVersion(), command.version());
        if (assignments.findActiveByRegistrationIdForUpdate(registration.getId(), ACTIVE)
                .isPresent()) {
            throw invalid("Đăng ký đã có GVPB đang hoạt động.");
        }
        UserAccountEntity reviewer = eligibleReviewer(registration, command.reviewerId());
        UserAccountEntity actor = accounts.findById(dean.accountId())
                .orElseThrow(ReviewerAssignmentService::missing);
        ReviewerAssignmentEntity saved = assignments.saveAndFlush(new ReviewerAssignmentEntity(
                registration, reviewer, locked.period().getReviewerScoreDeadline(),
                actor, time.now()));
        notifyReviewer(saved, dean.accountId());
        return saved.getId();
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public long replace(long assignmentId, AssignCommand command) {
        Objects.requireNonNull(command, "command");
        ReviewerAssignmentEntity snapshot = assignments.findDetailedById(assignmentId)
                .orElseThrow(ReviewerAssignmentService::missing);
        if (!Objects.equals(snapshot.getRegistration().getId(), command.registrationId())) {
            throw invalid("Phân công không thuộc đăng ký đã chọn.");
        }
        LockedRegistration locked = lockRegistration(command.registrationId());
        RegistrationEntity registration = locked.registration();
        ActorIdentity dean = authorization.requireDeanOfFaculty(
                locked.period().getFaculty().getId());
        requireAssignable(registration, locked.period());
        ReviewerAssignmentEntity current = assignments.findByIdForUpdate(assignmentId)
                .orElseThrow(ReviewerAssignmentService::missing);
        if (current.getStatus() != ReviewerAssignmentEntity.Status.ASSIGNED
                || reports.existsByAssignmentId(assignmentId)
                || evaluations.existsByAssignmentId(assignmentId)) {
            throw invalid("Chỉ thay GVPB trước khi có báo cáo hoặc điểm.");
        }
        requireVersion(current.getVersion(), command.version());
        if (Objects.equals(current.getReviewer().getId(), command.reviewerId())) {
            throw invalid("GVPB mới phải khác người hiện tại.");
        }
        UserAccountEntity reviewer = eligibleReviewer(registration, command.reviewerId());
        UserAccountEntity actor = accounts.findById(dean.accountId())
                .orElseThrow(ReviewerAssignmentService::missing);
        Instant now = time.now();
        current.markReplaced(actor, now);
        assignments.flush();
        ReviewerAssignmentEntity saved = assignments.saveAndFlush(new ReviewerAssignmentEntity(
                registration, reviewer, locked.period().getReviewerScoreDeadline(),
                actor, now));
        notifyReviewer(saved, dean.accountId());
        return saved.getId();
    }

    private void notifyReviewer(ReviewerAssignmentEntity assignment, long deanId) {
        notifications.emit(new EmissionCommand(
                NotificationEventType.REVIEWER_ASSIGNED,
                assignment.getId(),
                deanId,
                List.of(assignment.getReviewer().getId()),
                "Bạn được phân công phản biện đề tài",
                "Vui lòng xem nhiệm vụ, nộp báo cáo PDF và chấm điểm từng sinh viên đúng hạn.",
                null,
                NotificationTargetType.REVIEWER_ASSIGNMENT,
                assignment.getId()));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void submit(long assignmentId, SubmissionCommand command) {
        Objects.requireNonNull(command, "command");
        ReviewerAssignmentEntity snapshot = assignments.findDetailedById(assignmentId)
                .orElseThrow(ReviewerAssignmentService::missing);
        LockedRegistration locked = lockRegistration(snapshot.getRegistration().getId());
        ReviewerAssignmentEntity assignment = assignments.findByIdForUpdate(assignmentId)
                .orElseThrow(ReviewerAssignmentService::missing);
        ActorIdentity reviewer = requireReviewer(assignment);
        requireVersion(assignment.getVersion(), command.version());
        if (assignment.getStatus() != ReviewerAssignmentEntity.Status.ASSIGNED
                || locked.registration().getStatus() != RegistrationStatus.ACCEPTED
                || reports.existsByAssignmentId(assignmentId)
                || evaluations.existsByAssignmentId(assignmentId)) {
            throw invalid("Phân công không còn ở trạng thái có thể nộp.");
        }
        Instant now = time.now();
        if (assignment.getDeadlineDate() == null
                || now.isAfter(time.toEndOfDay(assignment.getDeadlineDate()))) {
            throw invalid("Đã quá hạn nộp phản biện.");
        }
        List<RegistrationMemberEntity> snapshotMembers = members.findAllByRegistrationId(
                locked.registration().getId());
        validateRows(snapshotMembers, command.scores());
        validateFile(command.file());
        UserAccountEntity submitter = accounts.findById(reviewer.accountId())
                .orElseThrow(ReviewerAssignmentService::missing);

        ReportFileRepository.StoredReportFile stored = null;
        try {
            stored = files.store(command.file().source().open(), command.file().sizeBytes());
            registerRollbackCleanup(stored.storageKey());
            for (ScoreRow row : command.scores()) {
                UserAccountEntity student = snapshotMembers.stream()
                        .filter(member -> Objects.equals(member.getStudent().getId(), row.studentId()))
                        .findFirst().orElseThrow().getStudent();
                ReviewerEvaluationEntity evaluation = new ReviewerEvaluationEntity(
                        assignment, student, row.score(), row.feedback());
                evaluation.submit(now);
                evaluations.save(evaluation);
            }
            evaluations.flush();
            reports.saveAndFlush(new ReviewerReportEntity(
                    assignment, safeFileName(command.file().originalFileName()),
                    stored.storageKey(), "application/pdf", stored.sizeBytes(),
                    submitter, now));
            assignment.markSubmitted(now);
            assignments.flush();
        } catch (IOException exception) {
            if (stored != null) {
                files.delete(stored.storageKey());
            }
            throw new ApplicationException(ApplicationErrorCode.REPORT_STORAGE_FAILED);
        } catch (RuntimeException exception) {
            if (stored != null) {
                files.delete(stored.storageKey());
            }
            throw exception;
        }
    }

    private LockedRegistration lockRegistration(long registrationId) {
        long periodId = registrations.findPeriodIdByRegistrationId(registrationId)
                .orElseThrow(ReviewerAssignmentService::missing);
        RegistrationPeriodEntity period = periods.findByIdForUpdate(periodId)
                .orElseThrow(ReviewerAssignmentService::missing);
        RegistrationEntity registration = registrations.findByIdForUpdate(registrationId)
                .orElseThrow(ReviewerAssignmentService::missing);
        if (!Objects.equals(registration.getPeriod().getId(), period.getId())) {
            throw new ApplicationException(ApplicationErrorCode.CONFLICT);
        }
        return new LockedRegistration(period, registration);
    }

    private void requireAssignable(
            RegistrationEntity registration, RegistrationPeriodEntity period) {
        if (!supported(period) || registration.getStatus() != RegistrationStatus.ACCEPTED
                || period.getReviewerScoreDeadline() == null
                || time.now().isAfter(time.toEndOfDay(period.getReviewerScoreDeadline()))) {
            throw invalid("Chỉ phân công đăng ký TLCN/KLTN đã duyệt và còn hạn phản biện.");
        }
    }

    private static boolean supported(RegistrationPeriodEntity period) {
        return "TLCN".equals(period.getTopicType()) || "KLTN".equals(period.getTopicType());
    }

    private UserAccountEntity eligibleReviewer(RegistrationEntity registration, long reviewerId) {
        UserAccountEntity reviewer = accounts.findByIdForUpdate(reviewerId)
                .orElseThrow(() -> invalid("Giảng viên phản biện không hợp lệ."));
        if (!reviewer.isEnabled() || !reviewer.getRoleCodes().contains(RoleCode.LECTURER)
                || reviewer.getRoleCodes().contains(RoleCode.DEAN)
                || reviewer.getFaculty() == null
                || !Objects.equals(reviewer.getFaculty().getId(),
                        registration.getPeriod().getFaculty().getId())
                || excludedIds(registration).contains(reviewerId)) {
            throw invalid("GVPB phải là giảng viên cùng Khoa, không phải GVHD hoặc người chấm.");
        }
        return reviewer;
    }

    private List<ReviewerCandidate> candidates(RegistrationEntity registration) {
        Set<Long> excluded = excludedIds(registration);
        return accounts.findEnabledByFacultyAndRole(
                        registration.getPeriod().getFaculty().getId(), RoleCode.LECTURER)
                .stream()
                .filter(account -> !excluded.contains(account.getId()))
                .map(account -> new ReviewerCandidate(account.getId(), displayName(account)))
                .toList();
    }

    private Set<Long> excludedIds(RegistrationEntity registration) {
        Set<Long> excluded = new HashSet<>();
        for (TopicSupervisorEntity supervisor : registration.getTopic().getSupervisors()) {
            excluded.add(supervisor.getLecturer().getId());
        }
        CouncilTopicEntity councilTopic = councilTopics.findByRegistrationId(registration.getId())
                .orElse(null);
        if (councilTopic != null) {
            councilMembers.findAllByCouncilIdOrderBySlot(councilTopic.getCouncil().getId())
                    .forEach(member -> excluded.add(member.getLecturer().getId()));
        }
        return excluded;
    }

    private ActorIdentity requireReviewer(ReviewerAssignmentEntity assignment) {
        ActorIdentity actor = authorization.requireLecturerOfFaculty(
                assignment.getRegistration().getPeriod().getFaculty().getId());
        if (actor.roles().contains(RoleCode.DEAN)
                || !Objects.equals(actor.accountId(), assignment.getReviewer().getId())
                || assignment.getStatus() == ReviewerAssignmentEntity.Status.REPLACED
                || assignment.getStatus() == ReviewerAssignmentEntity.Status.LEGACY_UNVERIFIED) {
            throw new AccessDeniedException("Reviewer assignment is outside actor scope");
        }
        return actor;
    }

    private static void validateRows(
            List<RegistrationMemberEntity> members, List<ScoreRow> rows) {
        if (rows == null || rows.size() != members.size() || rows.isEmpty()) {
            throw invalid("Phải nhập điểm cho đủ mọi sinh viên trong đăng ký.");
        }
        Set<Long> expected = new HashSet<>();
        members.forEach(member -> expected.add(member.getStudent().getId()));
        Set<Long> actual = new HashSet<>();
        for (ScoreRow row : rows) {
            if (row == null || row.studentId() == null || !actual.add(row.studentId())
                    || !expected.contains(row.studentId()) || !validScore(row.score())
                    || row.feedback() == null || row.feedback().isBlank()) {
                throw invalid("Điểm, nhận xét hoặc sinh viên chưa hợp lệ.");
            }
        }
        if (!actual.equals(expected)) {
            throw invalid("Phải nhập điểm cho đủ mọi sinh viên trong đăng ký.");
        }
    }

    private static boolean validScore(BigDecimal score) {
        return score != null && score.scale() <= 2
                && score.compareTo(BigDecimal.ZERO) >= 0
                && score.compareTo(BigDecimal.TEN) <= 0
                && score.remainder(SCORE_STEP).compareTo(BigDecimal.ZERO) == 0;
    }

    private static void validateFile(ReportUpload file) {
        if (file == null || file.source() == null
                || !"application/pdf".equalsIgnoreCase(file.contentType())
                || file.sizeBytes() < 1 || file.sizeBytes() > ReportRules.MAX_SIZE_BYTES) {
            throw invalid("Chỉ chấp nhận PDF không quá 20 MB.");
        }
        try (InputStream input = file.source().open()) {
            byte[] prefix = input.readNBytes(5);
            if (prefix.length != 5 || !"%PDF-".equals(
                    new String(prefix, java.nio.charset.StandardCharsets.US_ASCII))) {
                throw invalid("Nội dung file không phải PDF.");
            }
        } catch (IOException exception) {
            throw invalid("Không thể đọc file PDF.");
        }
    }

    private void registerRollbackCleanup(String storageKey) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != TransactionSynchronization.STATUS_COMMITTED) {
                    files.delete(storageKey);
                }
            }
        });
    }

    private static String safeFileName(String name) {
        String candidate = name == null ? "" : name.replace('\\', '/');
        candidate = candidate.substring(candidate.lastIndexOf('/') + 1)
                .replaceAll("[\\p{Cntrl}]", "").strip();
        if (candidate.isEmpty()) {
            return "review.pdf";
        }
        if (!candidate.toLowerCase(java.util.Locale.ROOT).endsWith(".pdf")) {
            candidate += ".pdf";
        }
        return candidate.length() <= 500 ? candidate : candidate.substring(0, 496) + ".pdf";
    }

    private static String displayName(UserAccountEntity account) {
        return account.getFullName() == null || account.getFullName().isBlank()
                ? account.getUsername() : account.getFullName();
    }

    private static AssignmentView assignmentView(ReviewerAssignmentEntity assignment) {
        return new AssignmentView(assignment.getId(),
                displayName(assignment.getReviewer()), assignment.getStatus(),
                assignment.getVersion());
    }

    private static void requireVersion(long actual, Long expected) {
        if (expected == null || actual != expected) {
            throw new ApplicationException(ApplicationErrorCode.CONFLICT);
        }
    }

    private static ApplicationException missing() {
        return new ApplicationException(ApplicationErrorCode.RESOURCE_NOT_FOUND);
    }

    private static ReviewerValidationException invalid(String message) {
        return new ReviewerValidationException(message);
    }

    private record LockedRegistration(RegistrationPeriodEntity period,
                                      RegistrationEntity registration) { }

    public record AssignCommand(long registrationId, long reviewerId, Long version) { }
    public record ReviewerCandidate(long id, String name) { }
    public record AssignmentView(long id, String reviewerName,
                                 ReviewerAssignmentEntity.Status status, long version) { }
    public record RegistrationOption(long id, long version, String periodName,
                                     String topicType, String topicTitle,
                                     AssignmentView assignment,
                                     List<ReviewerCandidate> candidates) { }
    public record DeanWorkspace(List<RegistrationOption> registrations) { }
    public record ReviewerTask(long id, String topicTitle, String periodName,
                               LocalDate deadline, ReviewerAssignmentEntity.Status status) { }
    public record StudentRow(long id, String name) { }
    public record SubmissionWorkspace(long assignmentId, long version, String topicTitle,
                                      String periodName, LocalDate deadline,
                                      ReviewerAssignmentEntity.Status status,
                                      List<StudentRow> students, boolean canSubmit) { }
    public record ScoreRow(Long studentId, BigDecimal score, String feedback) { }
    public record ReportUpload(String originalFileName, String contentType,
                               long sizeBytes, FileSource source) { }
    public record SubmissionCommand(Long version, ReportUpload file, List<ScoreRow> scores) { }

    @FunctionalInterface
    public interface FileSource { InputStream open() throws IOException; }

    public static class ReviewerValidationException extends RuntimeException {
        public ReviewerValidationException(String message) { super(message); }
    }
}
