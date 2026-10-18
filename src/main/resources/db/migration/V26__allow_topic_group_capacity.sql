ALTER TABLE topics
    ADD COLUMN max_group_count INT NOT NULL DEFAULT 1 AFTER status,
    ADD CONSTRAINT chk_topics_max_group_count CHECK (max_group_count BETWEEN 1 AND 5);

ALTER TABLE registrations
    DROP INDEX uq_one_accepted_group_per_topic,
    DROP COLUMN accepted_topic_guard,
    ADD KEY idx_registrations_topic_status (topic_id, status);

ALTER TABLE registrations
    DROP CHECK chk_registrations_auto_reason;

UPDATE registrations
SET rejection_reason = 'Đề tài đã đủ số lượng nhóm'
WHERE rejection_kind = 'TOPIC_TAKEN'
  AND rejection_reason = 'Đề tài đã nhận nhóm';

ALTER TABLE registrations
    ADD CONSTRAINT chk_registrations_auto_reason CHECK (
        rejection_kind IS NULL OR rejection_kind <> 'TOPIC_TAKEN' OR (
            status = 'REJECTED'
            AND rejection_reason IS NOT NULL
            AND rejection_reason IN (
                'Đề tài đã nhận nhóm',
                'Đề tài đã đủ số lượng nhóm'
            )
        )
    );
