package vn.edu.topicregistration.service;

import vn.edu.topicregistration.entity.NotificationEventType;

public final class NotificationEventKey {
    private NotificationEventKey() {
    }

    public static String of(NotificationEventType type, long sourceId) {
        if (type == null) {
            throw new IllegalArgumentException("Notification event type is required");
        }
        if (sourceId <= 0) {
            throw new IllegalArgumentException("Notification source id must be positive");
        }
        return switch (type) {
            case ANNOUNCEMENT_PUBLISHED -> "ANNOUNCEMENT:" + sourceId + ":PUBLISHED";
            case GROUP_INVITATION_CREATED -> "GROUP_INVITATION:" + sourceId + ":CREATED";
            case GROUP_INVITATION_ACCEPTED -> "GROUP_INVITATION:" + sourceId + ":ACCEPTED";
            case GROUP_INVITATION_REJECTED -> "GROUP_INVITATION:" + sourceId + ":REJECTED";
            case TOPIC_PROPOSAL_APPROVED -> "TOPIC:" + sourceId + ":APPROVED";
            case TOPIC_PROPOSAL_REJECTED -> "TOPIC:" + sourceId + ":REJECTED";
            case REGISTRATION_ACCEPTED -> "REGISTRATION:" + sourceId + ":ACCEPTED";
            case REGISTRATION_REJECTED -> "REGISTRATION:" + sourceId + ":REJECTED";
            case REGISTRATION_AUTO_REJECTED -> "REGISTRATION:" + sourceId + ":AUTO_REJECTED";
            case DIRECT_GRADER_ASSIGNED -> "DIRECT_GRADING:" + sourceId + ":ASSIGNED";
            case COUNCIL_MEMBER_ASSIGNED -> "COUNCIL_MEMBER:" + sourceId + ":ASSIGNED";
            case RESULT_PUBLISHED -> "RESULT:" + sourceId + ":PUBLISHED";
        };
    }
}
