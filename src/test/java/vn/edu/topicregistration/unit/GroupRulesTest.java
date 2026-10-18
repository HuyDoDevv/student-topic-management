package vn.edu.topicregistration.unit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import vn.edu.topicregistration.service.GroupRules;
import vn.edu.topicregistration.service.GroupRules.Failure;
import vn.edu.topicregistration.service.GroupRules.GroupRuleException;
import vn.edu.topicregistration.service.GroupRules.MutationContext;

class GroupRulesTest {
    private static final Instant START = Instant.parse("2026-09-12T01:00:00Z");
    private static final Instant END = Instant.parse("2026-09-14T16:59:59Z");
    private static final Instant OPEN = START.plusSeconds(60);

    @Test
    void createIncludesBothWindowBoundariesAndRejectsExistingMembershipOrInvitation() {
        assertDoesNotThrow(() -> GroupRules.validateCreate(START, START, END, false, false));
        assertDoesNotThrow(() -> GroupRules.validateCreate(END, START, END, false, false));
        assertFailure(Failure.WINDOW_CLOSED,
                () -> GroupRules.validateCreate(START.minusNanos(1), START, END, false, false));
        assertFailure(Failure.WINDOW_CLOSED,
                () -> GroupRules.validateCreate(END.plusNanos(1), START, END, false, false));
        assertFailure(Failure.ALREADY_MEMBER,
                () -> GroupRules.validateCreate(OPEN, START, END, true, false));
        assertFailure(Failure.INVITATION_CONFLICT,
                () -> GroupRules.validateCreate(OPEN, START, END, false, true));
    }

    @Test
    void invitationReservesTheLowestFreeSlotAndPendingCountsTowardThree() {
        MutationContext oneMemberOnePending = context(
                1, 1, List.of(1L), List.of(1), List.of(3), false, OPEN);
        assertEquals(2, GroupRules.validateInvite(oneMemberOnePending, 2, true, false));

        MutationContext full = context(
                1, 1, List.of(1L, 2L), List.of(1, 2), List.of(3), false, OPEN);
        assertFailure(Failure.GROUP_FULL,
                () -> GroupRules.validateInvite(full, 3, true, false));
        assertFailure(Failure.INVITATION_CONFLICT,
                () -> GroupRules.validateInvite(oneMemberOnePending, 2, true, true));
        assertFailure(Failure.INVITATION_CONFLICT,
                () -> GroupRules.validateInvite(oneMemberOnePending, 1, true, false));
    }

    @Test
    void onlyLeaderCanInviteAnEligibleStudent() {
        MutationContext memberActor = context(
                2, 1, List.of(1L, 2L), List.of(1, 2), List.of(), false, OPEN);
        assertFailure(Failure.ACCESS_DENIED,
                () -> GroupRules.validateInvite(memberActor, 3, true, false));
        MutationContext leader = context(
                1, 1, List.of(1L), List.of(1), List.of(), false, OPEN);
        assertFailure(Failure.INVALID_MEMBER,
                () -> GroupRules.validateInvite(leader, 3, false, false));
        assertFailure(Failure.INVALID_MEMBER,
                () -> GroupRules.validateInvite(leader, 0, true, false));
    }

    @Test
    void onlyInviteeCanAcceptAPendingFreeSlotWithoutAnotherMembership() {
        MutationContext invitee = context(
                2, 1, List.of(1L), List.of(1), List.of(2), false, OPEN);
        assertDoesNotThrow(() -> GroupRules.validateAccept(invitee, 2, 2, true, false));
        assertFailure(Failure.ACCESS_DENIED,
                () -> GroupRules.validateAccept(invitee, 3, 2, true, false));
        assertFailure(Failure.INVALID_INVITATION,
                () -> GroupRules.validateAccept(invitee, 2, 2, false, false));
        assertFailure(Failure.ALREADY_MEMBER,
                () -> GroupRules.validateAccept(invitee, 2, 2, true, true));
        assertFailure(Failure.INVALID_STATE,
                () -> GroupRules.validateAccept(invitee, 2, 3, true, false));
        assertFailure(Failure.INVALID_STATE,
                () -> GroupRules.validateAccept(invitee, 2, 1, true, false));
    }

    @Test
    void inviteeCanRejectAtAnyTimeButOnlyWhileInvitationIsPending() {
        assertDoesNotThrow(() -> GroupRules.validateReject(2, 2, true));
        assertFailure(Failure.ACCESS_DENIED, () -> GroupRules.validateReject(1, 2, true));
        assertFailure(Failure.INVALID_INVITATION, () -> GroupRules.validateReject(2, 2, false));
    }

    @Test
    void onlyLeaderCanCancelPendingInvitationOrRemoveAnotherMember() {
        MutationContext leader = context(
                1, 1, List.of(1L, 2L), List.of(1, 2), List.of(3), false, OPEN);
        assertDoesNotThrow(() -> GroupRules.validateCancel(leader, true));
        assertDoesNotThrow(() -> GroupRules.validateRemove(leader, 2));
        assertFailure(Failure.INVALID_INVITATION, () -> GroupRules.validateCancel(leader, false));
        assertFailure(Failure.INVALID_MEMBER, () -> GroupRules.validateRemove(leader, 1));
        assertFailure(Failure.INVALID_MEMBER, () -> GroupRules.validateRemove(leader, 4));

        MutationContext memberActor = context(
                2, 1, List.of(1L, 2L), List.of(1, 2), List.of(3), false, OPEN);
        assertFailure(Failure.ACCESS_DENIED, () -> GroupRules.validateCancel(memberActor, true));
        assertFailure(Failure.ACCESS_DENIED, () -> GroupRules.validateRemove(memberActor, 1));
    }

