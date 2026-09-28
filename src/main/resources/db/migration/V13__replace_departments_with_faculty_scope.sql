-- Replace department-scoped identity and academic data with direct faculty scope.
-- The current product operates for one faculty only, while retaining a generic
-- faculties table so multi-faculty provisioning can be added later.

SET NAMES utf8mb4 COLLATE utf8mb4_0900_ai_ci;
SET time_zone = '+00:00';

INSERT INTO faculties(name)
SELECT 'Khoa Công nghệ Thông tin'
WHERE NOT EXISTS (
    SELECT 1 FROM faculties WHERE name = 'Khoa Công nghệ Thông tin'
);

SET @canonical_faculty_id := (
    SELECT MIN(id) FROM faculties WHERE name = 'Khoa Công nghệ Thông tin'
);

UPDATE faculties
SET name = 'Khoa Công nghệ Thông tin'
WHERE id = @canonical_faculty_id;

ALTER TABLE user_accounts
    ADD COLUMN faculty_id BIGINT NULL AFTER password_hash,
    ADD KEY idx_accounts_faculty (faculty_id),
    ADD CONSTRAINT fk_accounts_faculty
        FOREIGN KEY (faculty_id) REFERENCES faculties(id);

UPDATE user_accounts account
SET account.faculty_id = @canonical_faculty_id
WHERE EXISTS (
    SELECT 1
    FROM user_roles assignment
    WHERE assignment.user_id = account.id
      AND assignment.role_code IN ('STUDENT', 'LECTURER', 'DEAN')
);

UPDATE registration_periods
SET faculty_id = @canonical_faculty_id;

UPDATE announcements
SET faculty_id = @canonical_faculty_id
WHERE faculty_id IS NOT NULL;

ALTER TABLE councils
    DROP FOREIGN KEY fk_councils_department,
    DROP INDEX idx_councils_period_department_status,
    DROP INDEX idx_councils_department,
    DROP COLUMN department_id;

ALTER TABLE topics
    DROP FOREIGN KEY fk_topics_department,
    DROP INDEX idx_topics_department,
    DROP COLUMN department_id;

ALTER TABLE user_accounts
    DROP FOREIGN KEY fk_accounts_department,
    DROP INDEX idx_accounts_department,
    DROP COLUMN department_id;

DROP TABLE departments;

DELETE FROM faculties
WHERE id <> @canonical_faculty_id;
