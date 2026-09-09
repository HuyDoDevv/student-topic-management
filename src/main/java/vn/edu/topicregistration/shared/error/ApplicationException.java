package vn.edu.topicregistration.shared.error;

import java.util.Objects;

public final class ApplicationException extends RuntimeException {
    private final ApplicationErrorCode code;

    public ApplicationException(ApplicationErrorCode code) {
        super(Objects.requireNonNull(code).name());
        this.code = code;
    }

    public ApplicationErrorCode code() {
        return code;
    }
}
