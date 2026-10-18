package vn.edu.topicregistration.unit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.junit.jupiter.api.Test;

import vn.edu.topicregistration.entity.CouncilStatus;
import vn.edu.topicregistration.entity.RegistrationStatus;
import vn.edu.topicregistration.service.ReportRules;
import vn.edu.topicregistration.service.ReportRules.Failure;
import vn.edu.topicregistration.service.ReportRules.ReportRuleException;
import vn.edu.topicregistration.service.ReportRules.UploadContext;
import vn.edu.topicregistration.service.ReportRules.ViewContext;

class ReportRulesTest {
    private static final long MAX_SIZE = 20L * 1024 * 1024;
    private static final byte[] PDF_SIGNATURE = "%PDF-1.7".getBytes(java.nio.charset.StandardCharsets.US_ASCII);

    @Test
    void acceptsPdfAtLimitFromSnapshotLeaderBeforeCouncilIsActive() {
        assertDoesNotThrow(() -> ReportRules.validateUpload(new UploadContext(
                RegistrationStatus.ACCEPTED,
                true,
                CouncilStatus.SCHEDULED,
                "application/pdf",
                MAX_SIZE,
                PDF_SIGNATURE)));
    }

    @Test
    void rejectsWrongActorRegistrationFileTypeSizeAndSignature() {
        assertFailure(Failure.ACCESS_DENIED, new UploadContext(
                RegistrationStatus.ACCEPTED, false, null,
                "application/pdf", 10, PDF_SIGNATURE));
        assertFailure(Failure.INVALID_REGISTRATION_STATE, new UploadContext(
                RegistrationStatus.PENDING, true, null,
                "application/pdf", 10, PDF_SIGNATURE));
        assertFailure(Failure.INVALID_FILE, new UploadContext(
                RegistrationStatus.ACCEPTED, true, null,
                "text/plain", 10, PDF_SIGNATURE));
        assertFailure(Failure.INVALID_FILE, new UploadContext(
                RegistrationStatus.ACCEPTED, true, null,
                "application/pdf", MAX_SIZE + 1, PDF_SIGNATURE));
        assertFailure(Failure.INVALID_FILE, new UploadContext(
                RegistrationStatus.ACCEPTED, true, null,
                "application/pdf", 10, "not-pdf".getBytes()));
    }

    @Test
    void locksUploadFromActiveThroughPublished() {
        for (CouncilStatus status : Set.of(
                CouncilStatus.ACTIVE,
                CouncilStatus.COMPLETED,
                CouncilStatus.PUBLISHED)) {
            assertFailure(Failure.REPORT_LOCKED, new UploadContext(
                    RegistrationStatus.ACCEPTED, true, status,
                    "application/pdf", 10, PDF_SIGNATURE));
        }
    }

    @Test
    void permitsOnlyConfirmedRelationshipsToView() {
        assertTrue(ReportRules.canView(new ViewContext(true, false, false, false)));
        assertTrue(ReportRules.canView(new ViewContext(false, true, false, false)));
        assertTrue(ReportRules.canView(new ViewContext(false, false, true, false)));
        assertTrue(ReportRules.canView(new ViewContext(false, false, false, true)));
        assertEquals(false,
                ReportRules.canView(new ViewContext(false, false, false, false)));
    }

    private static void assertFailure(Failure failure, UploadContext context) {
        ReportRuleException exception = assertThrows(
                ReportRuleException.class,
                () -> ReportRules.validateUpload(context));
        assertEquals(failure, exception.failure());
    }
}
