-- Expand-only: retain legacy periods until their Department mapping is confirmed.
-- Faculty/Department consistency and NOT NULL are enforced in later tasks.

ALTER TABLE registration_periods
    ADD COLUMN department_id BIGINT NULL AFTER faculty_id,
    ADD KEY idx_periods_department (department_id),
    ADD CONSTRAINT fk_periods_department
        FOREIGN KEY (department_id) REFERENCES departments(id);
