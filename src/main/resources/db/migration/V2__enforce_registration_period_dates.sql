-- Q-02 / T017: make the two calendar dates mandatory exactly for the types that use them.
-- V1 is already shared, so this migration replaces its permissive checks without rewriting history.

ALTER TABLE registration_periods
    DROP CHECK chk_periods_reviewer_date,
    DROP CHECK chk_periods_council_date,
    ADD CONSTRAINT chk_periods_dates_by_type CHECK (
        (
            topic_type IN ('COURSE', 'NCKH')
            AND reviewer_score_deadline IS NULL
            AND council_report_date IS NULL
        )
        OR (
            topic_type = 'TLCN'
            AND reviewer_score_deadline IS NOT NULL
            AND council_report_date IS NULL
        )
        OR (
            topic_type = 'KLTN'
            AND reviewer_score_deadline IS NOT NULL
            AND council_report_date IS NOT NULL
        )
    );
