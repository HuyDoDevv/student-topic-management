package vn.edu.topicregistration.groups.domain;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class GroupRules {
    private GroupRules() {
    }

    public static void validateCreate(
            Instant now,
            Instant studentStart,
            Instant studentEnd,
            boolean alreadyMemberInPeriod,
            boolean hasPendingInvitation) {
        requireStudentWindow(now, studentStart, studentEnd);
        if (alreadyMemberInPeriod) {
            throw failure(Failure.ALREADY_MEMBER);
        }
        if (hasPendingInvitation) {
            throw failure(Failure.INVITATION_CONFLICT);
        }
    }

    public static int validateInvite(
            MutationContext context,
            long candidateId,
            boolean eligibleStudent,
            boolean memberOrPendingInPeriod) {
        validateMutableByLeader(context);
        if (!eligibleStudent || candidateId <= 0) {
            throw failure(Failure.INVALID_MEMBER);
        }
        if (memberOrPendingInPeriod || context.memberIds().contains(candidateId)) {
            throw failure(Failure.INVITATION_CONFLICT);
        }
        if (context.memberIds().size() + context.pendingSlots().size() >= 3) {
            throw failure(Failure.GROUP_FULL);
        }
        for (int slot = 1; slot <= 3; slot++) {
            if (!context.memberSlots().contains(slot) && !context.pendingSlots().contains(slot)) {
                return slot;
            }
        }
        throw failure(Failure.GROUP_FULL);
    }

    public static void validateAccept(
            MutationContext context,
            long invitedStudentId,
            int invitationSlot,
            boolean pending,
            boolean alreadyMemberInPeriod) {
        validateGroupIntegrity(context);
        requirePendingInvitation(pending);
        if (context.actorId() != invitedStudentId) {
            throw failure(Failure.ACCESS_DENIED);
        }
        requireStudentWindow(context.now(), context.studentStart(), context.studentEnd());
        if (context.activeRegistration()) {
            throw failure(Failure.ACTIVE_REGISTRATION);
        }
        if (alreadyMemberInPeriod) {
            throw failure(Failure.ALREADY_MEMBER);
        }
        if (invitationSlot < 1
                || invitationSlot > 3
                || context.memberSlots().contains(invitationSlot)
                || !context.pendingSlots().contains(invitationSlot)
                || context.memberIds().size() + context.pendingSlots().size() > 3) {
            throw failure(Failure.INVALID_STATE);
        }
    }

    public static void validateReject(
            long actorId,
            long invitedStudentId,
            boolean pending) {
        requirePendingInvitation(pending);
        if (actorId != invitedStudentId) {
            throw failure(Failure.ACCESS_DENIED);
        }
    }

    public static void validateCancel(MutationContext context, boolean pending) {
        validateMutableByLeader(context);
        requirePendingInvitation(pending);
    }

    public static void validateRemove(MutationContext context, long memberId) {
        validateMutableByLeader(context);
        if (!context.memberIds().contains(memberId) || context.leaderId() == memberId) {
            throw failure(Failure.INVALID_MEMBER);
        }
    }

    public static void validateLeave(MutationContext context) {
        validateMutable(context);
        if (!context.memberIds().contains(context.actorId())) {
            throw failure(Failure.ACCESS_DENIED);
        }
        if (context.actorId() == context.leaderId()) {
            throw failure(Failure.INVALID_STATE);
        }
    }

    public static void validateDissolve(MutationContext context) {
        validateMutableByLeader(context);
        if (context.memberIds().size() != 1) {
            throw failure(Failure.INVALID_STATE);
        }
    }

    public static void validateTransfer(MutationContext context, long newLeaderId) {
        validateMutableByLeader(context);
        if (newLeaderId == context.leaderId() || !context.memberIds().contains(newLeaderId)) {
            throw failure(Failure.INVALID_MEMBER);
        }
    }

    private static void validateMutableByLeader(MutationContext context) {
        validateMutable(context);
        if (context.actorId() != context.leaderId()) {
            throw failure(Failure.ACCESS_DENIED);
        }
    }

    private static void validateMutable(MutationContext context) {
        validateGroupIntegrity(context);
        requireStudentWindow(context.now(), context.studentStart(), context.studentEnd());
        if (context.activeRegistration()) {
            throw failure(Failure.ACTIVE_REGISTRATION);
        }
    }

    private static void validateGroupIntegrity(MutationContext context) {
        Objects.requireNonNull(context, "context");
        if (context.memberIds().isEmpty()
                || !context.memberIds().contains(context.leaderId())
                || context.memberIds().size() > 3
                || context.memberIds().size() != context.memberSlots().size()
                || context.memberIds().size() + context.pendingSlots().size() > 3) {
            throw failure(Failure.INVALID_STATE);
        }
    }

    private static void requirePendingInvitation(boolean pending) {
        if (!pending) {
            throw failure(Failure.INVALID_INVITATION);
        }
    }

    private static void requireStudentWindow(
            Instant now,
            Instant studentStart,
            Instant studentEnd) {
        Objects.requireNonNull(now, "now");
        Objects.requireNonNull(studentStart, "studentStart");
        Objects.requireNonNull(studentEnd, "studentEnd");
        if (now.isBefore(studentStart) || now.isAfter(studentEnd)) {
            throw failure(Failure.WINDOW_CLOSED);
        }
    }

    private static GroupRuleException failure(Failure failure) {
        return new GroupRuleException(failure);
    }

    public enum Failure {
        ACCESS_DENIED,
        WINDOW_CLOSED,
        ACTIVE_REGISTRATION,
        GROUP_FULL,
        ALREADY_MEMBER,
        INVITATION_CONFLICT,
        INVALID_INVITATION,
        INVALID_MEMBER,
        INVALID_STATE
    }

    public record MutationContext(
            long actorId,
            long leaderId,
            List<Long> memberIds,
            List<Integer> memberSlots,
            List<Integer> pendingSlots,
            boolean activeRegistration,
            Instant now,
            Instant studentStart,
            Instant studentEnd) {
        public MutationContext {
            memberIds = memberIds == null ? List.of() : List.copyOf(memberIds);
            memberSlots = memberSlots == null ? List.of() : List.copyOf(memberSlots);
            pendingSlots = pendingSlots == null ? List.of() : List.copyOf(pendingSlots);
            Set<Long> uniqueMembers = new HashSet<>(memberIds);
            Set<Integer> uniqueMemberSlots = new HashSet<>(memberSlots);
            Set<Integer> uniquePendingSlots = new HashSet<>(pendingSlots);
            Set<Integer> allSlots = new HashSet<>(memberSlots);
            allSlots.addAll(pendingSlots);
            if (uniqueMembers.size() != memberIds.size()
                    || uniqueMemberSlots.size() != memberSlots.size()
                    || uniquePendingSlots.size() != pendingSlots.size()
                    || allSlots.size() != memberSlots.size() + pendingSlots.size()) {
                throw failure(Failure.INVALID_STATE);
            }
        }
    }

    public static final class GroupRuleException extends RuntimeException {
        private final Failure failure;

        public GroupRuleException(Failure failure) {
            super(Objects.requireNonNull(failure, "failure").name());
            this.failure = failure;
        }

        public Failure failure() {
            return failure;
        }
    }
}
