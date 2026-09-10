ALTER TABLE topics
    ADD COLUMN hidden_at TIMESTAMP(6) NULL AFTER status,
    ADD COLUMN hidden_by BIGINT NULL AFTER hidden_at,
    ADD COLUMN hidden_reason VARCHAR(1000) NULL AFTER hidden_by,
    ADD KEY idx_topics_hidden_by (hidden_by),
    ADD CONSTRAINT chk_topics_hidden_metadata CHECK (
        (hidden_at IS NULL AND hidden_by IS NULL AND hidden_reason IS NULL)
        OR (
            hidden_at IS NOT NULL
            AND hidden_by IS NOT NULL
            AND COALESCE(TRIM(hidden_reason), '') <> ''
        )
    ),
    ADD CONSTRAINT fk_topics_hidden_by
        FOREIGN KEY (hidden_by) REFERENCES user_accounts(id);
