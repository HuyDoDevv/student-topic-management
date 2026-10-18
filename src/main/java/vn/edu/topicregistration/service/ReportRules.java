package vn.edu.topicregistration.service;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

import vn.edu.topicregistration.entity.CouncilStatus;
import vn.edu.topicregistration.entity.RegistrationStatus;

public final class ReportRules {
    public static final long MAX_SIZE_BYTES = 20L * 1024 * 1024;
    private static final byte[] PDF_PREFIX = "%PDF-".getBytes(StandardCharsets.US_ASCII);

    private ReportRules() {
    }

    public static void validateUpload(UploadContext context) {
        Objects.requireNonNull(context, "context");
        if (!context.snapshotLeader()) {
            throw failure(Failure.ACCESS_DENIED);
        }
        if (context.registrationStatus() != RegistrationStatus.ACCEPTED) {
            throw failure(Failure.INVALID_REGISTRATION_STATE);
        }
        if (isLocked(context.councilStatus())) {
            throw failure(Failure.REPORT_LOCKED);
        }
        if (!"application/pdf".equalsIgnoreCase(context.contentType())
                || context.sizeBytes() < 1
                || context.sizeBytes() > MAX_SIZE_BYTES
                || !hasPdfSignature(context.signature())) {
            throw failure(Failure.INVALID_FILE);
        }
    }

    public static boolean canView(ViewContext context) {
        Objects.requireNonNull(context, "context");
        return context.snapshotMember()
                || context.supervisor()
                || context.scopedDean()
                || context.assignedCouncilMember();
    }

    public static boolean isUploadOpen(
            RegistrationStatus registrationStatus,
            boolean snapshotLeader,
            CouncilStatus councilStatus) {
        return registrationStatus == RegistrationStatus.ACCEPTED
                && snapshotLeader
                && !isLocked(councilStatus);
    }

    private static boolean hasPdfSignature(byte[] signature) {
        if (signature == null || signature.length < PDF_PREFIX.length) {
            return false;
        }
        for (int index = 0; index < PDF_PREFIX.length; index++) {
            if (signature[index] != PDF_PREFIX[index]) {
                return false;
            }
        }
        return true;
    }

    private static boolean isLocked(CouncilStatus status) {
        return status == CouncilStatus.ACTIVE
                || status == CouncilStatus.COMPLETED
                || status == CouncilStatus.PUBLISHED;
    }

    private static ReportRuleException failure(Failure failure) {
        return new ReportRuleException(failure);
    }

    public record UploadContext(
            RegistrationStatus registrationStatus,
            boolean snapshotLeader,
            CouncilStatus councilStatus,
            String contentType,
            long sizeBytes,
            byte[] signature) {
    }

    public record ViewContext(
            boolean snapshotMember,
            boolean supervisor,
            boolean scopedDean,
            boolean assignedCouncilMember) {
    }

    public enum Failure {
        ACCESS_DENIED,
        INVALID_REGISTRATION_STATE,
        INVALID_FILE,
        REPORT_LOCKED
    }

    public static final class ReportRuleException extends RuntimeException {
        private final Failure failure;

        public ReportRuleException(Failure failure) {
            super(Objects.requireNonNull(failure, "failure").name());
            this.failure = failure;
        }

        public Failure failure() {
            return failure;
        }
    }
}
