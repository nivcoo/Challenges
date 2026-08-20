package fr.nivcoo.challenges.challenges;

import java.math.BigDecimal;

public final class ChallengeAmount {
    private static final int MAX_SCALE = 12;
    private static final int MAX_TEXT_LENGTH = 96;
    private static final BigDecimal MAX_DELTA = new BigDecimal("1000000000000");
    private static final BigDecimal MAX_BALANCE = new BigDecimal("1000000000000000000000000000000");

    private ChallengeAmount() {
    }

    public static BigDecimal parseDelta(String value) {
        BigDecimal parsed = parseCanonical(value);
        if (parsed.signum() == 0 || parsed.abs().compareTo(MAX_DELTA) > 0) {
            throw new IllegalArgumentException("Challenge delta is outside allowed bounds.");
        }
        return parsed;
    }

    public static BigDecimal parseBalance(String value) {
        BigDecimal parsed = parseCanonical(value);
        if (parsed.abs().compareTo(MAX_BALANCE) > 0) {
            throw new IllegalArgumentException("Challenge balance is outside allowed bounds.");
        }
        return parsed;
    }

    public static String canonical(BigDecimal value) {
        if (value == null) throw new IllegalArgumentException("Challenge amount must not be null.");
        BigDecimal normalized = value.stripTrailingZeros();
        if (normalized.scale() > MAX_SCALE || normalized.scale() < -30
                || normalized.precision() > 64 || normalized.abs().compareTo(MAX_BALANCE) > 0) {
            throw new IllegalArgumentException("Challenge amount has excessive scale.");
        }
        return normalized.toPlainString();
    }

    public static BigDecimal visible(BigDecimal rawBalance) {
        return rawBalance.signum() < 0 ? BigDecimal.ZERO : rawBalance;
    }

    public static String visibleString(BigDecimal rawBalance) {
        return canonical(visible(rawBalance));
    }

    private static BigDecimal parseCanonical(String value) {
        if (value == null || value.isBlank() || value.length() > MAX_TEXT_LENGTH) {
            throw new IllegalArgumentException("Challenge amount is blank or too long.");
        }
        final BigDecimal parsed;
        try {
            parsed = new BigDecimal(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Invalid challenge amount.", exception);
        }
        String canonical = canonical(parsed);
        if (!canonical.equals(value)) throw new IllegalArgumentException("Challenge amount is not canonical.");
        return parsed.stripTrailingZeros();
    }
}
