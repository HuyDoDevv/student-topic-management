package vn.edu.topicregistration.registrations.domain;

import java.time.Instant;
import java.util.Objects;

public final class RegistrationRules {
    private RegistrationRules() {
    }

    public static void validateSubmit(SubmissionContext context) {
        Objects.requireNonNull(context, "context");
        requireLeader(context.actorId(), context.leaderId());
        requireStudentWindow(context.now(), context.studentStart(), context.studentEnd());

        if (!context.groupActive()
                || context.memberCount() < 1
                || context.memberCount() > 3
                || !context.leaderIsMember()) {
            throw failure(Failure.INVALID_GROUP_STATE);
        }
        if (!context.samePeriod() || !context.topicPublished()) {
            throw failure(Failure.INVALID_TOPIC_STATE);
        }
        if (context.pendingInvitation()) {
            throw failure(Failure.PENDING_INVITATION);
        }
        if (context.activeRegistration()) {
            throw failure(Failure.ACTIVE_REGISTRATION);
        }
        if (context.topicAccepted()) {
            throw failure(Failure.TOPIC_TAKEN);
        }
    }

    public static void validateWithdraw(WithdrawalContext context) {
        Objects.requireNonNull(context, "context");
        requireLeader(context.actorId(), context.leaderId());
        requireStudentWindow(context.now(), context.studentStart(), context.studentEnd());
        if (context.status() != State.PENDING) {
            throw failure(Failure.INVALID_REGISTRATION_STATE);
        }
    }

    private static void requireLeader(long actorId, long leaderId) {
        if (actorId != leaderId) {
            throw failure(Failure.ACCESS_DENIED);
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

    private static RegistrationRuleException failure(Failure failure) {
        return new RegistrationRuleException(failure);
    }

    public enum State {
        PENDING,
        ACCEPTED,
        REJECTED,
        WITHDRAWN
    }

    public enum Failure {
        ACCESS_DENIED,
        WINDOW_CLOSED,
        INVALID_GROUP_STATE,
        PENDING_INVITATION,
        ACTIVE_REGISTRATION,
        INVALID_TOPIC_STATE,
        TOPIC_TAKEN,
        INVALID_REGISTRATION_STATE
    }

    public record SubmissionContext(
            long actorId,
            long leaderId,
            int memberCount,
            boolean leaderIsMember,
            boolean groupActive,
            boolean pendingInvitation,
            boolean activeRegistration,
            boolean samePeriod,
            boolean topicPublished,
            boolean topicAccepted,
            Instant now,
            Instant studentStart,
            Instant studentEnd) {
    }

    public record WithdrawalContext(
            long actorId,
            long leaderId,
            State status,
            Instant now,
            Instant studentStart,
            Instant studentEnd) {
        public WithdrawalContext {
            Objects.requireNonNull(status, "status");
        }
    }

    public static final class RegistrationRuleException extends RuntimeException {
        private final Failure failure;

        public RegistrationRuleException(Failure failure) {
            super(Objects.requireNonNull(failure, "failure").name());
            this.failure = failure;
        }

        public Failure failure() {
            return failure;
        }
    }
}
