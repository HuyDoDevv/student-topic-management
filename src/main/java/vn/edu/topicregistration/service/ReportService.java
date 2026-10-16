package vn.edu.topicregistration.service;

import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

import org.springframework.context.annotation.Profile;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import vn.edu.topicregistration.entity.CouncilStatus;
import vn.edu.topicregistration.entity.CouncilTopicEntity;
import vn.edu.topicregistration.entity.ProjectReportEntity;
import vn.edu.topicregistration.entity.RegistrationEntity;
import vn.edu.topicregistration.entity.RegistrationPeriodEntity;
import vn.edu.topicregistration.entity.RegistrationStatus;
import vn.edu.topicregistration.entity.RoleCode;
import vn.edu.topicregistration.entity.TopicSupervisorEntity;
import vn.edu.topicregistration.entity.UserAccountEntity;
import vn.edu.topicregistration.exception.ApplicationErrorCode;
import vn.edu.topicregistration.exception.ApplicationException;
import vn.edu.topicregistration.repository.CouncilMemberRepository;
import vn.edu.topicregistration.repository.CouncilTopicRepository;
import vn.edu.topicregistration.repository.ProjectReportRepository;
import vn.edu.topicregistration.repository.RegistrationMemberRepository;
import vn.edu.topicregistration.repository.RegistrationPeriodRepository;
import vn.edu.topicregistration.repository.RegistrationRepository;
import vn.edu.topicregistration.repository.ReportFileRepository;
import vn.edu.topicregistration.repository.ReportFileRepository.StoredReportFile;
import vn.edu.topicregistration.repository.UserAccountRepository;
import vn.edu.topicregistration.security.AuthorizationPolicy;
import vn.edu.topicregistration.security.AuthorizationPolicy.ActorIdentity;
import vn.edu.topicregistration.service.ReportRules.ReportRuleException;
import vn.edu.topicregistration.service.ReportRules.UploadContext;
import vn.edu.topicregistration.service.ReportRules.ViewContext;

@Service
@Profile("!test")
public class ReportService {
    private static final int SIGNATURE_LENGTH = 5;

    private final RegistrationPeriodRepository periods;
    private final RegistrationRepository registrations;
    private final RegistrationMemberRepository members;
    private final ProjectReportRepository reports;
    private final CouncilTopicRepository councilTopics;
    private final CouncilMemberRepository councilMembers;
    private final UserAccountRepository accounts;
    private final AuthorizationPolicy authorization;
    private final ReportFileRepository files;
    private final Clock clock;

