-- Local UI reset only. The caller must validate the schema, retained rows,
-- target database, backup, PDF archive, and explicit confirmation first.
-- The caller temporarily removes and then restores the four guarded triggers
-- on group_memberships, council_members, and registration_members. Do not run
-- this file directly. DELETE keeps AUTO_INCREMENT and Flyway history unchanged.
START TRANSACTION;

DELETE FROM user_notifications;
DELETE FROM notification_events;
DELETE FROM reviewer_evaluations;
DELETE FROM reviewer_reports;
DELETE FROM evaluations;
DELETE FROM final_results;
DELETE FROM project_reports;
DELETE FROM group_invitations;
DELETE FROM registration_members;
DELETE FROM group_memberships;
DELETE FROM topic_supervisors;
DELETE FROM council_topics;
DELETE FROM council_members;
DELETE FROM reviewer_assignments;
DELETE FROM score_components;
DELETE FROM councils;
DELETE FROM registrations;
DELETE FROM student_groups;
DELETE FROM topics;
DELETE FROM registration_periods;
DELETE FROM announcements;
DELETE FROM student_profiles;
DELETE FROM user_roles WHERE user_id NOT IN (
    SELECT id FROM user_accounts WHERE username = 'admin'
);
DELETE FROM user_accounts WHERE username <> 'admin';
DELETE FROM departments;
DELETE FROM faculties WHERE BINARY name <> BINARY 'Khoa Công nghệ Thông tin';

COMMIT;
