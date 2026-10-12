-- Restore department as an organizational scope without changing runtime behavior.
-- Existing accounts and topics remain unassigned until a later verified backfill.

SET NAMES utf8mb4 COLLATE utf8mb4_0900_ai_ci;
SET time_zone = '+00:00';

CREATE TABLE departments (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(255) NOT NULL,
    faculty_id BIGINT NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_departments_faculty_name (faculty_id, name),
    KEY idx_departments_faculty (faculty_id),
    CONSTRAINT chk_departments_name CHECK (TRIM(name) <> ''),
    CONSTRAINT fk_departments_faculty
        FOREIGN KEY (faculty_id) REFERENCES faculties(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Bộ môn thuộc khoa';

ALTER TABLE user_accounts
    ADD COLUMN department_id BIGINT NULL AFTER faculty_id,
    ADD KEY idx_accounts_department (department_id),
    ADD CONSTRAINT fk_accounts_department
        FOREIGN KEY (department_id) REFERENCES departments(id);

ALTER TABLE topics
    ADD COLUMN department_id BIGINT NULL AFTER period_id,
    ADD KEY idx_topics_department (department_id),
    ADD CONSTRAINT fk_topics_department
        FOREIGN KEY (department_id) REFERENCES departments(id);
