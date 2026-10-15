package vn.edu.topicregistration.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import vn.edu.topicregistration.service.TopicRules;
import vn.edu.topicregistration.service.TopicRules.Failure;
import vn.edu.topicregistration.service.TopicRules.SupervisorCandidate;
import vn.edu.topicregistration.service.TopicRules.TopicAccess;
import vn.edu.topicregistration.service.TopicRules.TopicDraft;
import vn.edu.topicregistration.service.TopicRules.TopicRuleException;
import vn.edu.topicregistration.service.TopicRules.TopicStatus;

/** FR-05/06/14; AS-2.1-2.3, AS-2.5-2.11; AT-03/12. */
class TopicRulesTest {
    private static final long AUTHOR_ID = 11L;
    private static final long FACULTY_ID = 7L;
    private static final long DEPARTMENT_ID = 3L;
    private static final Instant START = Instant.parse("2026-09-12T00:00:00Z");
    private static final Instant END = Instant.parse("2026-09-12T02:00:00Z");
    private static final Instant MIDDLE = Instant.parse("2026-09-12T01:00:00Z");

    @Test
    void authorIsPrimaryAndMaySubmitWithNoCoSupervisorOrOneEligibleCoSupervisor() {
        TopicDraft solo = TopicRules.validateForSubmit(
                completeDraft(List.of(primary())), ownDraft(), START, START, END);
        TopicDraft paired = TopicRules.validateForSubmit(
                completeDraft(List.of(primary(), coSupervisor())), ownDraft(), END, START, END);

        assertEquals(1, solo.supervisors().size());
        assertEquals(AUTHOR_ID, solo.supervisors().getFirst().accountId());
        assertEquals(2, paired.supervisors().size());
        assertEquals(12L, paired.supervisors().get(1).accountId());
    }

    @Test
    void zeroOrMoreThanTwoTotalSupervisorsCannotBeSubmitted() {
        assertFailure(Failure.VALIDATION_FAILED,
                () -> submit(completeDraft(List.of()), ownDraft(), MIDDLE));
        assertFailure(Failure.VALIDATION_FAILED,
                () -> submit(completeDraft(List.of(primary(), coSupervisor(), candidate(13, 7, 3L, true, true))),
                        ownDraft(), MIDDLE));
    }

    @Test
    void primaryMustBeTheAuthorAndSupervisorIdsMustBeUnique() {
        assertFailure(Failure.VALIDATION_FAILED,
                () -> submit(completeDraft(List.of(coSupervisor())), ownDraft(), MIDDLE));
        assertFailure(Failure.VALIDATION_FAILED,
                () -> submit(completeDraft(List.of(primary(), primary())), ownDraft(), MIDDLE));
    }

    @Test
    void coSupervisorMustBeEnabledLecturerInTopicsDepartmentAndFaculty() {
        assertFailure(Failure.VALIDATION_FAILED,
                () -> submit(completeDraft(List.of(primary(), candidate(12, 8, 3L, true, true))),
                        ownDraft(), MIDDLE));
        assertFailure(Failure.VALIDATION_FAILED,
                () -> submit(completeDraft(List.of(primary(), candidate(12, 7, 4L, true, true))),
                        ownDraft(), MIDDLE));
        assertFailure(Failure.VALIDATION_FAILED,
                () -> submit(completeDraft(List.of(primary(), candidate(12, 7, null, true, true))),
                        ownDraft(), MIDDLE));
        assertFailure(Failure.VALIDATION_FAILED,
                () -> submit(completeDraft(List.of(primary(), candidate(12, 7, 3L, false, true))),
                        ownDraft(), MIDDLE));
        assertFailure(Failure.VALIDATION_FAILED,
                () -> submit(completeDraft(List.of(primary(), candidate(12, 7, 3L, true, false))),
                        ownDraft(), MIDDLE));
    }

    @Test
    void primaryMustAlsoBelongToTopicsDepartmentAndTopicNeedsDepartment() {
        assertFailure(Failure.VALIDATION_FAILED,
                () -> TopicRules.validateForSave(
                        completeDraft(List.of(candidate(AUTHOR_ID, FACULTY_ID, 4L, true, true))),
                        ownDraft()));
        TopicAccess noDepartment = new TopicAccess(AUTHOR_ID, AUTHOR_ID,
                FACULTY_ID, FACULTY_ID, null, TopicStatus.DRAFT);
        assertFailure(Failure.VALIDATION_FAILED,
                () -> TopicRules.validateForSave(completeDraft(List.of(primary())), noDepartment));
    }