    public ReportService(
            RegistrationPeriodRepository periods,
            RegistrationRepository registrations,
            RegistrationMemberRepository members,
            ProjectReportRepository reports,
            CouncilTopicRepository councilTopics,
            CouncilMemberRepository councilMembers,
            UserAccountRepository accounts,
            AuthorizationPolicy authorization,
            ReportFileRepository files,
            Clock clock) {
        this.periods = periods;
        this.registrations = registrations;
        this.members = members;
        this.reports = reports;
        this.councilTopics = councilTopics;
        this.councilMembers = councilMembers;
        this.accounts = accounts;
        this.authorization = authorization;
        this.files = files;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public ReportWorkspace workspace(long registrationId) {
        RegistrationEntity registration = detailedRegistration(registrationId);
        AccessContext access = requireViewAccess(registration);
        ProjectReportEntity current = reports
                .findByRegistrationIdAndReplacedAtIsNull(registrationId)
                .orElse(null);
        return toWorkspace(registration, current, access);
    }

    @Transactional(readOnly = true)
    public boolean hasCurrentReportForVisibleRegistration(long registrationId) {
        return workspace(registrationId).report() != null;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ReportWorkspace upload(long registrationId, Long expectedVersion, ReportUpload upload) {
        Objects.requireNonNull(upload, "upload");
        long periodId = registrations.findPeriodIdByRegistrationId(registrationId)
                .orElseThrow(ReportService::missingResource);
        RegistrationPeriodEntity period = periods.findByIdForUpdate(periodId)
                .orElseThrow(ReportService::missingResource);
        RegistrationEntity registration = registrations.findByIdForUpdate(registrationId)
                .orElseThrow(ReportService::missingResource);
        if (!Objects.equals(registration.getPeriod().getId(), period.getId())) {
            throw new ApplicationException(ApplicationErrorCode.CONFLICT);
        }

        ActorIdentity actor = authorization.currentActor();
        boolean snapshotLeader = actor.roles().contains(RoleCode.STUDENT)
                && actor.accountId() != null
                && members.existsByRegistrationIdAndStudentIdAndLeaderAtSubmissionTrue(
                        registrationId,
                        actor.accountId());
        Optional<CouncilTopicEntity> assignment = councilTopics.findByRegistrationId(
                registrationId);
        CouncilStatus councilStatus = assignment
                .map(CouncilTopicEntity::getCouncil)
                .map(council -> council.getStatus())
                .orElse(null);
        byte[] signature = readSignature(upload);
        applyRules(() -> ReportRules.validateUpload(new UploadContext(
                registration.getStatus(),
                snapshotLeader,
                councilStatus,
                upload.contentType(),
                upload.sizeBytes(),
                signature)));

        ProjectReportEntity current = reports
                .findCurrentByRegistrationIdForUpdate(registrationId)
                .orElse(null);
        requireExpectedVersion(current, expectedVersion);
        UserAccountEntity submitter = accounts.findById(actor.accountId())
                .filter(UserAccountEntity::isEnabled)
                .orElseThrow(() -> new AccessDeniedException("Account is not available"));

        StoredReportFile stored = null;
        try {
            stored = store(upload);
            registerRollbackCleanup(stored.storageKey());
            Instant now = clock.instant();
            if (current != null) {
                current.markReplaced(now);
                reports.flush();
            }
            ProjectReportEntity saved = reports.saveAndFlush(new ProjectReportEntity(
                    registration,
                    submitter,
                    now,
                    safeFileName(upload.originalFileName()),
                    stored.storageKey(),
                    "application/pdf",
                    stored.sizeBytes()));
            AccessContext access = new AccessContext(true, councilStatus);
            return toWorkspace(registration, saved, access);
        } catch (RuntimeException exception) {
            if (stored != null) {
                files.delete(stored.storageKey());
            }
            throw exception;
        }
    }

    @Transactional(readOnly = true)
    public ReportDownload download(long registrationId) {
        RegistrationEntity registration = detailedRegistration(registrationId);
        requireViewAccess(registration);
        ProjectReportEntity report = reports
                .findByRegistrationIdAndReplacedAtIsNull(registrationId)
                .orElseThrow(() -> new ApplicationException(
                        ApplicationErrorCode.REPORT_NOT_FOUND));
        try {
            return new ReportDownload(
                    report.getFileName(),
                    report.getSizeBytes(),
                    files.open(report.getStorageKey()));
        } catch (IOException | IllegalArgumentException exception) {
            throw new ApplicationException(ApplicationErrorCode.REPORT_NOT_FOUND);
        }
    }

    private RegistrationEntity detailedRegistration(long registrationId) {
        return registrations.findDetailedById(registrationId)
                .orElseThrow(ReportService::missingResource);
    }

    private AccessContext requireViewAccess(RegistrationEntity registration) {
        ActorIdentity actor = authorization.currentActor();
        long registrationId = registration.getId();
        long facultyId = registration.getPeriod().getFaculty().getId();
        boolean snapshotMember = actor.roles().contains(RoleCode.STUDENT)
                && actor.accountId() != null
                && members.existsByRegistrationIdAndStudentId(
                        registrationId,
                        actor.accountId());
        boolean supervisor = actor.roles().contains(RoleCode.LECTURER)
                && registration.getTopic().getSupervisors().stream()
                .map(TopicSupervisorEntity::getLecturer)
                .map(UserAccountEntity::getId)
                .anyMatch(id -> Objects.equals(id, actor.accountId()));
        boolean scopedDean = actor.roles().contains(RoleCode.DEAN)
                && Objects.equals(actor.facultyId(), facultyId);
        Optional<CouncilTopicEntity> assignment = councilTopics.findByRegistrationId(
                registrationId);
        boolean assignedCouncilMember = actor.roles().contains(RoleCode.LECTURER)
                && assignment
                .map(item -> councilMembers.existsByCouncilIdAndLecturerId(
                        item.getCouncil().getId(),
                        actor.accountId()))
                .orElse(false);

        if (!ReportRules.canView(new ViewContext(
                snapshotMember,
                supervisor,
                scopedDean,
                assignedCouncilMember))) {
            throw new AccessDeniedException("Report is outside the actor scope");
        }
        if (registration.getStatus() != RegistrationStatus.ACCEPTED) {
            throw new ApplicationException(ApplicationErrorCode.INVALID_REGISTRATION_STATE);
        }

        boolean snapshotLeader = snapshotMember
                && members.existsByRegistrationIdAndStudentIdAndLeaderAtSubmissionTrue(
                        registrationId,
                        actor.accountId());
        CouncilStatus councilStatus = assignment
                .map(CouncilTopicEntity::getCouncil)
                .map(council -> council.getStatus())
                .orElse(null);
        return new AccessContext(snapshotLeader, councilStatus);
    }

    private ReportWorkspace toWorkspace(
            RegistrationEntity registration,
            ProjectReportEntity report,
            AccessContext access) {
        return new ReportWorkspace(
                registration.getId(),
                registration.getPeriod().getId(),
                registration.getGroup().getId(),
                registration.getTopic().getTitle(),
                report == null ? null : toView(report),
                ReportRules.isUploadOpen(
                        registration.getStatus(),
                        access.snapshotLeader(),
                        access.councilStatus()));
    }

    private static ReportView toView(ProjectReportEntity report) {
        return new ReportView(
                report.getId(),
                report.getFileName(),
                report.getSizeBytes(),
                displayName(report.getSubmittedBy()),
                report.getSubmittedAt(),
                report.getVersion());
    }

    private static String displayName(UserAccountEntity account) {
        return account.getFullName() == null || account.getFullName().isBlank()
                ? account.getUsername()
                : account.getFullName();
    }

    private static void requireExpectedVersion(
            ProjectReportEntity current,
            Long expectedVersion) {
        if (current == null && expectedVersion != null) {
            throw new OptimisticLockingFailureException("Report no longer exists");
        }
        if (current != null
                && (expectedVersion == null || current.getVersion() != expectedVersion)) {
            throw new OptimisticLockingFailureException("Report version is stale");
        }
    }

    private byte[] readSignature(ReportUpload upload) {
        if (upload.source() == null) {
            throw new ApplicationException(ApplicationErrorCode.INVALID_REPORT_FILE);
        }
        try (InputStream input = upload.source().open()) {
            return input.readNBytes(SIGNATURE_LENGTH);
        } catch (IOException exception) {
            throw new ApplicationException(ApplicationErrorCode.INVALID_REPORT_FILE);
        }
    }

    private StoredReportFile store(ReportUpload upload) {
        try {
            return files.store(upload.source().open(), upload.sizeBytes());
        } catch (IllegalArgumentException exception) {
            throw new ApplicationException(ApplicationErrorCode.INVALID_REPORT_FILE);
        } catch (IOException exception) {
            throw new ApplicationException(ApplicationErrorCode.REPORT_STORAGE_FAILED);
        }
    }

    private void registerRollbackCleanup(String storageKey) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCompletion(int status) {
                        if (status != TransactionSynchronization.STATUS_COMMITTED) {
                            files.delete(storageKey);
                        }
                    }
                });
    }

    private static String safeFileName(String originalFileName) {
        String candidate = originalFileName == null
                ? ""
                : originalFileName.replace('\\', '/');
        int lastSeparator = candidate.lastIndexOf('/');
        if (lastSeparator >= 0) {
            candidate = candidate.substring(lastSeparator + 1);
        }
        candidate = candidate.replaceAll("[\\p{Cntrl}]", "").strip();
        if (candidate.isEmpty()) {
            return "report.pdf";
        }
        if (!candidate.toLowerCase(java.util.Locale.ROOT).endsWith(".pdf")) {
            candidate += ".pdf";
        }
        if (candidate.length() > 500) {
            candidate = candidate.substring(0, 496) + ".pdf";
        }
        return candidate;
    }

    private void applyRules(RuleOperation operation) {
        try {
            operation.run();
        } catch (ReportRuleException exception) {
            switch (exception.failure()) {
                case ACCESS_DENIED -> throw new AccessDeniedException("Access denied");
                case INVALID_REGISTRATION_STATE -> throw new ApplicationException(
                        ApplicationErrorCode.INVALID_REGISTRATION_STATE);
                case INVALID_FILE -> throw new ApplicationException(
                        ApplicationErrorCode.INVALID_REPORT_FILE);
                case REPORT_LOCKED -> throw new ApplicationException(
                        ApplicationErrorCode.REPORT_LOCKED);
            }
            throw new IllegalStateException("Unknown report rule failure", exception);
        }
    }

    private static ApplicationException missingResource() {
        return new ApplicationException(ApplicationErrorCode.RESOURCE_NOT_FOUND);
    }

    @FunctionalInterface
    private interface RuleOperation {
        void run();
    }

    private record AccessContext(boolean snapshotLeader, CouncilStatus councilStatus) {
    }

    @FunctionalInterface
    public interface InputStreamOpener {
        InputStream open() throws IOException;
    }

    public record ReportUpload(
            String originalFileName,
            String contentType,
            long sizeBytes,
            InputStreamOpener source) {
    }

    public record ReportView(
            Long id,
            String fileName,
            long sizeBytes,
            String submittedBy,
            Instant submittedAt,
            long version) {
    }

    public record ReportWorkspace(
            long registrationId,
            long periodId,
            long groupId,
            String topicTitle,
            ReportView report,
            boolean canUpload) {
    }

    public record ReportDownload(String fileName, long sizeBytes, InputStream content) {
    }
}
