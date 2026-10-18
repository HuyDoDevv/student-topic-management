package vn.edu.topicregistration.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;

import vn.edu.topicregistration.entity.CouncilMode;
import vn.edu.topicregistration.entity.CouncilStatus;

public final class FinalScoreRules {
    private static final BigDecimal MIN_SCORE = new BigDecimal("0.00");
    private static final BigDecimal MAX_SCORE = new BigDecimal("10.00");

    private FinalScoreRules() {
    }

    public static BigDecimal mean(List<BigDecimal> scores) {
        if (scores == null || scores.isEmpty()) {
            throw failure(FinalScoreFailure.EVALUATIONS_INCOMPLETE);
        }
        BigDecimal total = BigDecimal.ZERO;
        for (BigDecimal score : scores) {
            if (!validPersistedScore(score)) {
                throw failure(FinalScoreFailure.EVALUATIONS_INCOMPLETE);
            }
            total = total.add(score);
        }
        return total.divide(
                BigDecimal.valueOf(scores.size()),
                2,
                RoundingMode.HALF_UP);
    }

    public static void validateCompletion(CompletionContext context) {
        Objects.requireNonNull(context, "context");
        if (!context.authorizedActor()) {
            throw failure(FinalScoreFailure.ACCESS_DENIED);
        }
        if (context.status() != CouncilStatus.ACTIVE) {
            throw failure(FinalScoreFailure.INVALID_STATE);
        }
        long expectedEvaluations = (long) context.studentCount() * context.memberCount();
        boolean validCardinality = context.mode() == CouncilMode.DIRECT
                ? context.assignmentCount() == 1
                        && context.memberCount() == 1
                        && context.studentCount() >= 1
                : context.assignmentCount() >= 1
                        && context.studentCount() >= context.assignmentCount()
                        && context.memberCount() >= 3
                        && context.memberCount() <= 5;
        if (!validCardinality
                || expectedEvaluations != context.submittedEvaluationCount()
                || !context.exactSubmittedMatrix()) {
            throw failure(FinalScoreFailure.EVALUATIONS_INCOMPLETE);
        }
    }

    public static void validatePublication(PublicationContext context) {
        Objects.requireNonNull(context, "context");
        if (context.status() != CouncilStatus.COMPLETED) {
            throw failure(FinalScoreFailure.INVALID_STATE);
        }
        if (context.studentCount() < 1
                || context.studentCount() != context.resultCount()
                || !context.exactReadyResults()
                || !context.exactSubmittedSources()
                || !context.scoresMatchSources()) {
            throw failure(FinalScoreFailure.RESULT_NOT_READY);
        }
    }

    private static boolean validPersistedScore(BigDecimal score) {
        if (score == null
                || score.compareTo(MIN_SCORE) < 0
                || score.compareTo(MAX_SCORE) > 0) {
            return false;
        }
        try {
            score.setScale(2, RoundingMode.UNNECESSARY);
            return true;
        } catch (ArithmeticException exception) {
            return false;
        }
    }

    private static FinalScoreRuleException failure(FinalScoreFailure failure) {
        return new FinalScoreRuleException(failure);
    }

    public record CompletionContext(
            CouncilStatus status,
            boolean authorizedActor,
            int assignmentCount,
            int memberCount,
            int studentCount,
            int submittedEvaluationCount,
            boolean exactSubmittedMatrix,
            CouncilMode mode) {
        public CompletionContext(
                CouncilStatus status,
                boolean authorizedActor,
                int assignmentCount,
                int memberCount,
                int studentCount,
                int submittedEvaluationCount,
                boolean exactSubmittedMatrix) {
            this(status, authorizedActor, assignmentCount, memberCount,
                    studentCount, submittedEvaluationCount, exactSubmittedMatrix,
                    CouncilMode.COUNCIL);
        }
    }

    public record PublicationContext(
            CouncilStatus status,
            int studentCount,
            int resultCount,
            boolean exactReadyResults,
            boolean exactSubmittedSources,
            boolean scoresMatchSources) {
        public PublicationContext(
                CouncilStatus status, int studentCount, int resultCount,
                boolean exactReadyResults) {
            this(status, studentCount, resultCount, exactReadyResults, true, true);
        }
    }

    public enum FinalScoreFailure {
        ACCESS_DENIED,
        INVALID_STATE,
        EVALUATIONS_INCOMPLETE,
        RESULT_NOT_READY
    }

    public static final class FinalScoreRuleException extends RuntimeException {
        private final FinalScoreFailure failure;

        private FinalScoreRuleException(FinalScoreFailure failure) {
            super(failure.name());
            this.failure = failure;
        }

        public FinalScoreFailure failure() {
            return failure;
        }
    }
}
