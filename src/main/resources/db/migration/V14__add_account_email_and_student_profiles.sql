-- Add contact data shared by all accounts and role-specific student identity data.
-- Existing student accounts remain valid without a profile; every newly provisioned
-- student is required by the application service to receive one atomically.

SET NAMES utf8mb4 COLLATE utf8mb4_0900_ai_ci;
SET time_zone = '+00:00';

ALTER TABLE user_accounts
    ADD COLUMN email VARCHAR(254) NULL AFTER full_name,
    ADD UNIQUE KEY uq_user_accounts_email (email),
    ADD CONSTRAINT chk_user_accounts_email
        CHECK (email IS NULL OR TRIM(email) <> '');

CREATE TABLE student_profiles (
    user_id BIGINT NOT NULL,
    student_code VARCHAR(32) NOT NULL,
    class_name VARCHAR(64) NOT NULL,
    cohort_code VARCHAR(16) NOT NULL,
    PRIMARY KEY (user_id),
    UNIQUE KEY uq_student_profiles_student_code (student_code),
    CONSTRAINT chk_student_profiles_student_code CHECK (TRIM(student_code) <> ''),
    CONSTRAINT chk_student_profiles_class_name CHECK (TRIM(class_name) <> ''),
    CONSTRAINT chk_student_profiles_cohort_code
        CHECK (cohort_code REGEXP '^K[0-9]{4}$'),
    CONSTRAINT fk_student_profiles_user
        FOREIGN KEY (user_id) REFERENCES user_accounts(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Hồ sơ nhận diện chỉ dành cho tài khoản sinh viên';