    @Test
    void periodTopicAndAuthorDepartmentsMustAllMatch() {
        TopicRules.requireDepartmentAlignment(DEPARTMENT_ID, DEPARTMENT_ID, DEPARTMENT_ID);
        assertFailure(Failure.DEPARTMENT_MISMATCH,
                () -> TopicRules.requireDepartmentAlignment(null, DEPARTMENT_ID, DEPARTMENT_ID));
        assertFailure(Failure.DEPARTMENT_MISMATCH,
                () -> TopicRules.requireDepartmentAlignment(DEPARTMENT_ID, null, DEPARTMENT_ID));
        assertFailure(Failure.DEPARTMENT_MISMATCH,
                () -> TopicRules.requireDepartmentAlignment(DEPARTMENT_ID, 4L, DEPARTMENT_ID));
        assertFailure(Failure.DEPARTMENT_MISMATCH,
                () -> TopicRules.requireDepartmentAlignment(DEPARTMENT_ID, DEPARTMENT_ID, 4L));
    }

    @Test
    void allFiveFieldsAreRequiredToSubmitButCanRemainIncompleteInDraft() {
        TopicDraft[] incomplete = {
                draft(null, "Mô tả", "Mục tiêu", "Yêu cầu", "Kết quả"),
                draft("Tên", "  ", "Mục tiêu", "Yêu cầu", "Kết quả"),
                draft("Tên", "Mô tả", null, "Yêu cầu", "Kết quả"),
                draft("Tên", "Mô tả", "Mục tiêu", "  ", "Kết quả"),
                draft("Tên", "Mô tả", "Mục tiêu", "Yêu cầu", null)
        };

        for (TopicDraft candidate : incomplete) {
            assertNotNull(TopicRules.validateForSave(candidate, ownDraft()));
            assertFailure(Failure.VALIDATION_FAILED,
                    () -> submit(candidate, ownDraft(), MIDDLE));
        }
    }

    @Test
    void submitKeepsAllFiveNormalizedContentValues() {
        TopicDraft submitted = submit(draft(
                "  Tên  ", "  Mô tả  ", "  Mục tiêu  ", "  Yêu cầu  ", "  Kết quả  "),
                ownDraft(), MIDDLE);

        assertEquals("Tên", submitted.title());
        assertEquals("Mô tả", submitted.description());
        assertEquals("Mục tiêu", submitted.objectives());
        assertEquals("Yêu cầu", submitted.studentRequirements());
        assertEquals("Kết quả", submitted.expectedResults());
    }

    @Test
    void onlyAuthorFromTopicsFacultyCanSaveOrSubmit() {
        TopicAccess otherLecturer = new TopicAccess(12, AUTHOR_ID, FACULTY_ID,
                FACULTY_ID, DEPARTMENT_ID, TopicStatus.DRAFT);
        TopicAccess otherFaculty = new TopicAccess(AUTHOR_ID, AUTHOR_ID, 8,
                FACULTY_ID, DEPARTMENT_ID, TopicStatus.DRAFT);

        for (TopicAccess denied : List.of(otherLecturer, otherFaculty)) {
            assertFailure(Failure.ACCESS_DENIED,
                    () -> TopicRules.validateForSave(completeDraft(List.of(primary())), denied));
            assertFailure(Failure.ACCESS_DENIED,
                    () -> submit(completeDraft(List.of(primary())), denied, MIDDLE));
        }
    }

    @Test
    void saveAndSubmitAreLockedAfterLeavingDraft() {
        for (TopicStatus status : List.of(
                TopicStatus.PENDING_APPROVAL, TopicStatus.APPROVED, TopicStatus.PUBLISHED)) {
            TopicAccess access = new TopicAccess(AUTHOR_ID, AUTHOR_ID, FACULTY_ID,
                    FACULTY_ID, DEPARTMENT_ID, status);
            assertFailure(Failure.INVALID_STATE,
                    () -> TopicRules.validateForSave(completeDraft(List.of(primary())), access));
            assertFailure(Failure.INVALID_STATE,
                    () -> submit(completeDraft(List.of(primary())), access, MIDDLE));
        }
    }

