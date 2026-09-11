ALTER TABLE registrations
    ADD CONSTRAINT chk_registrations_rejection_metadata CHECK (
        (
            status = 'REJECTED'
            AND rejection_kind IS NOT NULL
            AND rejection_kind IN ('MANUAL', 'TOPIC_TAKEN')
            AND rejection_reason IS NOT NULL
            AND CHAR_LENGTH(TRIM(rejection_reason)) > 0
        )
        OR
        (
            status <> 'REJECTED'
            AND rejection_kind IS NULL
            AND rejection_reason IS NULL
        )
    );
