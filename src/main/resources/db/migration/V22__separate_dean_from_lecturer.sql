-- Do not choose which DEAN to retain when legacy data violates faculty uniqueness.
CREATE TEMPORARY TABLE v22_active_dean_guard (
    invalid_count BIGINT NOT NULL,
    CONSTRAINT chk_v22_active_dean_guard CHECK (invalid_count = 0)
);

INSERT INTO v22_active_dean_guard (invalid_count)
SELECT COUNT(*)
FROM (
    SELECT account.faculty_id
    FROM user_accounts account
    JOIN user_roles dean ON dean.user_id = account.id AND dean.role_code = 'DEAN'
    WHERE account.enabled = TRUE AND account.faculty_id IS NOT NULL
    GROUP BY account.faculty_id
    HAVING COUNT(*) > 1
) duplicate_deans;

DROP TEMPORARY TABLE v22_active_dean_guard;

-- Existing sessions carry the former LECTURER authority; expire them first.
UPDATE user_accounts account
JOIN user_roles dean ON dean.user_id = account.id AND dean.role_code = 'DEAN'
JOIN user_roles lecturer ON lecturer.user_id = account.id AND lecturer.role_code = 'LECTURER'
SET account.security_version = account.security_version + 1;

-- Preserve accounts and every historical reference to them.
DELETE lecturer
FROM user_roles lecturer
JOIN user_roles dean ON dean.user_id = lecturer.user_id AND dean.role_code = 'DEAN'
WHERE lecturer.role_code = 'LECTURER';
