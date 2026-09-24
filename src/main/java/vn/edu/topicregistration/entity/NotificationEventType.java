package vn.edu.topicregistration.entity;

public enum NotificationEventType {
    ANNOUNCEMENT_PUBLISHED(NotificationCategory.ANNOUNCEMENT),
    GROUP_INVITATION_CREATED(NotificationCategory.ACADEMIC_EVENT),
    GROUP_INVITATION_ACCEPTED(NotificationCategory.ACADEMIC_EVENT),
    GROUP_INVITATION_REJECTED(NotificationCategory.ACADEMIC_EVENT),
    TOPIC_PROPOSAL_APPROVED(NotificationCategory.ACADEMIC_EVENT),
    TOPIC_PROPOSAL_REJECTED(NotificationCategory.ACADEMIC_EVENT),
    REGISTRATION_ACCEPTED(NotificationCategory.ACADEMIC_EVENT),
    REGISTRATION_REJECTED(NotificationCategory.ACADEMIC_EVENT),
    REGISTRATION_AUTO_REJECTED(NotificationCategory.ACADEMIC_EVENT),
    DIRECT_GRADER_ASSIGNED(NotificationCategory.ACADEMIC_EVENT),
    COUNCIL_MEMBER_ASSIGNED(NotificationCategory.ACADEMIC_EVENT),
    RESULT_PUBLISHED(NotificationCategory.ACADEMIC_EVENT);

    private final NotificationCategory category;

    NotificationEventType(NotificationCategory category) {
        this.category = category;
    }

    public NotificationCategory category() {
        return category;
    }
}
