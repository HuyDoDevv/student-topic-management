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
            boolean alreadyMemberInPeriod) {
        requireStudentWindow(now, studentStart, studentEnd);
        if (alreadyMemberInPeriod) {
            throw failure(Failure.ALREADY_MEMBER);
        }
    }

    public static int validateAdd(
            MutationContext context,
            long candidateId,
            boolean eligibleStudent,
            boolean alreadyMemberInPeriod) {
        validateMutableByLeader(context);
        if (!eligibleStudent || candidateId <= 0) {
            throw failure(Failure.INVALID_MEMBER);
        }
        if (alreadyMemberInPeriod || context.memberIds().contains(candidateId)) {
            throw failure(Failure.ALREADY_MEMBER);
        }
        if (context.memberIds().size() >= 3) {
            throw failure(Failure.GROUP_FULL);
        }
        for (int slot = 1; slot <= 3; slot++) {
            if (!context.occupiedSlots().contains(slot)) {
                return slot;
            }
        }
        throw failure(Failure.GROUP_FULL);
    }

    public static void validateRemove(MutationContext context, long memberId) {
        validateMutableByLeader(context);
        if (!context.memberIds().contains(memberId) || context.leaderId() == memberId) {
            throw failure(Failure.INVALID_MEMBER);
        }
    }

    public static void validateTransfer(MutationContext context, long newLeaderId) {
        validateMutableByLeader(context);
        if (newLeaderId == context.leaderId() || !context.memberIds().contains(newLeaderId)) {
            throw failure(Failure.INVALID_MEMBER);
        }
    }

    private static void validateMutableByLeader(MutationContext context) {
        Objects.requireNonNull(context, "context");
        requireStudentWindow(context.now(), context.studentStart(), context.studentEnd());
        if (context.actorId() != context.leaderId()) {
            throw failure(Failure.ACCESS_DENIED);
        }
        if (context.activeRegistration()) {
            throw failure(Failure.ACTIVE_REGISTRATION);
        }
        if (context.memberIds().isEmpty()
                || !context.memberIds().contains(context.leaderId())
                || context.memberIds().size() > 3
                || context.memberIds().size() != context.occupiedSlots().size()) {
            throw failure(Failure.INVALID_STATE);
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
        INVALID_MEMBER,
        INVALID_STATE
    }

    public record MutationContext(
            long actorId,
            long leaderId,
            List<Long> memberIds,
            List<Integer> occupiedSlots,
            boolean activeRegistration,
            Instant now,
            Instant studentStart,
            Instant studentEnd) {
        public MutationContext {
            memberIds = memberIds == null ? List.of() : List.copyOf(memberIds);
            occupiedSlots = occupiedSlots == null ? List.of() : List.copyOf(occupiedSlots);
            Set<Long> uniqueMembers = new HashSet<>(memberIds);
            Set<Integer> uniqueSlots = new HashSet<>(occupiedSlots);
            if (uniqueMembers.size() != memberIds.size()
                    || uniqueSlots.size() != occupiedSlots.size()) {
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
