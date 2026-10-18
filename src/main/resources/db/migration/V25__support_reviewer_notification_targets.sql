-- Extend the notification contract without changing historical V12 migrations or rows.
ALTER TABLE notification_events
    DROP CHECK chk_notification_events_type,
    ADD CONSTRAINT chk_notification_events_type CHECK (
        event_type IN (
            'ANNOUNCEMENT_PUBLISHED',
            'GROUP_INVITATION_CREATED',
            'GROUP_INVITATION_ACCEPTED',
            'GROUP_INVITATION_REJECTED',
            'TOPIC_PROPOSAL_APPROVED',
            'TOPIC_PROPOSAL_REJECTED',
            'REGISTRATION_ACCEPTED',
            'REGISTRATION_REJECTED',
            'REGISTRATION_AUTO_REJECTED',
            'DIRECT_GRADER_ASSIGNED',
            'REVIEWER_ASSIGNED',
            'COUNCIL_MEMBER_ASSIGNED',
            'RESULT_PUBLISHED'
        )
    ),
    DROP CHECK chk_notification_events_target_type,
    ADD CONSTRAINT chk_notification_events_target_type CHECK (
        target_type IN (
            'ANNOUNCEMENT', 'GROUP_INVITATION', 'GROUP', 'TOPIC',
            'REGISTRATION', 'DIRECT_GRADING', 'REVIEWER_ASSIGNMENT',
            'COUNCIL', 'RESULT', 'NONE'
        )
    );
