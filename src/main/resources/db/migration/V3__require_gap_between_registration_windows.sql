-- The lecturer proposal window must finish before student registration begins.
-- Existing rows with equal boundary instants must be corrected before this migration runs.

ALTER TABLE registration_periods
    DROP CHECK chk_periods_windows,
    ADD CONSTRAINT chk_periods_windows CHECK (
        lecturer_start < lecturer_end
        AND lecturer_end < student_start
        AND student_start < student_end
    );
