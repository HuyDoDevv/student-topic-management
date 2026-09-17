package vn.edu.topicregistration.unit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import vn.edu.topicregistration.entity.RegistrationStatus;
import vn.edu.topicregistration.service.DirectGradingRules;
import vn.edu.topicregistration.service.DirectGradingRules.AssignmentContext;
import vn.edu.topicregistration.service.DirectGradingRules.DirectGradingRuleException;
import vn.edu.topicregistration.service.DirectGradingRules.Failure;

class DirectGradingRulesTest {
    private static final AssignmentContext VALID = new AssignmentContext(
            "COURSE",
            RegistrationStatus.ACCEPTED,
            true,
            false,
            true,
            true,
            true,
            false);

    @Test
    void acceptsCourseAndResearchAssignmentsWithOneIndependentLecturer() {
        assertDoesNotThrow(() -> DirectGradingRules.validate(VALID));
        assertDoesNotThrow(() -> DirectGradingRules.validate(
                VALID.withTopicType("NCKH")));
    }

    @Test
    void rejectsThesisTypesBecauseTheyKeepTheCouncilWorkflow() {
        assertFailure(Failure.UNSUPPORTED_TYPE,
                VALID.withTopicType("TLCN"));
        assertFailure(Failure.UNSUPPORTED_TYPE,
                VALID.withTopicType("KLTN"));
    }

    @Test
    void requiresAcceptedRegistrationCurrentReportAndNoPreviousAssignment() {
        assertFailure(Failure.INVALID_REGISTRATION,
                VALID.withRegistrationStatus(RegistrationStatus.PENDING));
        assertFailure(Failure.REPORT_REQUIRED,
                VALID.withReportPresent(false));
        assertFailure(Failure.ALREADY_ASSIGNED,
                VALID.withAlreadyAssigned(true));
    }

    @Test
    void rejectsSupervisorDisabledOutsiderAndNonLecturer() {
        assertFailure(Failure.INVALID_GRADER,
                VALID.withGraderEnabled(false));
        assertFailure(Failure.INVALID_GRADER,
                VALID.withGraderLecturer(false));
        assertFailure(Failure.INVALID_GRADER,
                VALID.withSameDepartment(false));
        assertFailure(Failure.SUPERVISOR_CONFLICT,
                VALID.withSupervisor(true));
    }

    private static void assertFailure(Failure failure, AssignmentContext context) {
        DirectGradingRuleException exception = assertThrows(
                DirectGradingRuleException.class,
                () -> DirectGradingRules.validate(context));
        assertEquals(failure, exception.failure());
    }
}
