package vn.edu.topicregistration.security;

import java.nio.charset.StandardCharsets;

public final class PasswordPolicy {
    public static final int MIN_LENGTH = 8;
    public static final int MAX_LENGTH = 72;
    public static final int MAX_BCRYPT_BYTES = 72;

    private PasswordPolicy() {
    }

    public static boolean isValid(String password) {
        if (password == null || password.isBlank()
                || password.length() < MIN_LENGTH || password.length() > MAX_LENGTH) {
            return false;
        }
        return password.getBytes(StandardCharsets.UTF_8).length <= MAX_BCRYPT_BYTES;
    }

    public static boolean isWithinBcryptLimit(String password) {
        return password == null
                || password.getBytes(StandardCharsets.UTF_8).length <= MAX_BCRYPT_BYTES;
    }
}
