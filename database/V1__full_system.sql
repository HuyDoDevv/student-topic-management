-- MySQL 8.4 LTS: full-system relational schema, REQ-01..32.
-- Select an empty database in DBeaver before running this script.
-- Versioned SQL applied manually; it contains no DROP and creates no demo accounts.
-- InnoDB supplies transactions and foreign keys; utf8mb4 stores Vietnamese text safely.

SET NAMES utf8mb4 COLLATE utf8mb4_0900_ai_ci;
SET time_zone = '+00:00';

CREATE TABLE faculties (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(255) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT chk_faculties_name CHECK (TRIM(name) <> '')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Khoa quản lý; quan hệ tổ chức không tự cấp quyền';

CREATE TABLE roles (
    code VARCHAR(32) NOT NULL,
    name VARCHAR(100) NOT NULL,
    PRIMARY KEY (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Danh mục vai trò tài khoản';

INSERT INTO roles(code, name) VALUES
    ('STUDENT', 'Sinh viên'),
    ('LECTURER', 'Giảng viên'),
    ('DEAN', 'Trưởng khoa');

CREATE TABLE departments (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(255) NOT NULL,
    faculty_id BIGINT NOT NULL,
    PRIMARY KEY (id),
    KEY idx_departments_faculty (faculty_id),
    CONSTRAINT chk_departments_name CHECK (TRIM(name) <> ''),
    CONSTRAINT fk_departments_faculty FOREIGN KEY (faculty_id) REFERENCES faculties(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Bộ môn thuộc khoa';

CREATE TABLE user_accounts (
    id BIGINT NOT NULL AUTO_INCREMENT,
    full_name VARCHAR(255),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    username VARCHAR(191) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    department_id BIGINT,
    PRIMARY KEY (id),
    UNIQUE KEY uq_user_accounts_username (username),
    KEY idx_accounts_department (department_id),
    CONSTRAINT chk_accounts_username CHECK (TRIM(username) <> ''),
    CONSTRAINT chk_accounts_password_hash CHECK (TRIM(password_hash) <> ''),
    CONSTRAINT fk_accounts_department FOREIGN KEY (department_id) REFERENCES departments(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Tài khoản ứng dụng, mật khẩu đã hash';

CREATE TABLE user_roles (
    user_id BIGINT NOT NULL,
    role_code VARCHAR(32) NOT NULL,
    PRIMARY KEY (user_id, role_code),
    KEY idx_user_roles_role (role_code),
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES user_accounts(id),
    CONSTRAINT fk_user_roles_role FOREIGN KEY (role_code) REFERENCES roles(code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Vai trò được cấp cho tài khoản';

CREATE TABLE registration_periods (
    id BIGINT NOT NULL AUTO_INCREMENT,
    faculty_id BIGINT NOT NULL,
    name VARCHAR(255) NOT NULL,
    topic_type VARCHAR(16) NOT NULL,
    lecturer_start TIMESTAMP(6) NOT NULL,
    lecturer_end TIMESTAMP(6) NOT NULL,
    student_start TIMESTAMP(6) NOT NULL,
    student_end TIMESTAMP(6) NOT NULL,
    reviewer_score_deadline DATE,
    council_report_date DATE,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_periods_faculty (faculty_id),
    CONSTRAINT chk_periods_name CHECK (TRIM(name) <> ''),
    CONSTRAINT chk_periods_topic_type CHECK (topic_type IN ('COURSE','NCKH','TLCN','KLTN')),
    CONSTRAINT chk_periods_version CHECK (version >= 0),
    CONSTRAINT chk_periods_windows CHECK (
        lecturer_start < lecturer_end
        AND lecturer_end <= student_start
        AND student_start < student_end
    ),
    CONSTRAINT chk_periods_reviewer_date CHECK (
        topic_type IN ('TLCN','KLTN') OR reviewer_score_deadline IS NULL
    ),
    CONSTRAINT chk_periods_council_date CHECK (
        topic_type = 'KLTN' OR council_report_date IS NULL
    ),
    CONSTRAINT fk_periods_faculty FOREIGN KEY (faculty_id) REFERENCES faculties(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Đợt đăng ký và các mốc thời gian';

CREATE TABLE topics (
    id BIGINT NOT NULL AUTO_INCREMENT,
    period_id BIGINT NOT NULL,
    department_id BIGINT NOT NULL,
    author_id BIGINT NOT NULL,
    title VARCHAR(500),
    description TEXT,
    objectives TEXT,
    student_requirements TEXT,
    expected_results TEXT,
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uq_topics_id_period (id, period_id),
    KEY idx_topics_period_status (period_id, status),
    KEY idx_topics_department (department_id),
    KEY idx_topics_author (author_id),
    CONSTRAINT chk_topics_status CHECK (status IN ('DRAFT','PENDING_APPROVAL','APPROVED','PUBLISHED')),
    CONSTRAINT chk_topics_version CHECK (version >= 0),
    CONSTRAINT chk_topics_submitted_content CHECK (
        status = 'DRAFT' OR (
            COALESCE(TRIM(title), '') <> ''
            AND COALESCE(TRIM(description), '') <> ''
            AND COALESCE(TRIM(objectives), '') <> ''
            AND COALESCE(TRIM(student_requirements), '') <> ''
            AND COALESCE(TRIM(expected_results), '') <> ''
        )
    ),
    CONSTRAINT fk_topics_period FOREIGN KEY (period_id) REFERENCES registration_periods(id),
    CONSTRAINT fk_topics_department FOREIGN KEY (department_id) REFERENCES departments(id),
    CONSTRAINT fk_topics_author FOREIGN KEY (author_id) REFERENCES user_accounts(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Đề tài thuộc một đợt đăng ký';

CREATE TABLE topic_supervisors (
    topic_id BIGINT NOT NULL,
    lecturer_id BIGINT NOT NULL,
    slot SMALLINT NOT NULL,
    PRIMARY KEY (topic_id, lecturer_id),
    UNIQUE KEY uq_topic_supervisors_slot (topic_id, slot),
    KEY idx_supervisors_lecturer (lecturer_id),
    CONSTRAINT chk_topic_supervisors_slot CHECK (slot BETWEEN 1 AND 2),
    CONSTRAINT fk_supervisors_topic FOREIGN KEY (topic_id) REFERENCES topics(id),
    CONSTRAINT fk_supervisors_lecturer FOREIGN KEY (lecturer_id) REFERENCES user_accounts(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Giảng viên hướng dẫn, tối đa hai người';

CREATE TABLE student_groups (
    id BIGINT NOT NULL AUTO_INCREMENT,
    period_id BIGINT NOT NULL,
    leader_student_id BIGINT NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uq_student_groups_id_period (id, period_id),
    KEY idx_groups_period (period_id),
    KEY idx_groups_leader (leader_student_id),
    CONSTRAINT chk_student_groups_version CHECK (version >= 0),
    CONSTRAINT fk_groups_period FOREIGN KEY (period_id) REFERENCES registration_periods(id),
    CONSTRAINT fk_groups_leader_account FOREIGN KEY (leader_student_id) REFERENCES user_accounts(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Nhóm sinh viên; trigger tự tạo membership cho trưởng nhóm';

CREATE TABLE group_memberships (
    group_id BIGINT NOT NULL,
    period_id BIGINT NOT NULL,
    student_id BIGINT NOT NULL,
    slot SMALLINT NOT NULL,
    PRIMARY KEY (group_id, student_id),
    UNIQUE KEY uq_memberships_period_student (period_id, student_id),
    UNIQUE KEY uq_memberships_group_slot (group_id, slot),
    KEY idx_memberships_student (student_id),
    CONSTRAINT chk_group_memberships_slot CHECK (slot BETWEEN 1 AND 3),
    CONSTRAINT fk_memberships_group_period FOREIGN KEY (group_id, period_id)
        REFERENCES student_groups(id, period_id),
    CONSTRAINT fk_memberships_student FOREIGN KEY (student_id) REFERENCES user_accounts(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Thành viên nhóm, tối đa ba và một nhóm trên sinh viên mỗi đợt';

-- MySQL has no deferred FK. Creating a group automatically creates slot 1 for its leader.
DELIMITER $$
CREATE TRIGGER trg_student_groups_add_leader
AFTER INSERT ON student_groups
FOR EACH ROW
BEGIN
    INSERT INTO group_memberships(group_id, period_id, student_id, slot)
    VALUES (NEW.id, NEW.period_id, NEW.leader_student_id, 1);
END$$

CREATE TRIGGER trg_student_groups_validate_leader
BEFORE UPDATE ON student_groups
FOR EACH ROW
BEGIN
    IF NEW.leader_student_id <> OLD.leader_student_id
       AND NOT EXISTS (
           SELECT 1 FROM group_memberships gm
           WHERE gm.group_id = OLD.id AND gm.student_id = NEW.leader_student_id
       ) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Group leader must already be a member';
    END IF;
END$$

CREATE TRIGGER trg_group_memberships_keep_leader
BEFORE DELETE ON group_memberships
FOR EACH ROW
BEGIN
    IF EXISTS (
        SELECT 1 FROM student_groups sg
        WHERE sg.id = OLD.group_id AND sg.leader_student_id = OLD.student_id
    ) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Transfer leadership before removing the leader';
    END IF;
END$$
DELIMITER ;

CREATE TABLE registrations (
    id BIGINT NOT NULL AUTO_INCREMENT,
    period_id BIGINT NOT NULL,
    group_id BIGINT NOT NULL,
    topic_id BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    submitted_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    decided_at TIMESTAMP(6) NULL,
    withdrawn_at TIMESTAMP(6) NULL,
    decision_actor_id BIGINT,
    rejection_kind VARCHAR(32),
    rejection_reason TEXT,
    version BIGINT NOT NULL DEFAULT 0,
    active_group_guard BIGINT GENERATED ALWAYS AS (
        CASE WHEN status IN ('PENDING','ACCEPTED') THEN group_id ELSE NULL END
    ) STORED,
    accepted_topic_guard BIGINT GENERATED ALWAYS AS (
        CASE WHEN status = 'ACCEPTED' THEN topic_id ELSE NULL END
    ) STORED,
    PRIMARY KEY (id),
    UNIQUE KEY uq_registrations_id_period (id, period_id),
    UNIQUE KEY uq_registrations_id_status (id, status),
    UNIQUE KEY uq_one_active_registration_per_group (period_id, active_group_guard),
    UNIQUE KEY uq_one_accepted_group_per_topic (period_id, accepted_topic_guard),
    KEY idx_registrations_group_period (group_id, period_id),
    KEY idx_registrations_topic_period (topic_id, period_id),
    KEY idx_registrations_actor (decision_actor_id),
    CONSTRAINT chk_registrations_status CHECK (status IN ('PENDING','ACCEPTED','REJECTED','WITHDRAWN')),
    CONSTRAINT chk_registrations_rejection_kind CHECK (
        rejection_kind IS NULL OR rejection_kind IN ('MANUAL','TOPIC_TAKEN')
    ),
    CONSTRAINT chk_registrations_version CHECK (version >= 0),
    CONSTRAINT chk_registrations_auto_reason CHECK (
        rejection_kind IS NULL OR rejection_kind <> 'TOPIC_TAKEN' OR (
            status = 'REJECTED'
            AND rejection_reason IS NOT NULL
            AND rejection_reason = 'Đề tài đã nhận nhóm'
        )
    ),
    CONSTRAINT fk_registrations_period FOREIGN KEY (period_id) REFERENCES registration_periods(id),
    CONSTRAINT fk_registrations_group_period FOREIGN KEY (group_id, period_id)
        REFERENCES student_groups(id, period_id),
    CONSTRAINT fk_registrations_topic_period FOREIGN KEY (topic_id, period_id)
        REFERENCES topics(id, period_id),
    CONSTRAINT fk_registrations_actor FOREIGN KEY (decision_actor_id) REFERENCES user_accounts(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Đăng ký đề tài và kết quả xét duyệt';

CREATE TABLE reviewer_assignments (
    registration_id BIGINT NOT NULL,
    registration_status VARCHAR(32) NOT NULL DEFAULT 'ACCEPTED',
    lecturer_id BIGINT NOT NULL,
    PRIMARY KEY (registration_id, lecturer_id),
    KEY idx_reviewers_lecturer (lecturer_id),
    CONSTRAINT chk_reviewer_assignment_status CHECK (registration_status = 'ACCEPTED'),
    CONSTRAINT fk_reviewers_registration_status FOREIGN KEY (registration_id, registration_status)
        REFERENCES registrations(id, status),
    CONSTRAINT fk_reviewers_lecturer FOREIGN KEY (lecturer_id) REFERENCES user_accounts(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Phân công giảng viên phản biện';

CREATE TABLE project_reports (
    id BIGINT NOT NULL AUTO_INCREMENT,
    registration_id BIGINT NOT NULL,
    registration_status VARCHAR(32) NOT NULL DEFAULT 'ACCEPTED',
    submitted_by BIGINT NOT NULL,
    submitted_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    file_name VARCHAR(500) NOT NULL,
    storage_key VARCHAR(1000) NOT NULL,
    content_type VARCHAR(255),
    size_bytes BIGINT,
    PRIMARY KEY (id),
    KEY idx_reports_registration (registration_id),
    KEY idx_reports_submitter (submitted_by),
    CONSTRAINT chk_project_reports_status CHECK (registration_status = 'ACCEPTED'),
    CONSTRAINT chk_project_reports_file_name CHECK (TRIM(file_name) <> ''),
    CONSTRAINT chk_project_reports_storage_key CHECK (TRIM(storage_key) <> ''),
    CONSTRAINT chk_project_reports_size CHECK (size_bytes IS NULL OR size_bytes >= 0),
    CONSTRAINT fk_reports_registration_status FOREIGN KEY (registration_id, registration_status)
        REFERENCES registrations(id, status),
    CONSTRAINT fk_reports_submitter FOREIGN KEY (submitted_by) REFERENCES user_accounts(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Metadata báo cáo do nhóm nộp';

CREATE TABLE councils (
    id BIGINT NOT NULL AUTO_INCREMENT,
    period_id BIGINT NOT NULL,
    name VARCHAR(255) NOT NULL,
    chair_id BIGINT NOT NULL,
    secretary_id BIGINT NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_councils_id_period (id, period_id),
    KEY idx_councils_period (period_id),
    KEY idx_councils_chair (chair_id),
    KEY idx_councils_secretary (secretary_id),
    CONSTRAINT chk_councils_name CHECK (TRIM(name) <> ''),
    CONSTRAINT chk_councils_distinct_officers CHECK (chair_id <> secretary_id),
    CONSTRAINT fk_councils_period FOREIGN KEY (period_id) REFERENCES registration_periods(id),
    CONSTRAINT fk_councils_chair_account FOREIGN KEY (chair_id) REFERENCES user_accounts(id),
    CONSTRAINT fk_councils_secretary_account FOREIGN KEY (secretary_id) REFERENCES user_accounts(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Hội đồng; trigger tự tạo membership cho chủ tịch và thư ký';

CREATE TABLE council_members (
    council_id BIGINT NOT NULL,
    lecturer_id BIGINT NOT NULL,
    slot SMALLINT NOT NULL,
    PRIMARY KEY (council_id, lecturer_id),
    UNIQUE KEY uq_council_members_slot (council_id, slot),
    KEY idx_council_members_lecturer (lecturer_id),
    CONSTRAINT chk_council_members_slot CHECK (slot BETWEEN 1 AND 5),
    CONSTRAINT fk_council_members_council FOREIGN KEY (council_id) REFERENCES councils(id),
    CONSTRAINT fk_council_members_lecturer FOREIGN KEY (lecturer_id) REFERENCES user_accounts(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Thành viên hội đồng, tối đa năm';

DELIMITER $$
CREATE TRIGGER trg_councils_add_officers
AFTER INSERT ON councils
FOR EACH ROW
BEGIN
    INSERT INTO council_members(council_id, lecturer_id, slot)
    VALUES (NEW.id, NEW.chair_id, 1);
    INSERT INTO council_members(council_id, lecturer_id, slot)
    VALUES (NEW.id, NEW.secretary_id, 2);
END$$

CREATE TRIGGER trg_councils_validate_officers
BEFORE UPDATE ON councils
FOR EACH ROW
BEGIN
    IF NEW.chair_id <> OLD.chair_id
       AND NOT EXISTS (
           SELECT 1 FROM council_members cm
           WHERE cm.council_id = OLD.id AND cm.lecturer_id = NEW.chair_id
       ) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Council chair must already be a member';
    END IF;
    IF NEW.secretary_id <> OLD.secretary_id
       AND NOT EXISTS (
           SELECT 1 FROM council_members cm
           WHERE cm.council_id = OLD.id AND cm.lecturer_id = NEW.secretary_id
       ) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Council secretary must already be a member';
    END IF;
END$$

CREATE TRIGGER trg_council_members_keep_officers
BEFORE DELETE ON council_members
FOR EACH ROW
BEGIN
    IF EXISTS (
        SELECT 1 FROM councils c
        WHERE c.id = OLD.council_id
          AND (c.chair_id = OLD.lecturer_id OR c.secretary_id = OLD.lecturer_id)
    ) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Change council officers before removing this member';
    END IF;
END$$
DELIMITER ;

CREATE TABLE council_topics (
    council_id BIGINT NOT NULL,
    registration_id BIGINT NOT NULL,
    period_id BIGINT NOT NULL,
    registration_status VARCHAR(32) NOT NULL DEFAULT 'ACCEPTED',
    scheduled_at TIMESTAMP(6) NULL,
    location VARCHAR(500),
    PRIMARY KEY (council_id, registration_id),
    KEY idx_council_topics_registration (registration_id),
    KEY idx_council_topics_council_period (council_id, period_id),
    KEY idx_council_topics_registration_period (registration_id, period_id),
    CONSTRAINT chk_council_topics_status CHECK (registration_status = 'ACCEPTED'),
    CONSTRAINT fk_council_topics_council_period FOREIGN KEY (council_id, period_id)
        REFERENCES councils(id, period_id),
    CONSTRAINT fk_council_topics_registration_period FOREIGN KEY (registration_id, period_id)
        REFERENCES registrations(id, period_id),
    CONSTRAINT fk_council_topics_registration_status FOREIGN KEY (registration_id, registration_status)
        REFERENCES registrations(id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Phân công đề tài đã nhận nhóm cho hội đồng';

CREATE TABLE score_components (
    id BIGINT NOT NULL AUTO_INCREMENT,
    period_id BIGINT NOT NULL,
    name VARCHAR(255) NOT NULL,
    minimum_score DECIMAL(12,4),
    maximum_score DECIMAL(12,4),
    PRIMARY KEY (id),
    UNIQUE KEY uq_score_components_id_period (id, period_id),
    UNIQUE KEY uq_score_components_period_name (period_id, name),
    CONSTRAINT chk_score_components_name CHECK (TRIM(name) <> ''),
    CONSTRAINT chk_score_components_range CHECK (
        minimum_score IS NULL OR maximum_score IS NULL OR minimum_score < maximum_score
    ),
    CONSTRAINT fk_score_components_period FOREIGN KEY (period_id) REFERENCES registration_periods(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Định nghĩa điểm thành phần theo đợt';

CREATE TABLE evaluations (
    id BIGINT NOT NULL AUTO_INCREMENT,
    registration_id BIGINT NOT NULL,
    period_id BIGINT NOT NULL,
    registration_status VARCHAR(32) NOT NULL DEFAULT 'ACCEPTED',
    evaluator_id BIGINT NOT NULL,
    component_id BIGINT NOT NULL,
    council_id BIGINT,
    score DECIMAL(12,4) NOT NULL,
    feedback TEXT,
    evaluated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_evaluations_actor_component (registration_id, evaluator_id, component_id),
    KEY idx_evaluations_evaluator (evaluator_id),
    KEY idx_evaluations_component_period (component_id, period_id),
    KEY idx_evaluations_council_registration (council_id, registration_id),
    KEY idx_evaluations_council_evaluator (council_id, evaluator_id),
    CONSTRAINT chk_evaluations_status CHECK (registration_status = 'ACCEPTED'),
    CONSTRAINT fk_evaluations_registration_period FOREIGN KEY (registration_id, period_id)
        REFERENCES registrations(id, period_id),
    CONSTRAINT fk_evaluations_registration_status FOREIGN KEY (registration_id, registration_status)
        REFERENCES registrations(id, status),
    CONSTRAINT fk_evaluations_evaluator FOREIGN KEY (evaluator_id) REFERENCES user_accounts(id),
    CONSTRAINT fk_evaluations_component_period FOREIGN KEY (component_id, period_id)
        REFERENCES score_components(id, period_id),
    CONSTRAINT fk_evaluations_council_topic FOREIGN KEY (council_id, registration_id)
        REFERENCES council_topics(council_id, registration_id),
    CONSTRAINT fk_evaluations_council_member FOREIGN KEY (council_id, evaluator_id)
        REFERENCES council_members(council_id, lecturer_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Điểm và nhận xét của giảng viên theo thành phần';

CREATE TABLE final_results (
    registration_id BIGINT NOT NULL,
    registration_status VARCHAR(32) NOT NULL DEFAULT 'ACCEPTED',
    council_id BIGINT,
    consolidated_by BIGINT,
    consolidated_at TIMESTAMP(6) NULL,
    evaluation_summary TEXT,
    final_score DECIMAL(12,4),
    review_completed_at TIMESTAMP(6) NULL,
    grading_completed_at TIMESTAMP(6) NULL,
    published_by BIGINT,
    published_at TIMESTAMP(6) NULL,
    PRIMARY KEY (registration_id),
    KEY idx_final_results_council_registration (council_id, registration_id),
    KEY idx_final_results_consolidator (consolidated_by),
    KEY idx_final_results_publisher (published_by),
    CONSTRAINT chk_final_results_status CHECK (registration_status = 'ACCEPTED'),
    CONSTRAINT chk_final_results_publication CHECK (
        published_at IS NULL OR (
            published_by IS NOT NULL
            AND final_score IS NOT NULL
            AND review_completed_at IS NOT NULL
            AND grading_completed_at IS NOT NULL
            AND published_at >= review_completed_at
            AND published_at >= grading_completed_at
        )
    ),
    CONSTRAINT fk_final_results_registration_status FOREIGN KEY (registration_id, registration_status)
        REFERENCES registrations(id, status),
    CONSTRAINT fk_final_results_council_topic FOREIGN KEY (council_id, registration_id)
        REFERENCES council_topics(council_id, registration_id),
    CONSTRAINT fk_final_results_consolidator FOREIGN KEY (consolidated_by) REFERENCES user_accounts(id),
    CONSTRAINT fk_final_results_publisher FOREIGN KEY (published_by) REFERENCES user_accounts(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Kết quả tổng hợp và công bố';

CREATE TABLE announcements (
    id BIGINT NOT NULL AUTO_INCREMENT,
    title VARCHAR(500) NOT NULL,
    body TEXT NOT NULL,
    source_name VARCHAR(255) NOT NULL,
    faculty_id BIGINT,
    published_by BIGINT,
    published_at TIMESTAMP(6) NULL,
    PRIMARY KEY (id),
    KEY idx_announcements_faculty (faculty_id),
    KEY idx_announcements_published (published_at),
    KEY idx_announcements_publisher (published_by),
    CONSTRAINT chk_announcements_title CHECK (TRIM(title) <> ''),
    CONSTRAINT chk_announcements_body CHECK (TRIM(body) <> ''),
    CONSTRAINT chk_announcements_source CHECK (TRIM(source_name) <> ''),
    CONSTRAINT chk_announcements_publication CHECK (published_at IS NULL OR published_by IS NOT NULL),
    CONSTRAINT fk_announcements_faculty FOREIGN KEY (faculty_id) REFERENCES faculties(id),
    CONSTRAINT fk_announcements_publisher FOREIGN KEY (published_by) REFERENCES user_accounts(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Thông báo chính thức của trường hoặc khoa';