    @Test
    void rejectedTopicCanBeResubmittedOnlyAsDraftAndWithinLecturerWindow() {
        TopicDraft draftAfterRejection = completeDraft(List.of(primary()));
        assertFailure(Failure.WINDOW_CLOSED,
                () -> submit(draftAfterRejection, ownDraft(), START.minusNanos(1)));
        assertFailure(Failure.WINDOW_CLOSED,
                () -> submit(draftAfterRejection, ownDraft(), END.plusNanos(1)));
        assertEquals("Tên", submit(draftAfterRejection, ownDraft(), END).title());
    }

    @Test
    void approvedTopicReturnsToDraftOnlyForAuthorWithinWindowWithoutRegistration() {
        TopicAccess approved = new TopicAccess(AUTHOR_ID, AUTHOR_ID,
                FACULTY_ID, FACULTY_ID, DEPARTMENT_ID, TopicStatus.APPROVED);
        assertEquals(TopicStatus.DRAFT,
                TopicRules.returnToDraft(approved, START, START, END, false));
        assertEquals(TopicStatus.DRAFT,
                TopicRules.returnToDraft(approved, END, START, END, false));
        assertFailure(Failure.WINDOW_CLOSED,
                () -> TopicRules.returnToDraft(approved, START.minusNanos(1), START, END, false));
        assertFailure(Failure.WINDOW_CLOSED,
                () -> TopicRules.returnToDraft(approved, END.plusNanos(1), START, END, false));
        assertFailure(Failure.REGISTRATION_EXISTS,
                () -> TopicRules.returnToDraft(approved, MIDDLE, START, END, true));
    }

    @Test
    void returnToDraftRejectsOtherActorAndAllOtherStates() {
        TopicAccess outsider = new TopicAccess(AUTHOR_ID + 1, AUTHOR_ID,
                FACULTY_ID, FACULTY_ID, DEPARTMENT_ID, TopicStatus.APPROVED);
        assertFailure(Failure.ACCESS_DENIED,
                () -> TopicRules.returnToDraft(outsider, MIDDLE, START, END, false));
        for (TopicStatus status : List.of(
                TopicStatus.DRAFT, TopicStatus.PENDING_APPROVAL, TopicStatus.PUBLISHED)) {
            TopicAccess access = new TopicAccess(AUTHOR_ID, AUTHOR_ID,
                    FACULTY_ID, FACULTY_ID, DEPARTMENT_ID, status);
            assertFailure(Failure.INVALID_STATE,
                    () -> TopicRules.returnToDraft(access, MIDDLE, START, END, false));
        }
    }

    private static TopicDraft submit(TopicDraft draft, TopicAccess access, Instant now) {
        return TopicRules.validateForSubmit(draft, access, now, START, END);
    }

    private static void assertFailure(Failure expected, Runnable action) {
        assertEquals(expected, assertThrows(TopicRuleException.class, action::run).failure());
    }

    private static TopicAccess ownDraft() {
        return new TopicAccess(AUTHOR_ID, AUTHOR_ID, FACULTY_ID,
                FACULTY_ID, DEPARTMENT_ID, TopicStatus.DRAFT);
    }

    private static TopicDraft completeDraft(List<SupervisorCandidate> supervisors) {
        return new TopicDraft("Tên", "Mô tả", "Mục tiêu", "Yêu cầu", "Kết quả", supervisors);
    }

    private static TopicDraft draft(
            String title, String description, String objectives,
            String studentRequirements, String expectedResults) {
        return new TopicDraft(title, description, objectives, studentRequirements,
                expectedResults, List.of(primary()));
    }

    private static SupervisorCandidate primary() {
        return candidate(AUTHOR_ID, FACULTY_ID, DEPARTMENT_ID, true, true);
    }

    private static SupervisorCandidate coSupervisor() {
        return candidate(12, FACULTY_ID, DEPARTMENT_ID, true, true);
    }

    private static SupervisorCandidate candidate(
            long accountId, long facultyId, Long departmentId, boolean enabled, boolean lecturer) {
        return new SupervisorCandidate(accountId, facultyId, departmentId, enabled, lecturer);
    }
}
