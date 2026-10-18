package vn.edu.topicregistration.service;

import java.util.Objects;
import java.util.Optional;

public interface ActiveRegistrationQuery {
    boolean existsForGroup(long periodId, long groupId);

    Optional<ActiveRegistration> findForGroup(long periodId, long groupId);

    enum ActiveRegistrationStatus {
        PENDING,
        ACCEPTED
    }

    record ActiveRegistration(
            long id,
            String topicTitle,
            ActiveRegistrationStatus status) {
        public ActiveRegistration {
            topicTitle = Objects.requireNonNull(topicTitle, "topicTitle");
            status = Objects.requireNonNull(status, "status");
        }
    }
}
