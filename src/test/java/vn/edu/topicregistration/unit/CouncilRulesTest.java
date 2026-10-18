package vn.edu.topicregistration.unit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.time.Instant;

import org.junit.jupiter.api.Test;

import vn.edu.topicregistration.entity.CouncilStatus;
import vn.edu.topicregistration.entity.RegistrationStatus;
import vn.edu.topicregistration.service.CouncilRules;
import vn.edu.topicregistration.service.CouncilRules.AssignmentContext;
import vn.edu.topicregistration.service.CouncilRules.CouncilRuleException;
import vn.edu.topicregistration.service.CouncilRules.Failure;
import vn.edu.topicregistration.service.CouncilRules.MemberCandidate;
import vn.edu.topicregistration.service.CouncilRules.TransitionContext;

class CouncilRulesTest {
    private static final long FACULTY_ID = 4L;

    @Test
    void scheduleMustBeStrictlyLaterThanServerInstant() {
        Instant now = Instant.parse("2026-09-14T08:00:00Z");
        assertEquals(false, CouncilRules.isFutureSchedule(now.minusNanos(1), now));
        assertEquals(false, CouncilRules.isFutureSchedule(now, now));
        assertEquals(true, CouncilRules.isFutureSchedule(now.plusNanos(1), now));
        assertEquals(false, CouncilRules.isFutureSchedule(null, now));
    }

    @Test
    void acceptsThreeToFiveDistinctLecturersForKltnOnly() {
        assertDoesNotThrow(() -> CouncilRules.validateCreation(
                "KLTN", FACULTY_ID, 11L, 12L, validMembers(3)));
        assertDoesNotThrow(() -> CouncilRules.validateCreation(
                "KLTN", FACULTY_ID, 11L, 12L, validMembers(5)));
    }

    @Test
    void rejectsInvalidCardinalityDuplicateOfficerRoleFacultyAndPeriodType() {
        assertFailure(Failure.INVALID_MEMBERS, () -> CouncilRules.validateCreation(
                "KLTN", FACULTY_ID, 11L, 12L, validMembers(2)));
        assertFailure(Failure.INVALID_MEMBERS, () -> CouncilRules.validateCreation(
                "KLTN", FACULTY_ID, 11L, 12L, validMembers(6)));
        assertFailure(Failure.INVALID_MEMBERS, () -> CouncilRules.validateCreation(
                "KLTN", FACULTY_ID, 11L, 11L, validMembers(3)));
        assertFailure(Failure.INVALID_MEMBERS, () -> CouncilRules.validateCreation(
                "KLTN", FACULTY_ID, 11L, 12L, List.of(
                        candidate(11L), candidate(12L), candidate(12L))));
        assertFailure(Failure.INVALID_MEMBERS, () -> CouncilRules.validateCreation(
                "KLTN", FACULTY_ID, 11L, 12L, List.of(
                        candidate(11L), candidate(12L),
                        new MemberCandidate(13L, FACULTY_ID, false, true))));
        assertFailure(Failure.INVALID_MEMBERS, () -> CouncilRules.validateCreation(
                "KLTN", FACULTY_ID, 11L, 12L, List.of(
                        candidate(11L), candidate(12L),
                        new MemberCandidate(13L, FACULTY_ID, true, false))));
        assertFailure(Failure.INVALID_MEMBERS, () -> CouncilRules.validateCreation(
                "KLTN", FACULTY_ID, 11L, 12L, List.of(
                        candidate(11L), candidate(12L),
                        new MemberCandidate(13L, 99L, true, true))));
        assertFailure(Failure.INVALID_STATE, () -> CouncilRules.validateCreation(
                "COURSE", FACULTY_ID, 11L, 12L, validMembers(3)));
        assertFailure(Failure.INVALID_STATE, () -> CouncilRules.validateCreation(
                "TLCN", FACULTY_ID, 11L, 12L, validMembers(3)));
    }

