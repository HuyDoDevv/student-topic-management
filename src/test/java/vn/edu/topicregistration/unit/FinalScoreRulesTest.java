package vn.edu.topicregistration.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import vn.edu.topicregistration.entity.CouncilMode;
import vn.edu.topicregistration.entity.CouncilStatus;
import vn.edu.topicregistration.service.FinalScoreRules;
import vn.edu.topicregistration.service.FinalScoreRules.CompletionContext;
import vn.edu.topicregistration.service.FinalScoreRules.FinalScoreFailure;
import vn.edu.topicregistration.service.FinalScoreRules.FinalScoreRuleException;
import vn.edu.topicregistration.service.FinalScoreRules.PublicationContext;

class FinalScoreRulesTest {
    @Test
    void calculatesArithmeticMeanAtScaleTwo() {
        assertEquals(
                new BigDecimal("8.50"),
                FinalScoreRules.mean(List.of(
                        new BigDecimal("8.50"),
                        new BigDecimal("9.00"),
                        new BigDecimal("8.00"))));
    }

    @Test
    void roundsHalfUpOnlyAfterCalculatingTheMean() {
        assertEquals(
                new BigDecimal("8.34"),
                FinalScoreRules.mean(List.of(
                        new BigDecimal("8.33"),
                        new BigDecimal("8.34"))));
    }

    @Test
    void onlyAuthorizedDeanCanCompleteAnActiveCouncil() {
        FinalScoreRuleException exception = assertThrows(
                FinalScoreRuleException.class,
                () -> FinalScoreRules.validateCompletion(new CompletionContext(
                        CouncilStatus.ACTIVE, false, 1, 3, 3, 9, true)));

        assertEquals(FinalScoreFailure.ACCESS_DENIED, exception.failure());
    }

    @Test
    void completionRequiresAnExactSubmittedEvaluationMatrix() {
        FinalScoreRuleException exception = assertThrows(
                FinalScoreRuleException.class,
                () -> FinalScoreRules.validateCompletion(new CompletionContext(
                        CouncilStatus.ACTIVE, true, 2, 3, 6, 17, false)));

        assertEquals(FinalScoreFailure.EVALUATIONS_INCOMPLETE, exception.failure());
    }

    @Test
    void directGradingRequiresOneScoreForEveryRegistrationStudent() {
        FinalScoreRules.validateCompletion(new CompletionContext(
                CouncilStatus.ACTIVE, true, 1, 1, 3, 3, true, CouncilMode.DIRECT));

        FinalScoreRuleException extraMember = assertThrows(
                FinalScoreRuleException.class,
                () -> FinalScoreRules.validateCompletion(new CompletionContext(
                        CouncilStatus.ACTIVE, true, 1, 2, 3, 6, true, CouncilMode.DIRECT)));
        assertEquals(FinalScoreFailure.EVALUATIONS_INCOMPLETE, extraMember.failure());
    }

    @Test
    void completionRejectsMissingScoreForOneStudent() {
        FinalScoreRuleException exception = assertThrows(
                FinalScoreRuleException.class,
                () -> FinalScoreRules.validateCompletion(new CompletionContext(
                        CouncilStatus.ACTIVE, true, 1, 3, 3, 8, false)));

        assertEquals(FinalScoreFailure.EVALUATIONS_INCOMPLETE, exception.failure());
    }

    @Test
    void publicationRequiresCompletedCouncilAndOneReadyResultPerAssignment() {
        FinalScoreRuleException wrongState = assertThrows(
                FinalScoreRuleException.class,
                () -> FinalScoreRules.validatePublication(new PublicationContext(
                        CouncilStatus.ACTIVE, 6, 6, true)));
        assertEquals(FinalScoreFailure.INVALID_STATE, wrongState.failure());

        FinalScoreRuleException partial = assertThrows(
                FinalScoreRuleException.class,
                () -> FinalScoreRules.validatePublication(new PublicationContext(
                        CouncilStatus.COMPLETED, 6, 5, false)));
        assertEquals(FinalScoreFailure.RESULT_NOT_READY, partial.failure());
    }

    @Test
    void publicationRejectsMissingSourcesOrMismatchedMean() {
        FinalScoreRuleException missing = assertThrows(FinalScoreRuleException.class,
                () -> FinalScoreRules.validatePublication(new PublicationContext(
                        CouncilStatus.COMPLETED, 2, 2, true, false, true)));
        assertEquals(FinalScoreFailure.RESULT_NOT_READY, missing.failure());
        FinalScoreRuleException mismatch = assertThrows(FinalScoreRuleException.class,
                () -> FinalScoreRules.validatePublication(new PublicationContext(
                        CouncilStatus.COMPLETED, 2, 2, true, true, false)));
        assertEquals(FinalScoreFailure.RESULT_NOT_READY, mismatch.failure());
    }
}