    @Test
    void memberMayLeaveButLeaderMustTransferOrDissolveSingleMemberGroup() {
        MutationContext member = context(
                2, 1, List.of(1L, 2L), List.of(1, 2), List.of(), false, OPEN);
        assertDoesNotThrow(() -> GroupRules.validateLeave(member));
        assertFailure(Failure.INVALID_STATE,
                () -> GroupRules.validateLeave(context(
                        1, 1, List.of(1L, 2L), List.of(1, 2), List.of(), false, OPEN)));
        assertFailure(Failure.ACCESS_DENIED,
                () -> GroupRules.validateLeave(context(
                        3, 1, List.of(1L, 2L), List.of(1, 2), List.of(), false, OPEN)));

        MutationContext singleLeader = context(
                1, 1, List.of(1L), List.of(1), List.of(), false, OPEN);
        assertDoesNotThrow(() -> GroupRules.validateDissolve(singleLeader));
        assertFailure(Failure.INVALID_STATE,
                () -> GroupRules.validateDissolve(context(
                        1, 1, List.of(1L, 2L), List.of(1, 2), List.of(), false, OPEN)));
        assertFailure(Failure.ACCESS_DENIED,
                () -> GroupRules.validateDissolve(context(
                        2, 1, List.of(1L), List.of(1), List.of(), false, OPEN)));
    }

    @Test
    void leaderCanTransferOnlyToAnotherCurrentMember() {
        MutationContext leader = context(
                1, 1, List.of(1L, 2L), List.of(1, 2), List.of(), false, OPEN);
        assertDoesNotThrow(() -> GroupRules.validateTransfer(leader, 2));
        assertFailure(Failure.INVALID_MEMBER, () -> GroupRules.validateTransfer(leader, 1));
        assertFailure(Failure.INVALID_MEMBER, () -> GroupRules.validateTransfer(leader, 3));
        assertFailure(Failure.ACCESS_DENIED,
                () -> GroupRules.validateTransfer(context(
                        2, 1, List.of(1L, 2L), List.of(1, 2), List.of(), false, OPEN), 1));

        MutationContext afterTransfer = context(
                1, 2, List.of(1L, 2L), List.of(1, 2), List.of(), false, OPEN);
        assertFailure(Failure.ACCESS_DENIED, () -> GroupRules.validateRemove(afterTransfer, 2));
        assertDoesNotThrow(() -> GroupRules.validateLeave(afterTransfer));
    }

    @Test
    void allMutationsExceptRejectCloseOutsideWindowOrDuringActiveRegistration() {
        MutationContext closed = context(
                1, 1, List.of(1L, 2L), List.of(1, 2), List.of(3), false, END.plusNanos(1));
        MutationContext active = context(
                1, 1, List.of(1L, 2L), List.of(1, 2), List.of(3), true, OPEN);
        for (MutationContext blocked : List.of(closed, active)) {
            Failure expected = blocked.activeRegistration()
                    ? Failure.ACTIVE_REGISTRATION : Failure.WINDOW_CLOSED;
            assertFailure(expected, () -> GroupRules.validateInvite(blocked, 3, true, false));
            assertFailure(expected, () -> GroupRules.validateAccept(
                    context(3, 1, blocked.memberIds(), blocked.memberSlots(),
                            blocked.pendingSlots(), blocked.activeRegistration(), blocked.now()),
                    3, 3, true, false));
            assertFailure(expected, () -> GroupRules.validateCancel(blocked, true));
            assertFailure(expected, () -> GroupRules.validateRemove(blocked, 2));
            assertFailure(expected, () -> GroupRules.validateDissolve(blocked));
            assertFailure(expected, () -> GroupRules.validateTransfer(blocked, 2));
            assertFailure(expected, () -> GroupRules.validateLeave(context(
                    2, 1, blocked.memberIds(), blocked.memberSlots(),
                    blocked.pendingSlots(), blocked.activeRegistration(), blocked.now())));
        }
        assertDoesNotThrow(() -> GroupRules.validateReject(3, 3, true));
    }

    @Test
    void malformedGroupCannotMutateOrGiveLeadershipToNonmember() {
        MutationContext missingLeader = context(
                1, 1, List.of(2L), List.of(1), List.of(), false, OPEN);
        assertFailure(Failure.INVALID_STATE,
                () -> GroupRules.validateTransfer(missingLeader, 2));
        assertFailure(Failure.INVALID_STATE,
                () -> GroupRules.validateAccept(missingLeader, 1, 2, true, false));
        assertFailure(Failure.INVALID_STATE,
                () -> context(1, 1, List.of(1L, 1L), List.of(1, 2), List.of(), false, OPEN));
        assertFailure(Failure.INVALID_STATE,
                () -> context(1, 1, List.of(1L), List.of(1), List.of(1), false, OPEN));
    }

    private static MutationContext context(
            long actor,
            long leader,
            List<Long> members,
            List<Integer> memberSlots,
            List<Integer> pendingSlots,
            boolean active,
            Instant now) {
        return new MutationContext(actor, leader, members, memberSlots,
                pendingSlots, active, now, START, END);
    }

    private static void assertFailure(Failure expected, Executable action) {
        assertEquals(expected, assertThrows(GroupRuleException.class, action).failure());
    }
}
