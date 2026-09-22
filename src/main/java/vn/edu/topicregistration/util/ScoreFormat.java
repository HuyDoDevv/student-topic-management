package vn.edu.topicregistration.util;

import java.math.BigDecimal;

public final class ScoreFormat {
    private ScoreFormat() {
    }

    public static BigDecimal normalize(BigDecimal value) {
        if (value == null) {
            return null;
        }
        BigDecimal normalized = value.stripTrailingZeros();
        return normalized.scale() < 0 ? normalized.setScale(0) : normalized;
    }

    public static String display(BigDecimal value) {
        BigDecimal normalized = normalize(value);
        return normalized == null ? null : normalized.toPlainString();
    }
}