    @Test
    void acceptsOnlyAnAcceptedSameScopeNonSupervisedAssignmentBeforeScheduling() {
        AssignmentContext valid = new AssignmentContext(
                CouncilStatus.DRAFT,
                "KLTN",
                RegistrationStatus.ACCEPTED,
                true,
                true,
                false,
                false);
        assertDoesNotThrow(() -> CouncilRules.validateAssignment(valid));

        assertAssignmentFailure(valid.withRegistrationStatus(RegistrationStatus.PENDING));
        assertAssignmentFailure(valid.withSamePeriod(false));
        assertAssignmentFailure(valid.withSameFaculty(false));
        assertAssignmentFailure(valid.withAssignedElsewhere(true));
        assertAssignmentFailure(valid.withSupervisorConflict(true));
        assertAssignmentFailure(valid.withReviewerConflict(true));
        assertAssignmentFailure(valid.withTopicType("NCKH"));
        assertAssignmentFailure(valid.withTopicType("TLCN"));
        assertFailure(Failure.INVALID_STATE, () -> CouncilRules.validateAssignment(
                valid.withCouncilStatus(CouncilStatus.SCHEDULED)));
    }

    @Test
    void enforcesEveryLifecycleStepAndActivationRevalidation() {
        assertDoesNotThrow(() -> CouncilRules.validateTransition(new TransitionContext(
                CouncilStatus.DRAFT, CouncilStatus.ASSIGNED,
                3, 1, false, true, false, false)));
        assertDoesNotThrow(() -> CouncilRules.validateTransition(new TransitionContext(
                CouncilStatus.ASSIGNED, CouncilStatus.SCHEDULED,
                3, 2, true, true, false, false)));
        assertDoesNotThrow(() -> CouncilRules.validateTransition(new TransitionContext(
                CouncilStatus.SCHEDULED, CouncilStatus.ACTIVE,
                5, 2, true, true, false, true)));

        assertStateFailure(new TransitionContext(
                CouncilStatus.DRAFT, CouncilStatus.SCHEDULED,
                3, 1, true, true, false, true));
        assertStateFailure(new TransitionContext(
                CouncilStatus.DRAFT, CouncilStatus.ASSIGNED,
                2, 1, false, true, false, false));
        assertStateFailure(new TransitionContext(
                CouncilStatus.DRAFT, CouncilStatus.ASSIGNED,
                3, 0, false, true, false, false));
        assertStateFailure(new TransitionContext(
                CouncilStatus.ASSIGNED, CouncilStatus.SCHEDULED,
                3, 1, false, true, false, false));
        assertStateFailure(new TransitionContext(
                CouncilStatus.SCHEDULED, CouncilStatus.ACTIVE,
                3, 1, true, false, false, true));
        assertStateFailure(new TransitionContext(
                CouncilStatus.SCHEDULED, CouncilStatus.ACTIVE,
                3, 1, true, true, true, true));
        assertStateFailure(new TransitionContext(
                CouncilStatus.SCHEDULED, CouncilStatus.ACTIVE,
                3, 1, true, true, false, true, true));
        assertStateFailure(new TransitionContext(
                CouncilStatus.SCHEDULED, CouncilStatus.ACTIVE,
                3, 1, true, true, false, false));
    }

    private static List<MemberCandidate> validMembers(int count) {
        return java.util.stream.LongStream.range(11, 11 + count)
                .mapToObj(CouncilRulesTest::candidate)
                .toList();
    }

    private static MemberCandidate candidate(long id) {
        return new MemberCandidate(id, FACULTY_ID, true, true);
    }

    private static void assertAssignmentFailure(AssignmentContext context) {
        assertFailure(Failure.INVALID_ASSIGNMENT,
                () -> CouncilRules.validateAssignment(context));
    }

    private static void assertStateFailure(TransitionContext context) {
        assertFailure(Failure.INVALID_STATE,
                () -> CouncilRules.validateTransition(context));
    }

    private static void assertFailure(Failure expected, Runnable operation) {
        CouncilRuleException exception = assertThrows(
                CouncilRuleException.class, operation::run);
        assertEquals(expected, exception.failure());
    }
}
