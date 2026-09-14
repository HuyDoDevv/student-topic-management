-- MySQL 8.4 LTS: implement report, council, evaluation and result lifecycles.
-- V1 created forward-compatible placeholder tables; V8 only extends those tables.
-- Invalid legacy academic rows are intentionally rejected by the new constraints.

SET NAMES utf8mb4 COLLATE utf8mb4_0900_ai_ci;
SET time_zone = '+00:00';

ALTER TABLE project_reports
    ADD COLUMN replaced_at TIMESTAMP(6) NULL AFTER size_bytes,
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0 AFTER replaced_at;

-- The row with the greatest id is the current legacy report. Older rows remain as history.
UPDATE project_reports report
JOIN (
    SELECT registration_id, MAX(id) AS current_id
    FROM project_reports
    GROUP BY registration_id
) latest ON latest.registration_id = report.registration_id
SET report.replaced_at = COALESCE(report.replaced_at, report.submitted_at)
WHERE report.id <> latest.current_id;

ALTER TABLE project_reports
    DROP CHECK chk_project_reports_size,
    MODIFY COLUMN content_type VARCHAR(255) NOT NULL,
    MODIFY COLUMN size_bytes BIGINT NOT NULL,
    ADD COLUMN current_registration_guard BIGINT GENERATED ALWAYS AS (
        CASE WHEN replaced_at IS NULL THEN registration_id ELSE NULL END
    ) STORED AFTER version,
    ADD UNIQUE KEY uq_project_reports_current_registration (current_registration_guard),
    ADD CONSTRAINT chk_project_reports_content_type CHECK (content_type = 'application/pdf'),
    ADD CONSTRAINT chk_project_reports_size CHECK (size_bytes BETWEEN 1 AND 20971520),
    ADD CONSTRAINT chk_project_reports_version CHECK (version >= 0);

ALTER TABLE councils
    ADD COLUMN department_id BIGINT NULL AFTER period_id,
    ADD COLUMN status VARCHAR(32) NOT NULL DEFAULT 'DRAFT' AFTER secretary_id,
    ADD COLUMN created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) AFTER status,
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0 AFTER created_at;

-- Before V8 a council had no department. Its chair's trusted organization assignment is
-- the only conservative source for backfill; a missing assignment makes MODIFY fail.
UPDATE councils council
JOIN user_accounts chair ON chair.id = council.chair_id
SET council.department_id = chair.department_id
WHERE council.department_id IS NULL;

ALTER TABLE councils
    MODIFY COLUMN department_id BIGINT NOT NULL,
    ADD KEY idx_councils_department (department_id),
    ADD KEY idx_councils_period_department_status (period_id, department_id, status),
    ADD CONSTRAINT chk_councils_status CHECK (
        status IN ('DRAFT','ASSIGNED','SCHEDULED','ACTIVE','COMPLETED','PUBLISHED')
    ),
    ADD CONSTRAINT chk_councils_version CHECK (version >= 0),
    ADD CONSTRAINT fk_councils_department FOREIGN KEY (department_id) REFERENCES departments(id);

ALTER TABLE council_topics
    ADD UNIQUE KEY uq_council_topics_registration (registration_id),
    ADD CONSTRAINT chk_council_topics_schedule CHECK (
        (scheduled_at IS NULL AND location IS NULL)
        OR (scheduled_at IS NOT NULL AND COALESCE(TRIM(location), '') <> '')
    );

ALTER TABLE evaluations
    ADD COLUMN status VARCHAR(32) NOT NULL DEFAULT 'DRAFT' AFTER feedback,
    ADD COLUMN submitted_at TIMESTAMP(6) NULL AFTER evaluated_at,
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0 AFTER submitted_at;

-- Every pre-V8 evaluation represented an already recorded score.
UPDATE evaluations
SET status = 'SUBMITTED', submitted_at = evaluated_at;

ALTER TABLE evaluations
    MODIFY COLUMN council_id BIGINT NOT NULL,
    ADD CONSTRAINT chk_evaluations_lifecycle CHECK (status IN ('DRAFT','SUBMITTED')),
    ADD CONSTRAINT chk_evaluations_score CHECK (
        score BETWEEN 0.00 AND 10.00 AND score = ROUND(score, 2)
    ),
    ADD CONSTRAINT chk_evaluations_submission CHECK (
        (status = 'DRAFT' AND submitted_at IS NULL)
        OR (status = 'SUBMITTED' AND submitted_at IS NOT NULL)
    ),
    ADD CONSTRAINT chk_evaluations_version CHECK (version >= 0);

ALTER TABLE final_results
    ADD COLUMN status VARCHAR(32) NOT NULL DEFAULT 'READY' AFTER registration_status,
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0 AFTER published_at;

UPDATE final_results
SET status = CASE WHEN published_at IS NULL THEN 'READY' ELSE 'PUBLISHED' END;

ALTER TABLE final_results
    MODIFY COLUMN council_id BIGINT NOT NULL,
    MODIFY COLUMN consolidated_by BIGINT NOT NULL,
    MODIFY COLUMN consolidated_at TIMESTAMP(6) NOT NULL,
    MODIFY COLUMN evaluation_summary TEXT NOT NULL,
    MODIFY COLUMN final_score DECIMAL(12,4) NOT NULL,
    MODIFY COLUMN review_completed_at TIMESTAMP(6) NOT NULL,
    MODIFY COLUMN grading_completed_at TIMESTAMP(6) NOT NULL,
    ADD CONSTRAINT chk_final_results_lifecycle CHECK (
        (
            status = 'READY'
            AND published_by IS NULL
            AND published_at IS NULL
        ) OR (
            status = 'PUBLISHED'
            AND published_by IS NOT NULL
            AND published_at IS NOT NULL
            AND published_at >= review_completed_at
            AND published_at >= grading_completed_at
        )
    ),
    ADD CONSTRAINT chk_final_results_score CHECK (
        final_score BETWEEN 0.00 AND 10.00 AND final_score = ROUND(final_score, 2)
    ),
    ADD CONSTRAINT chk_final_results_version CHECK (version >= 0);
