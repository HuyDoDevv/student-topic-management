package vn.edu.topicregistration.service;

import org.springframework.stereotype.Component;

import vn.edu.topicregistration.entity.NotificationTargetType;

@Component
public class NotificationTargetResolver {
    public String resolve(NotificationTargetType type, Long targetId) {
        if (type == null) {
            throw new IllegalArgumentException("Notification target type is required");
        }
        if (type == NotificationTargetType.NONE) {
            if (targetId != null) {
                throw new IllegalArgumentException("NONE notification target cannot have an id");
            }
            return "/notifications";
        }
        long id = requirePositive(targetId);
        return switch (type) {
            case ANNOUNCEMENT -> "/announcements/" + id;
            case GROUP_INVITATION -> "/group-invitations";
            case GROUP -> "/groups/" + id;
            case TOPIC -> "/topics/" + id;
            case REGISTRATION -> "/registrations/" + id;
            case DIRECT_GRADING -> "/lecturer/direct-grading";
            case COUNCIL -> "/lecturer/scoring";
            case RESULT -> "/student/results/" + id;
            case NONE -> throw new IllegalStateException("NONE handled before target switch");
        };
    }

    private static long requirePositive(Long targetId) {
        if (targetId == null || targetId <= 0) {
            throw new IllegalArgumentException("Notification target id must be positive");
        }
        return targetId;
    }
}
