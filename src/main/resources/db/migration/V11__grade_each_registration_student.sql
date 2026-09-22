-- Grade and publish results per student in the immutable registration snapshot.
-- Existing group-level scores/results are copied to every snapshot member so upgrades
-- preserve all previously published information without inventing different scores.

ALTER TABLE evaluations
    DROP INDEX uq_evaluations_actor_component,
    ADD COLUMN student_id BIGINT NULL AFTER registration_id;

UPDATE evaluations evaluation
JOIN registration_members member
  ON member.registration_id = evaluation.registration_id
 AND member.slot = (
     SELECT MIN(first_member.slot)
     FROM registration_members first_member
     WHERE first_member.registration_id = evaluation.registration_id
 )
SET evaluation.student_id = member.student_id;

INSERT INTO evaluations(
    registration_id, student_id, period_id, registration_status,
    evaluator_id, component_id, council_id, score, feedback, status,
    evaluated_at, submitted_at, version
)
SELECT
    evaluation.registration_id, member.student_id, evaluation.period_id,
    evaluation.registration_status, evaluation.evaluator_id, evaluation.component_id,
    evaluation.council_id, evaluation.score, evaluation.feedback, evaluation.status,
    evaluation.evaluated_at, evaluation.submitted_at, evaluation.version
FROM evaluations evaluation
JOIN registration_members member
  ON member.registration_id = evaluation.registration_id
WHERE evaluation.student_id IS NOT NULL
  AND member.student_id <> evaluation.student_id;

ALTER TABLE evaluations
    MODIFY COLUMN student_id BIGINT NOT NULL,
    ADD UNIQUE KEY uq_evaluations_student_actor_component
        (registration_id, student_id, evaluator_id, component_id),
    ADD KEY idx_evaluations_student (student_id),
    ADD CONSTRAINT fk_evaluations_registration_student
        FOREIGN KEY (registration_id, student_id)
        REFERENCES registration_members(registration_id, student_id);

ALTER TABLE final_results
    ADD COLUMN student_id BIGINT NULL AFTER registration_id;

UPDATE final_results result
JOIN registration_members member
  ON member.registration_id = result.registration_id
 AND member.slot = (
     SELECT MIN(first_member.slot)
     FROM registration_members first_member
     WHERE first_member.registration_id = result.registration_id
 )
SET result.student_id = member.student_id;

ALTER TABLE final_results
    DROP PRIMARY KEY;

INSERT INTO final_results(
    registration_id, student_id, registration_status, status, council_id,
    consolidated_by, consolidated_at, evaluation_summary, final_score,
    review_completed_at, grading_completed_at, published_by, published_at, version
)
SELECT
    result.registration_id, member.student_id, result.registration_status, result.status,
    result.council_id, result.consolidated_by, result.consolidated_at,
    result.evaluation_summary, result.final_score, result.review_completed_at,
    result.grading_completed_at, result.published_by, result.published_at, result.version
FROM final_results result
JOIN registration_members member
  ON member.registration_id = result.registration_id
WHERE result.student_id IS NOT NULL
  AND member.student_id <> result.student_id;

ALTER TABLE final_results
    MODIFY COLUMN student_id BIGINT NOT NULL,
    ADD PRIMARY KEY (registration_id, student_id),
    ADD KEY idx_final_results_student (student_id),
    ADD CONSTRAINT fk_final_results_registration_student
        FOREIGN KEY (registration_id, student_id)
        REFERENCES registration_members(registration_id, student_id);
