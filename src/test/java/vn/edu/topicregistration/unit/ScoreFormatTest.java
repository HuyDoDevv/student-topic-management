package vn.edu.topicregistration.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import vn.edu.topicregistration.util.ScoreFormat;

class ScoreFormatTest {
    @Test
    void removesOnlyRedundantFractionDigits() {
        assertEquals("5", ScoreFormat.display(new BigDecimal("5.0000")));
        assertEquals("6.5", ScoreFormat.display(new BigDecimal("6.5000")));
        assertEquals("7.75", ScoreFormat.display(new BigDecimal("7.7500")));
        assertEquals("10", ScoreFormat.display(new BigDecimal("10.0000")));
    }
}
