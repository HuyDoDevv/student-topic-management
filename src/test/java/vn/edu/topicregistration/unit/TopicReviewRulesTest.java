package vn.edu.topicregistration.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import vn.edu.topicregistration.service.TopicReviewRules;
import vn.edu.topicregistration.service.TopicReviewRules.InvalidTopicReviewStateException;
import vn.edu.topicregistration.service.TopicReviewRules.ReviewDecision;
import vn.edu.topicregistration.service.TopicRules;
import vn.edu.topicregistration.service.TopicRules.Failure;
import vn.edu.topicregistration.service.TopicRules.SupervisorCandidate;
import vn.edu.topicregistration.service.TopicRules.TopicAccess;
import vn.edu.topicregistration.service.TopicRules.TopicDraft;
import vn.edu.topicregistration.service.TopicRules.TopicRuleException;
import vn.edu.topicregistration.service.TopicRules.TopicStatus;

class TopicReviewRulesTest {
    private static final Instant LECTURER_START = Instant.parse("2026-09-10T01:00:00Z");
    private static final Instant LECTURER_END = Instant.parse("2026-09-11T16:59:59Z");

    @Test
    void pendingTopicCanBeApprovedThenPublished() {
        TopicStatus approved = TopicReviewRules.review(
                TopicStatus.PENDING_APPROVAL, ReviewDecision.APPROVE);
        assertEquals(TopicStatus.APPROVED, approved);
        assertEquals(TopicStatus.PUBLISHED, TopicReviewRules.publish(approved));
    }

    @Test
    void rejectedPendingTopicReturnsToDraftRatherThanPublishedOrApproved() {
        TopicStatus rejected = TopicReviewRules.review(
                TopicStatus.PENDING_APPROVAL, ReviewDecision.REJECT);
        assertEquals(TopicStatus.DRAFT, rejected);
        assertThrows(InvalidTopicReviewStateException.class,
                () -> TopicReviewRules.publish(rejected));
    }

    @Test
    void reviewCannotSkipPendingOrRepeatAfterDecision() {
        for (TopicStatus status : List.of(
                TopicStatus.DRAFT, TopicStatus.APPROVED, TopicStatus.PUBLISHED)) {
            assertThrows(InvalidTopicReviewStateException.class,
                    () -> TopicReviewRules.review(status, ReviewDecision.APPROVE));
            assertThrows(InvalidTopicReviewStateException.class,
                    () -> TopicReviewRules.review(status, ReviewDecision.REJECT));
        }
    }

    @Test
    void publicationRequiresApprovalAndCannotRepeat() {
        for (TopicStatus status : List.of(
                TopicStatus.DRAFT, TopicStatus.PENDING_APPROVAL, TopicStatus.PUBLISHED)) {
            assertThrows(InvalidTopicReviewStateException.class,
                    () -> TopicReviewRules.publish(status));
        }
    }

    @Test
    void rejectedDraftMayBeResubmittedByAuthorAtBothWindowBoundaries() {
        TopicStatus rejected = TopicReviewRules.review(
                TopicStatus.PENDING_APPROVAL, ReviewDecision.REJECT);
        TopicAccess author = new TopicAccess(10, 10, 20, 20, 30L, rejected);

        assertEquals(draft(), TopicRules.validateForSubmit(
                draft(), author, LECTURER_START, LECTURER_START, LECTURER_END));
        assertEquals(draft(), TopicRules.validateForSubmit(
                draft(), author, LECTURER_END, LECTURER_START, LECTURER_END));
    }

    @Test
    void rejectedDraftCannotBeResubmittedOutsideLecturerWindowOrByAnotherLecturer() {
        TopicStatus rejected = TopicReviewRules.review(
                TopicStatus.PENDING_APPROVAL, ReviewDecision.REJECT);
        TopicAccess author = new TopicAccess(10, 10, 20, 20, 30L, rejected);
        assertFailure(Failure.WINDOW_CLOSED, () -> TopicRules.validateForSubmit(
                draft(), author, LECTURER_START.minusNanos(1),
                LECTURER_START, LECTURER_END));
        assertFailure(Failure.WINDOW_CLOSED, () -> TopicRules.validateForSubmit(
                draft(), author, LECTURER_END.plusNanos(1),
                LECTURER_START, LECTURER_END));
        TopicAccess anotherLecturer = new TopicAccess(11, 10, 20, 20, 30L, rejected);
        assertFailure(Failure.ACCESS_DENIED, () -> TopicRules.validateForSubmit(
                draft(), anotherLecturer, LECTURER_START,
                LECTURER_START, LECTURER_END));
    }

    @Test
    void approvedOrPublishedTopicCannotBeResubmittedThroughDraftFlow() {
        for (TopicStatus status : List.of(TopicStatus.APPROVED, TopicStatus.PUBLISHED)) {
            TopicAccess author = new TopicAccess(10, 10, 20, 20, 30L, status);
            assertFailure(Failure.INVALID_STATE, () -> TopicRules.validateForSubmit(
                    draft(), author, LECTURER_START,
                    LECTURER_START, LECTURER_END));
        }
    }

    private static TopicDraft draft() {
        return new TopicDraft(
                "Tên đề tài", "Mô tả", "Mục tiêu", "Yêu cầu sinh viên", "Kết quả dự kiến",
                List.of(new SupervisorCandidate(10, 20, 30L, true, true)));
    }

    private static void assertFailure(Failure expected, org.junit.jupiter.api.function.Executable action) {
        assertEquals(expected, assertThrows(TopicRuleException.class, action).failure());
    }
}
