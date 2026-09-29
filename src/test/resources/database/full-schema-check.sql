-- MySQL 8.4 verification fixture.
-- Run after Flyway has migrated a disposable test database.
-- Business fixture rows are rolled back. The helper procedure is removed at the end.

DROP PROCEDURE IF EXISTS expect_failure;
DELIMITER $$
CREATE PROCEDURE expect_failure(IN statement_text LONGTEXT)
BEGIN
    DECLARE failed BOOLEAN DEFAULT FALSE;
    BEGIN
        DECLARE CONTINUE HANDLER FOR SQLEXCEPTION SET failed = TRUE;
        SET @checked_sql = statement_text;
        PREPARE checked_statement FROM @checked_sql;
        EXECUTE checked_statement;
        DEALLOCATE PREPARE checked_statement;
    END;
    IF failed = FALSE THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Expected statement to fail, but it succeeded';
    END IF;
END$$
DELIMITER ;

START TRANSACTION;

INSERT INTO faculties(id, name) VALUES (-1, 'Test faculty');
INSERT INTO user_accounts(id, username, password_hash, faculty_id) VALUES
    (-1, 'schema-check-1', 'NOT_A_LOGIN_HASH', -1),
    (-2, 'schema-check-2', 'NOT_A_LOGIN_HASH', -1),
    (-3, 'schema-check-3', 'NOT_A_LOGIN_HASH', -1),
    (-4, 'schema-check-4', 'NOT_A_LOGIN_HASH', -1),
    (-5, 'schema-check-5', 'NOT_A_LOGIN_HASH', -1),
    (-6, 'schema-check-6', 'NOT_A_LOGIN_HASH', -1),
    (-7, 'schema-check-7', 'NOT_A_LOGIN_HASH', -1),
    (-8, 'schema-check-8', 'NOT_A_LOGIN_HASH', -1);
INSERT INTO user_roles(user_id, role_code) VALUES
    (-1, 'LECTURER'), (-1, 'ADMIN'), (-2, 'LECTURER'), (-3, 'STUDENT');
UPDATE user_accounts
SET email = '23110001@student.hcmute.edu.vn'
WHERE id = -3;
INSERT INTO student_profiles(user_id, student_code, class_name, cohort_code)
VALUES (-3, '23110001', '23110CL1', 'K2023');
CALL expect_failure('UPDATE user_accounts SET email=''23110001@student.hcmute.edu.vn'' WHERE id=-4');
CALL expect_failure('UPDATE user_accounts SET security_version=-1 WHERE id=-4');
CALL expect_failure('INSERT INTO student_profiles(user_id,student_code,class_name,cohort_code) VALUES (-4,''23110001'',''23110CL2'',''K2023'')');
CALL expect_failure('INSERT INTO student_profiles(user_id,student_code,class_name,cohort_code) VALUES (-5,''23110002'',''23110CL2'',''2023'')');

INSERT INTO registration_periods(
    id, faculty_id, name, topic_type,
    lecturer_start, lecturer_end, student_start, student_end
) VALUES
    (-1, -1, 'Test period', 'COURSE', '2026-01-01 00:00:00', '2026-02-01 00:00:00',
     '2026-02-02 00:00:00', '2026-03-01 00:00:00'),
    (-2, -1, 'Other period', 'COURSE', '2026-01-01 00:00:00', '2026-02-01 00:00:00',
     '2026-02-02 00:00:00', '2026-03-01 00:00:00');

INSERT INTO topics(id, period_id, author_id) VALUES
    (-1, -1, -1), (-2, -1, -1), (-3, -2, -1);
INSERT INTO topic_supervisors(topic_id, lecturer_id, slot) VALUES
    (-1, -1, 1), (-1, -2, 2);

-- Inserting student_groups automatically creates the leader membership in slot 1.
INSERT INTO student_groups(id, period_id, leader_student_id) VALUES
    (-1, -1, -3), (-2, -1, -6);
INSERT INTO group_memberships(group_id, period_id, student_id, slot) VALUES
    (-1, -1, -4, 2), (-1, -1, -5, 3),
    (-2, -1, -7, 2), (-2, -1, -8, 3);

CALL expect_failure('INSERT INTO topic_supervisors(topic_id,lecturer_id,slot) VALUES (-1,-8,3)');
CALL expect_failure('INSERT INTO group_memberships(group_id,period_id,student_id,slot) VALUES (-1,-1,-8,4)');
CALL expect_failure('INSERT INTO group_memberships(group_id,period_id,student_id,slot) VALUES (-2,-1,-3,2)');
CALL expect_failure('UPDATE student_groups SET leader_student_id=-8 WHERE id=-1');
CALL expect_failure('DELETE FROM group_memberships WHERE group_id=-1 AND student_id=-3');
CALL expect_failure('UPDATE topics SET status=''PUBLISHED'' WHERE id=-1');
CALL expect_failure('UPDATE registration_periods SET student_start=lecturer_start WHERE id=-1');
CALL expect_failure('UPDATE registration_periods SET student_start=lecturer_end WHERE id=-1');
CALL expect_failure('UPDATE registration_periods SET council_report_date=CURRENT_DATE WHERE id=-1');
CALL expect_failure('INSERT INTO registrations(period_id,group_id,topic_id) VALUES (-1,-1,-3)');

