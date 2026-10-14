-- Reject incomplete or cross-Faculty period mappings before permanent DDL.
-- A temporary CHECK fails the migration without changing the business schema.
CREATE TEMPORARY TABLE v21_period_scope_guard (
    invalid_count BIGINT NOT NULL,
    CONSTRAINT chk_v21_period_scope_guard CHECK (invalid_count = 0)
);

INSERT INTO v21_period_scope_guard (invalid_count)
SELECT COUNT(*)
FROM registration_periods period
LEFT JOIN departments department ON department.id = period.department_id
WHERE period.department_id IS NULL
   OR department.id IS NULL
   OR department.faculty_id <> period.faculty_id;

DROP TEMPORARY TABLE v21_period_scope_guard;

-- The composite key makes the Faculty/Department relationship enforceable by FK.
ALTER TABLE departments
    ADD UNIQUE KEY uq_departments_id_faculty (id, faculty_id);

ALTER TABLE registration_periods
    DROP FOREIGN KEY fk_periods_department,
    MODIFY COLUMN department_id BIGINT NOT NULL,
    ADD KEY idx_periods_department_faculty (department_id, faculty_id),
    ADD CONSTRAINT fk_periods_department_faculty
        FOREIGN KEY (department_id, faculty_id)
        REFERENCES departments(id, faculty_id);
