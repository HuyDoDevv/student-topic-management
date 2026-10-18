package vn.edu.topicregistration.entity;

/** Validated plain-text content of an official announcement draft. */
public record Announcement(String title, String body) {
    public static final int MAX_TITLE_LENGTH = 200;
    public static final int MAX_BODY_LENGTH = 5_000;

    public Announcement {
        title = normalize(title, MAX_TITLE_LENGTH, "title");
        body = normalize(body, MAX_BODY_LENGTH, "body");
    }

    private static String normalize(String value, int maxLength, String field) {
        String normalized = value == null ? null : value.strip();
        if (normalized == null || normalized.isEmpty() || normalized.length() > maxLength) {
            throw new IllegalArgumentException("Invalid announcement " + field);
        }
        return normalized;
    }
}
