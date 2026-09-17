-- Reuse the council/evaluation/result infrastructure for one-person COURSE/NCKH grading.
-- Existing rows remain regular councils; only the direct-grading service creates DIRECT rows.

SET NAMES utf8mb4 COLLATE utf8mb4_0900_ai_ci;
SET time_zone = '+00:00';

ALTER TABLE councils
    ADD COLUMN grading_mode VARCHAR(16) NOT NULL DEFAULT 'COUNCIL' AFTER secretary_id,
    DROP CHECK chk_councils_distinct_officers,
    ADD CONSTRAINT chk_councils_grading_mode CHECK (
        grading_mode IN ('COUNCIL', 'DIRECT')
    ),
    ADD CONSTRAINT chk_councils_grading_mode_officers CHECK (
        (grading_mode = 'COUNCIL' AND chair_id <> secretary_id)
        OR (grading_mode = 'DIRECT' AND chair_id = secretary_id)
    );

DROP TRIGGER trg_councils_add_officers;

DELIMITER $$
CREATE TRIGGER trg_councils_add_officers
AFTER INSERT ON councils
FOR EACH ROW
BEGIN
    INSERT INTO council_members(council_id, lecturer_id, slot)
    VALUES (NEW.id, NEW.chair_id, 1);

    IF NEW.secretary_id <> NEW.chair_id THEN
        INSERT INTO council_members(council_id, lecturer_id, slot)
        VALUES (NEW.id, NEW.secretary_id, 2);
    END IF;
END$$
DELIMITER ;
