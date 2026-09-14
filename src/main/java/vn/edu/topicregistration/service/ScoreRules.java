package vn.edu.topicregistration.service;

import java.math.BigDecimal;
import java.util.Objects;

import vn.edu.topicregistration.entity.CouncilStatus;
import vn.edu.topicregistration.entity.RegistrationStatus;

public final class ScoreRules {
    private static final BigDecimal MINIMUM = new BigDecimal("0.00");
    private static final BigDecimal MAXIMUM = new BigDecimal("10.00");

    private ScoreRules() {
    }

    public static void validate(ScoreContext context) {
        Objects.requireNonNull(context, "context");
        if (!context.assignedMember() || context.supervisor()) {
            throw failure(Failure.ACCESS_DENIED);
        }
        if (context.councilStatus() != CouncilStatus.ACTIVE
                || context.registrationStatus() != RegistrationStatus.ACCEPTED) {
            throw failure(Failure.INVALID_STATE);
        }
        BigDecimal score = context.score();
        if (score == null
                || score.compareTo(MINIMUM) < 0
                || score.compareTo(MAXIMUM) > 0
                || score.scale() > 2) {
            throw failure(Failure.INVALID_SCORE);
        }
    }

    private static ScoreRuleException failure(Failure failure) {
        return new ScoreRuleException(failure);
    }

    public record ScoreContext(
            CouncilStatus councilStatus,
            RegistrationStatus registrationStatus,
            boolean assignedMember,
            boolean supervisor,
            BigDecimal score) {
        public ScoreContext withCouncilStatus(CouncilStatus value) {
            return new ScoreContext(
                    value, registrationStatus, assignedMember, supervisor, score);
        }

        public ScoreContext withRegistrationStatus(RegistrationStatus value) {
            return new ScoreContext(
                    councilStatus, value, assignedMember, supervisor, score);
        }

        public ScoreContext withAssignedMember(boolean value) {
            return new ScoreContext(
                    councilStatus, registrationStatus, value, supervisor, score);
        }

        public ScoreContext withSupervisor(boolean value) {
            return new ScoreContext(
                    councilStatus, registrationStatus, assignedMember, value, score);
        }
    }

    public enum Failure {
        ACCESS_DENIED,
        INVALID_STATE,
        INVALID_SCORE
    }

    public static final class ScoreRuleException extends RuntimeException {
        private final Failure failure;

        public ScoreRuleException(Failure failure) {
            super(Objects.requireNonNull(failure, "failure").name());
            this.failure = failure;
        }

        public Failure failure() {
            return failure;
        }
    }
}
