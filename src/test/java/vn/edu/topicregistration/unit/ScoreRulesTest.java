package vn.edu.topicregistration.unit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import vn.edu.topicregistration.entity.CouncilStatus;
import vn.edu.topicregistration.entity.RegistrationStatus;
import vn.edu.topicregistration.service.ScoreRules;
import vn.edu.topicregistration.service.ScoreRules.Failure;
import vn.edu.topicregistration.service.ScoreRules.ScoreContext;
import vn.edu.topicregistration.service.ScoreRules.ScoreRuleException;

class ScoreRulesTest {
    @Test
    void acceptsBoundariesAndQuarterPointScoresForAssignedMember() {
        assertDoesNotThrow(() -> ScoreRules.validate(valid(new BigDecimal("0.00"))));
        assertDoesNotThrow(() -> ScoreRules.validate(valid(new BigDecimal("5.5"))));
        assertDoesNotThrow(() -> ScoreRules.validate(valid(new BigDecimal("5.75"))));
        assertDoesNotThrow(() -> ScoreRules.validate(valid(new BigDecimal("10.00"))));
    }

    @Test
    void rejectsMissingOutOfRangeOverPreciseAndNonQuarterScores() {
        assertFailure(Failure.INVALID_SCORE, valid(null));
        assertFailure(Failure.INVALID_SCORE, valid(new BigDecimal("-0.01")));
        assertFailure(Failure.INVALID_SCORE, valid(new BigDecimal("10.01")));
        assertFailure(Failure.INVALID_SCORE, valid(new BigDecimal("8.555")));
        assertFailure(Failure.INVALID_SCORE, valid(new BigDecimal("5.6")));
        assertFailure(Failure.INVALID_SCORE, valid(new BigDecimal("8.55")));
    }

    @Test
    void rejectsOutsiderSupervisorAndNonAcceptedRegistration() {
        ScoreContext valid = valid(new BigDecimal("8.50"));
        assertFailure(Failure.ACCESS_DENIED, valid.withAssignedMember(false));
        assertFailure(Failure.ACCESS_DENIED, valid.withSupervisor(true));
        assertFailure(Failure.INVALID_STATE,
                valid.withRegistrationStatus(RegistrationStatus.PENDING));
    }

    @Test
    void locksScoringUnlessCouncilIsActive() {
        for (CouncilStatus status : CouncilStatus.values()) {
            if (status != CouncilStatus.ACTIVE) {
                assertFailure(Failure.INVALID_STATE,
                        valid(new BigDecimal("8.50")).withCouncilStatus(status));
            }
        }
    }

    private static ScoreContext valid(BigDecimal score) {
        return new ScoreContext(
                CouncilStatus.ACTIVE,
                RegistrationStatus.ACCEPTED,
                true,
                false,
                score);
    }

    private static void assertFailure(Failure expected, ScoreContext context) {
        ScoreRuleException exception = assertThrows(
                ScoreRuleException.class,
                () -> ScoreRules.validate(context));
        assertEquals(expected, exception.failure());
    }
}