INSERT INTO registrations(id, period_id, group_id, topic_id) VALUES
    (-1, -1, -1, -1), (-2, -1, -2, -1);
CALL expect_failure('UPDATE registration_members SET leader_at_submission=FALSE WHERE registration_id=-1 AND student_id=-3');
CALL expect_failure('DELETE FROM registration_members WHERE registration_id=-1 AND student_id=-4');
CALL expect_failure('INSERT INTO registrations(period_id,group_id,topic_id) VALUES (-1,-1,-2)');
UPDATE registrations SET status='ACCEPTED', decided_at=CURRENT_TIMESTAMP(6) WHERE id=-1;
CALL expect_failure('UPDATE registrations SET status=''ACCEPTED'', decided_at=CURRENT_TIMESTAMP(6) WHERE id=-2');
CALL expect_failure('UPDATE registrations SET status=''REJECTED'', rejection_kind=''TOPIC_TAKEN'' WHERE id=-2');
CALL expect_failure('UPDATE registrations SET status=''REJECTED'', rejection_kind=''TOPIC_TAKEN'', rejection_reason=NULL WHERE id=-2');
UPDATE registrations
SET status='REJECTED', rejection_kind='TOPIC_TAKEN', rejection_reason='Đề tài đã nhận nhóm'
WHERE id=-2;
INSERT INTO registrations(period_id, group_id, topic_id) VALUES (-1, -2, -2);

INSERT INTO reviewer_assignments(registration_id, lecturer_id) VALUES (-1, -2);
INSERT INTO project_reports(
    registration_id, submitted_by, file_name, storage_key, content_type, size_bytes
)
VALUES (-1, -3, 'report.pdf', 'test/report.pdf', 'application/pdf', 128);

-- Inserting councils automatically creates chair slot 1 and secretary slot 2.
INSERT INTO councils(id, period_id, name, chair_id, secretary_id)
VALUES (-1, -1, 'Test council', -1, -2);
INSERT INTO council_members(council_id, lecturer_id, slot) VALUES (-1, -8, 3);
CALL expect_failure('INSERT INTO council_members(council_id,lecturer_id,slot) VALUES (-1,-7,6)');
CALL expect_failure('UPDATE councils SET chair_id=-7 WHERE id=-1');
CALL expect_failure('DELETE FROM council_members WHERE council_id=-1 AND lecturer_id=-1');

INSERT INTO council_topics(council_id, registration_id, period_id) VALUES (-1, -1, -1);
INSERT INTO score_components(id, period_id, name) VALUES
    (-1, -1, 'Review'), (-2, -2, 'Other');
INSERT INTO evaluations(
    registration_id, student_id, period_id, evaluator_id, component_id, council_id, score
) VALUES (-1, -3, -1, -2, -1, -1, 8.0);
INSERT INTO final_results(
    registration_id, student_id, council_id, consolidated_by, consolidated_at,
    evaluation_summary, final_score, review_completed_at, grading_completed_at
) VALUES (
    -1, -3, -1, -1, CURRENT_TIMESTAMP(6),
    'Test summary', 8.0, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)
);
INSERT INTO announcements(id, title, body, source_name)
VALUES (900000001, 'Test', 'Content', 'Khoa');
INSERT INTO notification_events(
    id, event_key, category, event_type, title, body,
    announcement_id, target_type, target_id
) VALUES (
    900000001, 'ANNOUNCEMENT:900000001:PUBLISHED', 'ANNOUNCEMENT',
    'ANNOUNCEMENT_PUBLISHED', 'Test', 'Content',
    900000001, 'ANNOUNCEMENT', 900000001
);
INSERT INTO user_notifications(id, event_id, recipient_id)
VALUES (900000001, 900000001, -3);

CALL expect_failure('INSERT INTO reviewer_assignments(registration_id,lecturer_id) VALUES (-2,-8)');
CALL expect_failure('INSERT INTO project_reports(registration_id,submitted_by,file_name,storage_key,content_type,size_bytes) VALUES (-2,-6,''report.pdf'',''path'',''application/pdf'',128)');
CALL expect_failure('INSERT INTO evaluations(registration_id,student_id,period_id,evaluator_id,component_id,council_id,score) VALUES (-1,-3,-1,-8,-2,-1,8)');
CALL expect_failure('INSERT INTO evaluations(registration_id,student_id,period_id,evaluator_id,component_id,council_id,score) VALUES (-1,-3,-1,-7,-1,-1,8)');
CALL expect_failure('UPDATE final_results SET published_at=CURRENT_TIMESTAMP(6) WHERE registration_id=-1');
CALL expect_failure('INSERT INTO user_roles(user_id,role_code) VALUES (-1,''UNKNOWN'')');
CALL expect_failure('INSERT INTO notification_events(event_key,category,event_type,title,body,announcement_id,target_type,target_id) VALUES (''ANNOUNCEMENT:900000001:PUBLISHED'',''ANNOUNCEMENT'',''ANNOUNCEMENT_PUBLISHED'',''Duplicate'',''Duplicate'',900000001,''ANNOUNCEMENT'',900000001)');
CALL expect_failure('INSERT INTO user_notifications(event_id,recipient_id) VALUES (900000001,-3)');

ROLLBACK;
DROP PROCEDURE expect_failure;

SELECT 'PASS: MySQL full-system fixture and constraint rejection checks' AS result;
