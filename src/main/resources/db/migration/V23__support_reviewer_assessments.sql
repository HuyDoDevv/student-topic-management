-- Preserve V1 reviewer rows without claiming that they were submitted or audited.
-- Only newly assigned rows participate in the one-active-reviewer invariant.
SET NAMES utf8mb4 COLLATE utf8mb4_0900_ai_ci;
SET time_zone = '+00:00';

ALTER TABLE reviewer_assignments
    ADD COLUMN id BIGINT NOT NULL AUTO_INCREMENT FIRST,
    DROP PRIMARY KEY,
    ADD PRIMARY KEY (id),
    ADD KEY idx_reviewers_registration_status (registration_id, registration_status),
    ADD KEY idx_reviewers_registration_lecturer (registration_id, lecturer_id),
    ADD UNIQUE KEY uq_reviewers_id_registration (id, registration_id),
    ADD COLUMN deadline_date DATE NULL AFTER lecturer_id,
    ADD COLUMN status VARCHAR(32) NOT NULL DEFAULT 'LEGACY_UNVERIFIED' AFTER deadline_date,
    ADD COLUMN assigned_by BIGINT NULL AFTER status,
    ADD COLUMN assigned_at TIMESTAMP(6) NULL AFTER assigned_by,
    ADD COLUMN submitted_at TIMESTAMP(6) NULL AFTER assigned_at,
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0 AFTER submitted_at,
    ADD COLUMN active_registration_guard BIGINT GENERATED ALWAYS AS (
        CASE WHEN status IN ('ASSIGNED','SUBMITTED') THEN registration_id ELSE NULL END
    ) STORED AFTER version,
    ADD UNIQUE KEY uq_reviewers_one_active_registration (active_registration_guard),
    ADD KEY idx_reviewers_assigned_by (assigned_by),
    ADD CONSTRAINT chk_reviewers_status CHECK (
        (status = 'LEGACY_UNVERIFIED' AND deadline_date IS NULL
            AND assigned_by IS NULL AND assigned_at IS NULL AND submitted_at IS NULL)
        OR (status = 'ASSIGNED' AND deadline_date IS NOT NULL
            AND assigned_by IS NOT NULL AND assigned_at IS NOT NULL
            AND submitted_at IS NULL)
        OR (status = 'SUBMITTED' AND deadline_date IS NOT NULL
            AND assigned_by IS NOT NULL AND assigned_at IS NOT NULL
            AND submitted_at IS NOT NULL)
    ),
    ADD CONSTRAINT chk_reviewers_version CHECK (version >= 0),
    ADD CONSTRAINT fk_reviewers_assigned_by FOREIGN KEY (assigned_by)
        REFERENCES user_accounts(id);

CREATE TABLE reviewer_reports (
    id BIGINT NOT NULL AUTO_INCREMENT,
    assignment_id BIGINT NOT NULL,
    file_name VARCHAR(500) NOT NULL,
    storage_key VARCHAR(1000) NOT NULL,
    content_type VARCHAR(255) NOT NULL,
    size_bytes BIGINT NOT NULL,
    submitted_by BIGINT NOT NULL,
    submitted_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_reviewer_reports_assignment (assignment_id),
    KEY idx_reviewer_reports_submitter (submitted_by),
    CONSTRAINT chk_reviewer_reports_file_name CHECK (TRIM(file_name) <> ''),
    CONSTRAINT chk_reviewer_reports_storage_key CHECK (TRIM(storage_key) <> ''),
    CONSTRAINT chk_reviewer_reports_content_type CHECK (content_type = 'application/pdf'),
    CONSTRAINT chk_reviewer_reports_size CHECK (size_bytes BETWEEN 1 AND 20971520),
    CONSTRAINT fk_reviewer_reports_assignment FOREIGN KEY (assignment_id)
        REFERENCES reviewer_assignments(id),
    CONSTRAINT fk_reviewer_reports_submitter FOREIGN KEY (submitted_by)
        REFERENCES user_accounts(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Metadata báo cáo PDF do giảng viên phản biện nộp';

CREATE TABLE reviewer_evaluations (
    id BIGINT NOT NULL AUTO_INCREMENT,
    assignment_id BIGINT NOT NULL,
    registration_id BIGINT NOT NULL,
    student_id BIGINT NOT NULL,
    score DECIMAL(12,4) NOT NULL,
    feedback TEXT,
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    submitted_at TIMESTAMP(6) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uq_reviewer_evaluations_assignment_student (assignment_id, student_id),
    KEY idx_reviewer_evaluations_assignment_registration (assignment_id, registration_id),
    KEY idx_reviewer_evaluations_registration_student (registration_id, student_id),
    CONSTRAINT chk_reviewer_evaluations_score CHECK (
        score BETWEEN 0.00 AND 10.00 AND score = ROUND(score, 2)
    ),
    CONSTRAINT chk_reviewer_evaluations_submission CHECK (
        (status = 'DRAFT' AND submitted_at IS NULL)
        OR (status = 'SUBMITTED' AND submitted_at IS NOT NULL)
    ),
    CONSTRAINT chk_reviewer_evaluations_version CHECK (version >= 0),
    CONSTRAINT fk_reviewer_evaluations_assignment_registration
        FOREIGN KEY (assignment_id, registration_id)
        REFERENCES reviewer_assignments(id, registration_id),
    CONSTRAINT fk_reviewer_evaluations_member
        FOREIGN KEY (registration_id, student_id)
        REFERENCES registration_members(registration_id, student_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Điểm phản biện riêng của từng sinh viên trong đăng ký';
