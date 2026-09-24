-- In-app notifications and official announcement delivery.
-- Additive migration: keeps all existing announcement rows and V1-V11 history intact.

ALTER TABLE announcements
    ADD COLUMN created_by BIGINT NULL AFTER faculty_id,
    ADD COLUMN created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) AFTER published_at,
    ADD COLUMN updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6) AFTER created_at,
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0 AFTER updated_at,
    ADD KEY idx_announcements_creator (created_by),
    ADD KEY idx_announcements_scope_state (faculty_id, published_at, updated_at),
    ADD CONSTRAINT chk_announcements_version CHECK (version >= 0),
    ADD CONSTRAINT fk_announcements_creator
        FOREIGN KEY (created_by) REFERENCES user_accounts(id);

UPDATE announcements
SET created_by = published_by
WHERE created_by IS NULL AND published_by IS NOT NULL;

CREATE TABLE notification_events (
    id BIGINT NOT NULL AUTO_INCREMENT,
    event_key VARCHAR(191) NOT NULL,
    category VARCHAR(32) NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    title VARCHAR(200) NOT NULL,
    body TEXT NOT NULL,
    actor_id BIGINT,
    announcement_id BIGINT,
    target_type VARCHAR(32) NOT NULL,
    target_id BIGINT,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_notification_events_key (event_key),
    UNIQUE KEY uq_notification_events_announcement (announcement_id),
    KEY idx_notification_events_actor (actor_id),
    KEY idx_notification_events_created (created_at, id),
    CONSTRAINT chk_notification_events_key CHECK (TRIM(event_key) <> ''),
    CONSTRAINT chk_notification_events_category CHECK (
        category IN ('ANNOUNCEMENT', 'ACADEMIC_EVENT')
    ),
    CONSTRAINT chk_notification_events_type CHECK (
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
            'COUNCIL_MEMBER_ASSIGNED',
            'RESULT_PUBLISHED'
        )
    ),
    CONSTRAINT chk_notification_events_title CHECK (TRIM(title) <> ''),
    CONSTRAINT chk_notification_events_body CHECK (TRIM(body) <> ''),
    CONSTRAINT chk_notification_events_target_type CHECK (
        target_type IN (
            'ANNOUNCEMENT', 'GROUP_INVITATION', 'GROUP', 'TOPIC',
            'REGISTRATION', 'DIRECT_GRADING', 'COUNCIL', 'RESULT', 'NONE'
        )
    ),
    CONSTRAINT chk_notification_events_target CHECK (
        (target_type = 'NONE' AND target_id IS NULL)
        OR (target_type <> 'NONE' AND target_id IS NOT NULL AND target_id > 0)
    ),
    CONSTRAINT chk_notification_events_announcement CHECK (
        (event_type = 'ANNOUNCEMENT_PUBLISHED' AND announcement_id IS NOT NULL)
        OR (event_type <> 'ANNOUNCEMENT_PUBLISHED' AND announcement_id IS NULL)
    ),
    CONSTRAINT fk_notification_events_actor
        FOREIGN KEY (actor_id) REFERENCES user_accounts(id),
    CONSTRAINT fk_notification_events_announcement
        FOREIGN KEY (announcement_id) REFERENCES announcements(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Một lần phát sinh nội dung thông báo bất biến';

CREATE TABLE user_notifications (
    id BIGINT NOT NULL AUTO_INCREMENT,
    event_id BIGINT NOT NULL,
    recipient_id BIGINT NOT NULL,
    delivered_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    read_at TIMESTAMP(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_user_notifications_event_recipient (event_id, recipient_id),
    KEY idx_user_notifications_recipient_read_time (
        recipient_id, read_at, delivered_at, id
    ),
    KEY idx_user_notifications_recipient_time (recipient_id, delivered_at, id),
    CONSTRAINT chk_user_notifications_read_time CHECK (
        read_at IS NULL OR read_at >= delivered_at
    ),
    CONSTRAINT fk_user_notifications_event
        FOREIGN KEY (event_id) REFERENCES notification_events(id),
    CONSTRAINT fk_user_notifications_recipient
        FOREIGN KEY (recipient_id) REFERENCES user_accounts(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Bản nhận và trạng thái đọc riêng của từng tài khoản';
