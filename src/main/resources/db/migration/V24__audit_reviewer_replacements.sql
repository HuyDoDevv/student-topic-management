-- Keep the original assignment as an immutable audit row when a DEAN replaces
-- an unsubmitted reviewer. The V23 active guard already excludes REPLACED rows.
ALTER TABLE reviewer_assignments
    ADD COLUMN replaced_by BIGINT NULL AFTER submitted_at,
    ADD COLUMN replaced_at TIMESTAMP(6) NULL AFTER replaced_by,
    ADD KEY idx_reviewers_replaced_by (replaced_by),
    ADD CONSTRAINT fk_reviewers_replaced_by FOREIGN KEY (replaced_by)
        REFERENCES user_accounts(id),
    DROP CHECK chk_reviewers_status,
    ADD CONSTRAINT chk_reviewers_status CHECK (
        (status = 'LEGACY_UNVERIFIED' AND deadline_date IS NULL
            AND assigned_by IS NULL AND assigned_at IS NULL
            AND submitted_at IS NULL AND replaced_by IS NULL AND replaced_at IS NULL)
        OR (status = 'ASSIGNED' AND deadline_date IS NOT NULL
            AND assigned_by IS NOT NULL AND assigned_at IS NOT NULL
            AND submitted_at IS NULL AND replaced_by IS NULL AND replaced_at IS NULL)
        OR (status = 'SUBMITTED' AND deadline_date IS NOT NULL
            AND assigned_by IS NOT NULL AND assigned_at IS NOT NULL
            AND submitted_at IS NOT NULL AND replaced_by IS NULL AND replaced_at IS NULL)
        OR (status = 'REPLACED' AND deadline_date IS NOT NULL
            AND assigned_by IS NOT NULL AND assigned_at IS NOT NULL
            AND submitted_at IS NULL AND replaced_by IS NOT NULL AND replaced_at IS NOT NULL)
    );
