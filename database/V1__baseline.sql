-- Clean-slate local baseline for MySQL 8.4.
-- Contains the final V26-equivalent schema plus one local ADMIN login.
-- Username: admin. The password is stored only as a BCrypt hash.


/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!40101 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `announcements` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `title` varchar(500) NOT NULL,
  `body` text NOT NULL,
  `source_name` varchar(255) NOT NULL,
  `faculty_id` bigint DEFAULT NULL,
  `created_by` bigint DEFAULT NULL,
  `published_by` bigint DEFAULT NULL,
  `published_at` timestamp(6) NULL DEFAULT NULL,
  `created_at` timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `updated_at` timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  `version` bigint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_announcements_faculty` (`faculty_id`),
  KEY `idx_announcements_published` (`published_at`),
  KEY `idx_announcements_publisher` (`published_by`),
  KEY `idx_announcements_creator` (`created_by`),
  KEY `idx_announcements_scope_state` (`faculty_id`,`published_at`,`updated_at`),
  CONSTRAINT `fk_announcements_creator` FOREIGN KEY (`created_by`) REFERENCES `user_accounts` (`id`),
  CONSTRAINT `fk_announcements_faculty` FOREIGN KEY (`faculty_id`) REFERENCES `faculties` (`id`),
  CONSTRAINT `fk_announcements_publisher` FOREIGN KEY (`published_by`) REFERENCES `user_accounts` (`id`),
  CONSTRAINT `chk_announcements_body` CHECK ((trim(`body`) <> _utf8mb4'')),
  CONSTRAINT `chk_announcements_publication` CHECK (((`published_at` is null) or (`published_by` is not null))),
  CONSTRAINT `chk_announcements_source` CHECK ((trim(`source_name`) <> _utf8mb4'')),
  CONSTRAINT `chk_announcements_title` CHECK ((trim(`title`) <> _utf8mb4'')),
  CONSTRAINT `chk_announcements_version` CHECK ((`version` >= 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Thông báo chính thức của trường hoặc khoa';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `council_members` (
  `council_id` bigint NOT NULL,
  `lecturer_id` bigint NOT NULL,
  `slot` smallint NOT NULL,
  `created_at` timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `updated_at` timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`council_id`,`lecturer_id`),
  UNIQUE KEY `uq_council_members_slot` (`council_id`,`slot`),
  KEY `idx_council_members_lecturer` (`lecturer_id`),
  CONSTRAINT `fk_council_members_council` FOREIGN KEY (`council_id`) REFERENCES `councils` (`id`),
  CONSTRAINT `fk_council_members_lecturer` FOREIGN KEY (`lecturer_id`) REFERENCES `user_accounts` (`id`),
  CONSTRAINT `chk_council_members_slot` CHECK ((`slot` between 1 and 5))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Thành viên hội đồng, tối đa năm';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!50003 SET @saved_cs_client      = @@character_set_client */ ;
/*!50003 SET @saved_cs_results     = @@character_set_results */ ;
/*!50003 SET @saved_col_connection = @@collation_connection */ ;
/*!50003 SET character_set_client  = utf8mb4 */ ;
/*!50003 SET character_set_results = utf8mb4 */ ;
/*!50003 SET collation_connection  = utf8mb4_0900_ai_ci */ ;
/*!50003 SET @saved_sql_mode       = @@sql_mode */ ;
/*!50003 SET sql_mode              = 'ONLY_FULL_GROUP_BY,STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION' */ ;
DELIMITER ;;
/*!50003 CREATE*/ /*!50003 TRIGGER `trg_council_members_keep_officers` BEFORE DELETE ON `council_members` FOR EACH ROW BEGIN
    IF EXISTS (
        SELECT 1 FROM councils c
        WHERE c.id = OLD.council_id
          AND (c.chair_id = OLD.lecturer_id OR c.secretary_id = OLD.lecturer_id)
    ) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Change council officers before removing this member';
    END IF;
END */;;
DELIMITER ;
/*!50003 SET sql_mode              = @saved_sql_mode */ ;
/*!50003 SET character_set_client  = @saved_cs_client */ ;
/*!50003 SET character_set_results = @saved_cs_results */ ;
/*!50003 SET collation_connection  = @saved_col_connection */ ;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `council_topics` (
  `council_id` bigint NOT NULL,
  `registration_id` bigint NOT NULL,
  `period_id` bigint NOT NULL,
  `registration_status` varchar(32) NOT NULL DEFAULT 'ACCEPTED',
  `scheduled_at` timestamp(6) NULL DEFAULT NULL,
  `location` varchar(500) DEFAULT NULL,
  `created_at` timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `updated_at` timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`council_id`,`registration_id`),
  UNIQUE KEY `uq_council_topics_registration` (`registration_id`),
  KEY `idx_council_topics_registration` (`registration_id`),
  KEY `idx_council_topics_council_period` (`council_id`,`period_id`),
  KEY `idx_council_topics_registration_period` (`registration_id`,`period_id`),
  KEY `fk_council_topics_registration_status` (`registration_id`,`registration_status`),
  CONSTRAINT `fk_council_topics_council_period` FOREIGN KEY (`council_id`, `period_id`) REFERENCES `councils` (`id`, `period_id`),
  CONSTRAINT `fk_council_topics_registration_period` FOREIGN KEY (`registration_id`, `period_id`) REFERENCES `registrations` (`id`, `period_id`),
  CONSTRAINT `fk_council_topics_registration_status` FOREIGN KEY (`registration_id`, `registration_status`) REFERENCES `registrations` (`id`, `status`),
  CONSTRAINT `chk_council_topics_schedule` CHECK ((((`scheduled_at` is null) and (`location` is null)) or ((`scheduled_at` is not null) and (coalesce(trim(`location`),_utf8mb4'') <> _utf8mb4'')))),
  CONSTRAINT `chk_council_topics_status` CHECK ((`registration_status` = _utf8mb4'ACCEPTED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Phân công đề tài đã nhận nhóm cho hội đồng';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `councils` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `period_id` bigint NOT NULL,
  `name` varchar(255) NOT NULL,
  `chair_id` bigint NOT NULL,
  `secretary_id` bigint NOT NULL,
  `grading_mode` varchar(16) NOT NULL DEFAULT 'COUNCIL',
  `status` varchar(32) NOT NULL DEFAULT 'DRAFT',
  `created_at` timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `updated_at` timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  `version` bigint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_councils_id_period` (`id`,`period_id`),
  KEY `idx_councils_period` (`period_id`),
  KEY `idx_councils_chair` (`chair_id`),
  KEY `idx_councils_secretary` (`secretary_id`),
  CONSTRAINT `fk_councils_chair_account` FOREIGN KEY (`chair_id`) REFERENCES `user_accounts` (`id`),
  CONSTRAINT `fk_councils_period` FOREIGN KEY (`period_id`) REFERENCES `registration_periods` (`id`),
  CONSTRAINT `fk_councils_secretary_account` FOREIGN KEY (`secretary_id`) REFERENCES `user_accounts` (`id`),
  CONSTRAINT `chk_councils_grading_mode` CHECK ((`grading_mode` in (_utf8mb4'COUNCIL',_utf8mb4'DIRECT'))),
  CONSTRAINT `chk_councils_grading_mode_officers` CHECK ((((`grading_mode` = _utf8mb4'COUNCIL') and (`chair_id` <> `secretary_id`)) or ((`grading_mode` = _utf8mb4'DIRECT') and (`chair_id` = `secretary_id`)))),
  CONSTRAINT `chk_councils_name` CHECK ((trim(`name`) <> _utf8mb4'')),
  CONSTRAINT `chk_councils_status` CHECK ((`status` in (_utf8mb4'DRAFT',_utf8mb4'ASSIGNED',_utf8mb4'SCHEDULED',_utf8mb4'ACTIVE',_utf8mb4'COMPLETED',_utf8mb4'PUBLISHED'))),
  CONSTRAINT `chk_councils_version` CHECK ((`version` >= 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Hội đồng; trigger tự tạo membership cho chủ tịch và thư ký';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!50003 SET @saved_cs_client      = @@character_set_client */ ;
/*!50003 SET @saved_cs_results     = @@character_set_results */ ;
/*!50003 SET @saved_col_connection = @@collation_connection */ ;
/*!50003 SET character_set_client  = utf8mb4 */ ;
/*!50003 SET character_set_results = utf8mb4 */ ;
/*!50003 SET collation_connection  = utf8mb4_0900_ai_ci */ ;
/*!50003 SET @saved_sql_mode       = @@sql_mode */ ;
/*!50003 SET sql_mode              = 'ONLY_FULL_GROUP_BY,STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION' */ ;
DELIMITER ;;
/*!50003 CREATE*/ /*!50003 TRIGGER `trg_councils_add_officers` AFTER INSERT ON `councils` FOR EACH ROW BEGIN
    INSERT INTO council_members(council_id, lecturer_id, slot)
    VALUES (NEW.id, NEW.chair_id, 1);

    IF NEW.secretary_id <> NEW.chair_id THEN
        INSERT INTO council_members(council_id, lecturer_id, slot)
        VALUES (NEW.id, NEW.secretary_id, 2);
    END IF;
END */;;
DELIMITER ;
/*!50003 SET sql_mode              = @saved_sql_mode */ ;
/*!50003 SET character_set_client  = @saved_cs_client */ ;
/*!50003 SET character_set_results = @saved_cs_results */ ;
/*!50003 SET collation_connection  = @saved_col_connection */ ;
/*!50003 SET @saved_cs_client      = @@character_set_client */ ;
/*!50003 SET @saved_cs_results     = @@character_set_results */ ;
/*!50003 SET @saved_col_connection = @@collation_connection */ ;
/*!50003 SET character_set_client  = utf8mb4 */ ;
/*!50003 SET character_set_results = utf8mb4 */ ;
/*!50003 SET collation_connection  = utf8mb4_0900_ai_ci */ ;
/*!50003 SET @saved_sql_mode       = @@sql_mode */ ;
/*!50003 SET sql_mode              = 'ONLY_FULL_GROUP_BY,STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION' */ ;
DELIMITER ;;
/*!50003 CREATE*/ /*!50003 TRIGGER `trg_councils_validate_officers` BEFORE UPDATE ON `councils` FOR EACH ROW BEGIN
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
END */;;
DELIMITER ;
/*!50003 SET sql_mode              = @saved_sql_mode */ ;
/*!50003 SET character_set_client  = @saved_cs_client */ ;
/*!50003 SET character_set_results = @saved_cs_results */ ;
/*!50003 SET collation_connection  = @saved_col_connection */ ;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `departments` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(255) NOT NULL,
  `faculty_id` bigint NOT NULL,
  `created_at` timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `updated_at` timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_departments_faculty_name` (`faculty_id`,`name`),
  UNIQUE KEY `uq_departments_id_faculty` (`id`,`faculty_id`),
  KEY `idx_departments_faculty` (`faculty_id`),
  CONSTRAINT `fk_departments_faculty` FOREIGN KEY (`faculty_id`) REFERENCES `faculties` (`id`),
  CONSTRAINT `chk_departments_name` CHECK ((trim(`name`) <> _utf8mb4''))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Bộ môn thuộc khoa';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `evaluations` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `registration_id` bigint NOT NULL,
  `student_id` bigint NOT NULL,
  `period_id` bigint NOT NULL,
  `registration_status` varchar(32) NOT NULL DEFAULT 'ACCEPTED',
  `evaluator_id` bigint NOT NULL,
  `component_id` bigint NOT NULL,
  `council_id` bigint NOT NULL,
  `score` decimal(12,4) NOT NULL,
  `feedback` text,
  `status` varchar(32) NOT NULL DEFAULT 'DRAFT',
  `evaluated_at` timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `submitted_at` timestamp(6) NULL DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `created_at` timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `updated_at` timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_evaluations_student_actor_component` (`registration_id`,`student_id`,`evaluator_id`,`component_id`),
  KEY `idx_evaluations_evaluator` (`evaluator_id`),
  KEY `idx_evaluations_component_period` (`component_id`,`period_id`),
  KEY `idx_evaluations_council_registration` (`council_id`,`registration_id`),
  KEY `idx_evaluations_council_evaluator` (`council_id`,`evaluator_id`),
  KEY `fk_evaluations_registration_period` (`registration_id`,`period_id`),
  KEY `fk_evaluations_registration_status` (`registration_id`,`registration_status`),
  KEY `idx_evaluations_student` (`student_id`),
  CONSTRAINT `fk_evaluations_component_period` FOREIGN KEY (`component_id`, `period_id`) REFERENCES `score_components` (`id`, `period_id`),
  CONSTRAINT `fk_evaluations_council_member` FOREIGN KEY (`council_id`, `evaluator_id`) REFERENCES `council_members` (`council_id`, `lecturer_id`),
  CONSTRAINT `fk_evaluations_council_topic` FOREIGN KEY (`council_id`, `registration_id`) REFERENCES `council_topics` (`council_id`, `registration_id`),
  CONSTRAINT `fk_evaluations_evaluator` FOREIGN KEY (`evaluator_id`) REFERENCES `user_accounts` (`id`),
  CONSTRAINT `fk_evaluations_registration_period` FOREIGN KEY (`registration_id`, `period_id`) REFERENCES `registrations` (`id`, `period_id`),
  CONSTRAINT `fk_evaluations_registration_status` FOREIGN KEY (`registration_id`, `registration_status`) REFERENCES `registrations` (`id`, `status`),
  CONSTRAINT `fk_evaluations_registration_student` FOREIGN KEY (`registration_id`, `student_id`) REFERENCES `registration_members` (`registration_id`, `student_id`),
  CONSTRAINT `chk_evaluations_lifecycle` CHECK ((`status` in (_utf8mb4'DRAFT',_utf8mb4'SUBMITTED'))),
  CONSTRAINT `chk_evaluations_score` CHECK (((`score` between 0.00 and 10.00) and (`score` = round(`score`,2)))),
  CONSTRAINT `chk_evaluations_status` CHECK ((`registration_status` = _utf8mb4'ACCEPTED')),
  CONSTRAINT `chk_evaluations_submission` CHECK ((((`status` = _utf8mb4'DRAFT') and (`submitted_at` is null)) or ((`status` = _utf8mb4'SUBMITTED') and (`submitted_at` is not null)))),
  CONSTRAINT `chk_evaluations_version` CHECK ((`version` >= 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Điểm và nhận xét của giảng viên theo thành phần';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `faculties` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(255) NOT NULL,
  `created_at` timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `updated_at` timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_faculties_name` (`name`),
  CONSTRAINT `chk_faculties_name` CHECK ((trim(`name`) <> _utf8mb4''))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Khoa quản lý; quan hệ tổ chức không tự cấp quyền';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `final_results` (
  `registration_id` bigint NOT NULL,
  `student_id` bigint NOT NULL,
  `registration_status` varchar(32) NOT NULL DEFAULT 'ACCEPTED',
  `status` varchar(32) NOT NULL DEFAULT 'READY',
  `council_id` bigint NOT NULL,
  `consolidated_by` bigint NOT NULL,
  `consolidated_at` timestamp(6) NOT NULL,
  `evaluation_summary` text NOT NULL,
  `final_score` decimal(12,4) NOT NULL,
  `review_completed_at` timestamp(6) NOT NULL,
  `grading_completed_at` timestamp(6) NOT NULL,
  `published_by` bigint DEFAULT NULL,
  `published_at` timestamp(6) NULL DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `created_at` timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `updated_at` timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`registration_id`,`student_id`),
  KEY `idx_final_results_council_registration` (`council_id`,`registration_id`),
  KEY `idx_final_results_consolidator` (`consolidated_by`),
  KEY `idx_final_results_publisher` (`published_by`),
  KEY `fk_final_results_registration_status` (`registration_id`,`registration_status`),
  KEY `idx_final_results_student` (`student_id`),
  CONSTRAINT `fk_final_results_consolidator` FOREIGN KEY (`consolidated_by`) REFERENCES `user_accounts` (`id`),
  CONSTRAINT `fk_final_results_council_topic` FOREIGN KEY (`council_id`, `registration_id`) REFERENCES `council_topics` (`council_id`, `registration_id`),
  CONSTRAINT `fk_final_results_publisher` FOREIGN KEY (`published_by`) REFERENCES `user_accounts` (`id`),
  CONSTRAINT `fk_final_results_registration_status` FOREIGN KEY (`registration_id`, `registration_status`) REFERENCES `registrations` (`id`, `status`),
  CONSTRAINT `fk_final_results_registration_student` FOREIGN KEY (`registration_id`, `student_id`) REFERENCES `registration_members` (`registration_id`, `student_id`),
  CONSTRAINT `chk_final_results_lifecycle` CHECK ((((`status` = _utf8mb4'READY') and (`published_by` is null) and (`published_at` is null)) or ((`status` = _utf8mb4'PUBLISHED') and (`published_by` is not null) and (`published_at` is not null) and (`published_at` >= `review_completed_at`) and (`published_at` >= `grading_completed_at`)))),
  CONSTRAINT `chk_final_results_publication` CHECK (((`published_at` is null) or ((`published_by` is not null) and (`final_score` is not null) and (`review_completed_at` is not null) and (`grading_completed_at` is not null) and (`published_at` >= `review_completed_at`) and (`published_at` >= `grading_completed_at`)))),
  CONSTRAINT `chk_final_results_score` CHECK (((`final_score` between 0.00 and 10.00) and (`final_score` = round(`final_score`,2)))),
  CONSTRAINT `chk_final_results_status` CHECK ((`registration_status` = _utf8mb4'ACCEPTED')),
  CONSTRAINT `chk_final_results_version` CHECK ((`version` >= 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Kết quả tổng hợp và công bố';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `group_invitations` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `group_id` bigint NOT NULL,
  `period_id` bigint NOT NULL,
  `student_id` bigint NOT NULL,
  `invited_by` bigint NOT NULL,
  `slot` smallint NOT NULL,
  `status` varchar(16) NOT NULL DEFAULT 'PENDING',
  `created_at` timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `updated_at` timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  `responded_at` timestamp(6) NULL DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `pending_student_guard` bigint GENERATED ALWAYS AS ((case when (`status` = _utf8mb4'PENDING') then `student_id` else NULL end)) STORED,
  `pending_slot_guard` smallint GENERATED ALWAYS AS ((case when (`status` = _utf8mb4'PENDING') then `slot` else NULL end)) STORED,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_group_invitations_pending_student` (`period_id`,`pending_student_guard`),
  UNIQUE KEY `uq_group_invitations_pending_slot` (`group_id`,`pending_slot_guard`),
  KEY `idx_group_invitations_group_status` (`group_id`,`status`),
  KEY `idx_group_invitations_student_status` (`student_id`,`status`),
  KEY `idx_group_invitations_invited_by` (`invited_by`),
  KEY `fk_group_invitations_group_period` (`group_id`,`period_id`),
  CONSTRAINT `fk_group_invitations_group_period` FOREIGN KEY (`group_id`, `period_id`) REFERENCES `student_groups` (`id`, `period_id`),
  CONSTRAINT `fk_group_invitations_invited_by` FOREIGN KEY (`invited_by`) REFERENCES `user_accounts` (`id`),
  CONSTRAINT `fk_group_invitations_student` FOREIGN KEY (`student_id`) REFERENCES `user_accounts` (`id`),
  CONSTRAINT `chk_group_invitations_response_time` CHECK ((((`status` = _utf8mb4'PENDING') and (`responded_at` is null)) or ((`status` <> _utf8mb4'PENDING') and (`responded_at` is not null)))),
  CONSTRAINT `chk_group_invitations_slot` CHECK ((`slot` between 1 and 3)),
  CONSTRAINT `chk_group_invitations_status` CHECK ((`status` in (_utf8mb4'PENDING',_utf8mb4'ACCEPTED',_utf8mb4'REJECTED',_utf8mb4'CANCELLED'))),
  CONSTRAINT `chk_group_invitations_version` CHECK ((`version` >= 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Lời mời vào nhóm sinh viên và lịch sử phản hồi';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `group_memberships` (
  `group_id` bigint NOT NULL,
  `period_id` bigint NOT NULL,
  `student_id` bigint NOT NULL,
  `slot` smallint NOT NULL,
  `created_at` timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `updated_at` timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`group_id`,`student_id`),
  UNIQUE KEY `uq_memberships_period_student` (`period_id`,`student_id`),
  UNIQUE KEY `uq_memberships_group_slot` (`group_id`,`slot`),
  KEY `idx_memberships_student` (`student_id`),
  KEY `fk_memberships_group_period` (`group_id`,`period_id`),
  CONSTRAINT `fk_memberships_group_period` FOREIGN KEY (`group_id`, `period_id`) REFERENCES `student_groups` (`id`, `period_id`),
  CONSTRAINT `fk_memberships_student` FOREIGN KEY (`student_id`) REFERENCES `user_accounts` (`id`),
  CONSTRAINT `chk_group_memberships_slot` CHECK ((`slot` between 1 and 3))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Thành viên nhóm, tối đa ba và một nhóm trên sinh viên mỗi đợt';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!50003 SET @saved_cs_client      = @@character_set_client */ ;
/*!50003 SET @saved_cs_results     = @@character_set_results */ ;
/*!50003 SET @saved_col_connection = @@collation_connection */ ;
/*!50003 SET character_set_client  = utf8mb4 */ ;
/*!50003 SET character_set_results = utf8mb4 */ ;
/*!50003 SET collation_connection  = utf8mb4_0900_ai_ci */ ;
/*!50003 SET @saved_sql_mode       = @@sql_mode */ ;
/*!50003 SET sql_mode              = 'ONLY_FULL_GROUP_BY,STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION' */ ;
DELIMITER ;;
/*!50003 CREATE*/ /*!50003 TRIGGER `trg_group_memberships_keep_leader` BEFORE DELETE ON `group_memberships` FOR EACH ROW BEGIN
    IF EXISTS (
        SELECT 1 FROM student_groups sg
        WHERE sg.id = OLD.group_id
          AND sg.leader_student_id = OLD.student_id
          AND sg.dissolved_at IS NULL
    ) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Transfer leadership before removing the leader';
    END IF;
END */;;
DELIMITER ;
/*!50003 SET sql_mode              = @saved_sql_mode */ ;
/*!50003 SET character_set_client  = @saved_cs_client */ ;
/*!50003 SET character_set_results = @saved_cs_results */ ;
/*!50003 SET collation_connection  = @saved_col_connection */ ;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `notification_events` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `event_key` varchar(191) NOT NULL,
  `category` varchar(32) NOT NULL,
  `event_type` varchar(64) NOT NULL,
  `title` varchar(200) NOT NULL,
  `body` text NOT NULL,
  `actor_id` bigint DEFAULT NULL,
  `announcement_id` bigint DEFAULT NULL,
  `target_type` varchar(32) NOT NULL,
  `target_id` bigint DEFAULT NULL,
  `created_at` timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `updated_at` timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_notification_events_key` (`event_key`),
  UNIQUE KEY `uq_notification_events_announcement` (`announcement_id`),
  KEY `idx_notification_events_actor` (`actor_id`),
  KEY `idx_notification_events_created` (`created_at`,`id`),
  CONSTRAINT `fk_notification_events_actor` FOREIGN KEY (`actor_id`) REFERENCES `user_accounts` (`id`),
  CONSTRAINT `fk_notification_events_announcement` FOREIGN KEY (`announcement_id`) REFERENCES `announcements` (`id`),
  CONSTRAINT `chk_notification_events_announcement` CHECK ((((`event_type` = _utf8mb4'ANNOUNCEMENT_PUBLISHED') and (`announcement_id` is not null)) or ((`event_type` <> _utf8mb4'ANNOUNCEMENT_PUBLISHED') and (`announcement_id` is null)))),
  CONSTRAINT `chk_notification_events_body` CHECK ((trim(`body`) <> _utf8mb4'')),
  CONSTRAINT `chk_notification_events_category` CHECK ((`category` in (_utf8mb4'ANNOUNCEMENT',_utf8mb4'ACADEMIC_EVENT'))),
  CONSTRAINT `chk_notification_events_key` CHECK ((trim(`event_key`) <> _utf8mb4'')),
  CONSTRAINT `chk_notification_events_target` CHECK ((((`target_type` = _utf8mb4'NONE') and (`target_id` is null)) or ((`target_type` <> _utf8mb4'NONE') and (`target_id` is not null) and (`target_id` > 0)))),
  CONSTRAINT `chk_notification_events_target_type` CHECK ((`target_type` in (_utf8mb4'ANNOUNCEMENT',_utf8mb4'GROUP_INVITATION',_utf8mb4'GROUP',_utf8mb4'TOPIC',_utf8mb4'REGISTRATION',_utf8mb4'DIRECT_GRADING',_utf8mb4'REVIEWER_ASSIGNMENT',_utf8mb4'COUNCIL',_utf8mb4'RESULT',_utf8mb4'NONE'))),
  CONSTRAINT `chk_notification_events_title` CHECK ((trim(`title`) <> _utf8mb4'')),
  CONSTRAINT `chk_notification_events_type` CHECK ((`event_type` in (_utf8mb4'ANNOUNCEMENT_PUBLISHED',_utf8mb4'GROUP_INVITATION_CREATED',_utf8mb4'GROUP_INVITATION_ACCEPTED',_utf8mb4'GROUP_INVITATION_REJECTED',_utf8mb4'TOPIC_PROPOSAL_APPROVED',_utf8mb4'TOPIC_PROPOSAL_REJECTED',_utf8mb4'REGISTRATION_ACCEPTED',_utf8mb4'REGISTRATION_REJECTED',_utf8mb4'REGISTRATION_AUTO_REJECTED',_utf8mb4'DIRECT_GRADER_ASSIGNED',_utf8mb4'REVIEWER_ASSIGNED',_utf8mb4'COUNCIL_MEMBER_ASSIGNED',_utf8mb4'RESULT_PUBLISHED')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Một lần phát sinh nội dung thông báo bất biến';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `project_reports` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `registration_id` bigint NOT NULL,
  `registration_status` varchar(32) NOT NULL DEFAULT 'ACCEPTED',
  `submitted_by` bigint NOT NULL,
  `submitted_at` timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `file_name` varchar(500) NOT NULL,
  `storage_key` varchar(1000) NOT NULL,
  `content_type` varchar(255) NOT NULL,
  `size_bytes` bigint NOT NULL,
  `replaced_at` timestamp(6) NULL DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `current_registration_guard` bigint GENERATED ALWAYS AS ((case when (`replaced_at` is null) then `registration_id` else NULL end)) STORED,
  `created_at` timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `updated_at` timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_project_reports_current_registration` (`current_registration_guard`),
  KEY `idx_reports_registration` (`registration_id`),
  KEY `idx_reports_submitter` (`submitted_by`),
  KEY `fk_reports_registration_status` (`registration_id`,`registration_status`),
  CONSTRAINT `fk_reports_registration_status` FOREIGN KEY (`registration_id`, `registration_status`) REFERENCES `registrations` (`id`, `status`),
  CONSTRAINT `fk_reports_submitter` FOREIGN KEY (`submitted_by`) REFERENCES `user_accounts` (`id`),
  CONSTRAINT `chk_project_reports_content_type` CHECK ((`content_type` = _utf8mb4'application/pdf')),
  CONSTRAINT `chk_project_reports_file_name` CHECK ((trim(`file_name`) <> _utf8mb4'')),
  CONSTRAINT `chk_project_reports_size` CHECK ((`size_bytes` between 1 and 20971520)),
  CONSTRAINT `chk_project_reports_status` CHECK ((`registration_status` = _utf8mb4'ACCEPTED')),
  CONSTRAINT `chk_project_reports_storage_key` CHECK ((trim(`storage_key`) <> _utf8mb4'')),
  CONSTRAINT `chk_project_reports_version` CHECK ((`version` >= 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Metadata báo cáo do nhóm nộp';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `registration_members` (
  `registration_id` bigint NOT NULL,
  `student_id` bigint NOT NULL,
  `slot` smallint NOT NULL,
  `leader_at_submission` boolean NOT NULL,
  `leader_guard` tinyint GENERATED ALWAYS AS ((case when (`leader_at_submission` = true) then 1 else NULL end)) STORED,
  `created_at` timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `updated_at` timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`registration_id`,`student_id`),
  UNIQUE KEY `uq_registration_members_slot` (`registration_id`,`slot`),
  UNIQUE KEY `uq_registration_members_leader` (`registration_id`,`leader_guard`),
  KEY `idx_registration_members_student` (`student_id`),
  CONSTRAINT `fk_registration_members_registration` FOREIGN KEY (`registration_id`) REFERENCES `registrations` (`id`),
  CONSTRAINT `fk_registration_members_student` FOREIGN KEY (`student_id`) REFERENCES `user_accounts` (`id`),
  CONSTRAINT `chk_registration_members_slot` CHECK ((`slot` between 1 and 3))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Ảnh chụp bất biến thành viên và trưởng nhóm khi gửi đăng ký';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!50003 SET @saved_cs_client      = @@character_set_client */ ;
/*!50003 SET @saved_cs_results     = @@character_set_results */ ;
/*!50003 SET @saved_col_connection = @@collation_connection */ ;
/*!50003 SET character_set_client  = utf8mb4 */ ;
/*!50003 SET character_set_results = utf8mb4 */ ;
/*!50003 SET collation_connection  = utf8mb4_0900_ai_ci */ ;
/*!50003 SET @saved_sql_mode       = @@sql_mode */ ;
/*!50003 SET sql_mode              = 'ONLY_FULL_GROUP_BY,STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION' */ ;
DELIMITER ;;
/*!50003 CREATE*/ /*!50003 TRIGGER `trg_registration_members_immutable_update` BEFORE UPDATE ON `registration_members` FOR EACH ROW BEGIN
    SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'Registration member snapshot is immutable';
END */;;
DELIMITER ;
/*!50003 SET sql_mode              = @saved_sql_mode */ ;
/*!50003 SET character_set_client  = @saved_cs_client */ ;
/*!50003 SET character_set_results = @saved_cs_results */ ;
/*!50003 SET collation_connection  = @saved_col_connection */ ;
/*!50003 SET @saved_cs_client      = @@character_set_client */ ;
/*!50003 SET @saved_cs_results     = @@character_set_results */ ;
/*!50003 SET @saved_col_connection = @@collation_connection */ ;
/*!50003 SET character_set_client  = utf8mb4 */ ;
/*!50003 SET character_set_results = utf8mb4 */ ;
/*!50003 SET collation_connection  = utf8mb4_0900_ai_ci */ ;
/*!50003 SET @saved_sql_mode       = @@sql_mode */ ;
/*!50003 SET sql_mode              = 'ONLY_FULL_GROUP_BY,STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION' */ ;
DELIMITER ;;
/*!50003 CREATE*/ /*!50003 TRIGGER `trg_registration_members_immutable_delete` BEFORE DELETE ON `registration_members` FOR EACH ROW BEGIN
    SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'Registration member snapshot is immutable';
END */;;
DELIMITER ;
/*!50003 SET sql_mode              = @saved_sql_mode */ ;
/*!50003 SET character_set_client  = @saved_cs_client */ ;
/*!50003 SET character_set_results = @saved_cs_results */ ;
/*!50003 SET collation_connection  = @saved_col_connection */ ;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `registration_periods` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `faculty_id` bigint NOT NULL,
  `department_id` bigint NOT NULL,
  `name` varchar(255) NOT NULL,
  `topic_type` varchar(16) NOT NULL,
  `lecturer_start` timestamp(6) NOT NULL,
  `lecturer_end` timestamp(6) NOT NULL,
  `student_start` timestamp(6) NOT NULL,
  `student_end` timestamp(6) NOT NULL,
  `reviewer_score_deadline` date DEFAULT NULL,
  `council_report_date` date DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `created_at` timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `updated_at` timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  KEY `idx_periods_faculty` (`faculty_id`),
  KEY `idx_periods_department` (`department_id`),
  KEY `idx_periods_department_faculty` (`department_id`,`faculty_id`),
  CONSTRAINT `fk_periods_department_faculty` FOREIGN KEY (`department_id`, `faculty_id`) REFERENCES `departments` (`id`, `faculty_id`),
  CONSTRAINT `fk_periods_faculty` FOREIGN KEY (`faculty_id`) REFERENCES `faculties` (`id`),
  CONSTRAINT `chk_periods_dates_by_type` CHECK ((((`topic_type` in (_utf8mb4'COURSE',_utf8mb4'NCKH')) and (`reviewer_score_deadline` is null) and (`council_report_date` is null)) or ((`topic_type` = _utf8mb4'TLCN') and (`reviewer_score_deadline` is not null) and (`council_report_date` is null)) or ((`topic_type` = _utf8mb4'KLTN') and (`reviewer_score_deadline` is not null) and (`council_report_date` is not null)))),
  CONSTRAINT `chk_periods_name` CHECK ((trim(`name`) <> _utf8mb4'')),
  CONSTRAINT `chk_periods_topic_type` CHECK ((`topic_type` in (_utf8mb4'COURSE',_utf8mb4'NCKH',_utf8mb4'TLCN',_utf8mb4'KLTN'))),
  CONSTRAINT `chk_periods_version` CHECK ((`version` >= 0)),
  CONSTRAINT `chk_periods_windows` CHECK (((`lecturer_start` < `lecturer_end`) and (`lecturer_end` < `student_start`) and (`student_start` < `student_end`)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Đợt đăng ký và các mốc thời gian';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `registrations` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `period_id` bigint NOT NULL,
  `group_id` bigint NOT NULL,
  `topic_id` bigint NOT NULL,
  `status` varchar(32) NOT NULL DEFAULT 'PENDING',
  `submitted_at` timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `decided_at` timestamp(6) NULL DEFAULT NULL,
  `withdrawn_at` timestamp(6) NULL DEFAULT NULL,
  `decision_actor_id` bigint DEFAULT NULL,
  `rejection_kind` varchar(32) DEFAULT NULL,
  `rejection_reason` text,
  `version` bigint NOT NULL DEFAULT '0',
  `active_group_guard` bigint GENERATED ALWAYS AS ((case when (`status` in (_utf8mb4'PENDING',_utf8mb4'ACCEPTED')) then `group_id` else NULL end)) STORED,
  `created_at` timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `updated_at` timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_registrations_id_period` (`id`,`period_id`),
  UNIQUE KEY `uq_registrations_id_status` (`id`,`status`),
  UNIQUE KEY `uq_one_active_registration_per_group` (`period_id`,`active_group_guard`),
  KEY `idx_registrations_group_period` (`group_id`,`period_id`),
  KEY `idx_registrations_topic_period` (`topic_id`,`period_id`),
  KEY `idx_registrations_actor` (`decision_actor_id`),
  KEY `idx_registrations_topic_status` (`topic_id`,`status`),
  CONSTRAINT `fk_registrations_actor` FOREIGN KEY (`decision_actor_id`) REFERENCES `user_accounts` (`id`),
  CONSTRAINT `fk_registrations_group_period` FOREIGN KEY (`group_id`, `period_id`) REFERENCES `student_groups` (`id`, `period_id`),
  CONSTRAINT `fk_registrations_period` FOREIGN KEY (`period_id`) REFERENCES `registration_periods` (`id`),
  CONSTRAINT `fk_registrations_topic_period` FOREIGN KEY (`topic_id`, `period_id`) REFERENCES `topics` (`id`, `period_id`),
  CONSTRAINT `chk_registrations_auto_reason` CHECK (((`rejection_kind` is null) or (`rejection_kind` <> _utf8mb4'TOPIC_TAKEN') or ((`status` = _utf8mb4'REJECTED') and (`rejection_reason` is not null) and (`rejection_reason` in (_utf8mb4'Đề tài đã nhận nhóm',_utf8mb4'Đề tài đã đủ số lượng nhóm'))))),
  CONSTRAINT `chk_registrations_rejection_kind` CHECK (((`rejection_kind` is null) or (`rejection_kind` in (_utf8mb4'MANUAL',_utf8mb4'TOPIC_TAKEN')))),
  CONSTRAINT `chk_registrations_rejection_metadata` CHECK ((((`status` = _utf8mb4'REJECTED') and (`rejection_kind` is not null) and (`rejection_kind` in (_utf8mb4'MANUAL',_utf8mb4'TOPIC_TAKEN')) and (`rejection_reason` is not null) and (char_length(trim(`rejection_reason`)) > 0)) or ((`status` <> _utf8mb4'REJECTED') and (`rejection_kind` is null) and (`rejection_reason` is null)))),
  CONSTRAINT `chk_registrations_status` CHECK ((`status` in (_utf8mb4'PENDING',_utf8mb4'ACCEPTED',_utf8mb4'REJECTED',_utf8mb4'WITHDRAWN'))),
  CONSTRAINT `chk_registrations_version` CHECK ((`version` >= 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Đăng ký đề tài và kết quả xét duyệt';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!50003 SET @saved_cs_client      = @@character_set_client */ ;
/*!50003 SET @saved_cs_results     = @@character_set_results */ ;
/*!50003 SET @saved_col_connection = @@collation_connection */ ;
/*!50003 SET character_set_client  = utf8mb4 */ ;
/*!50003 SET character_set_results = utf8mb4 */ ;
/*!50003 SET collation_connection  = utf8mb4_0900_ai_ci */ ;
/*!50003 SET @saved_sql_mode       = @@sql_mode */ ;
/*!50003 SET sql_mode              = 'ONLY_FULL_GROUP_BY,STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION' */ ;
DELIMITER ;;
/*!50003 CREATE*/ /*!50003 TRIGGER `trg_registrations_snapshot_members` AFTER INSERT ON `registrations` FOR EACH ROW BEGIN
    DECLARE member_count INT;
    DECLARE leader_count INT;

    SELECT COUNT(*) INTO member_count
    FROM group_memberships membership
    WHERE membership.group_id = NEW.group_id;

    SELECT COUNT(*) INTO leader_count
    FROM group_memberships membership
    JOIN student_groups student_group ON student_group.id = membership.group_id
    WHERE membership.group_id = NEW.group_id
      AND membership.student_id = student_group.leader_student_id;

    IF member_count <> 3 OR leader_count <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Registration requires exactly three members and one leader';
    END IF;

    INSERT INTO registration_members(
        registration_id, student_id, slot, leader_at_submission)
    SELECT
        NEW.id,
        membership.student_id,
        membership.slot,
        membership.student_id = student_group.leader_student_id
    FROM group_memberships membership
    JOIN student_groups student_group ON student_group.id = membership.group_id
    WHERE membership.group_id = NEW.group_id;
END */;;
DELIMITER ;
/*!50003 SET sql_mode              = @saved_sql_mode */ ;
/*!50003 SET character_set_client  = @saved_cs_client */ ;
/*!50003 SET character_set_results = @saved_cs_results */ ;
/*!50003 SET collation_connection  = @saved_col_connection */ ;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `reviewer_assignments` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `registration_id` bigint NOT NULL,
  `registration_status` varchar(32) NOT NULL DEFAULT 'ACCEPTED',
  `lecturer_id` bigint NOT NULL,
  `deadline_date` date DEFAULT NULL,
  `status` varchar(32) NOT NULL DEFAULT 'LEGACY_UNVERIFIED',
  `assigned_by` bigint DEFAULT NULL,
  `assigned_at` timestamp(6) NULL DEFAULT NULL,
  `submitted_at` timestamp(6) NULL DEFAULT NULL,
  `replaced_by` bigint DEFAULT NULL,
  `replaced_at` timestamp(6) NULL DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `active_registration_guard` bigint GENERATED ALWAYS AS ((case when (`status` in (_utf8mb4'ASSIGNED',_utf8mb4'SUBMITTED')) then `registration_id` else NULL end)) STORED,
  `created_at` timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `updated_at` timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_reviewers_id_registration` (`id`,`registration_id`),
  UNIQUE KEY `uq_reviewers_one_active_registration` (`active_registration_guard`),
  KEY `idx_reviewers_lecturer` (`lecturer_id`),
  KEY `idx_reviewers_registration_status` (`registration_id`,`registration_status`),
  KEY `idx_reviewers_registration_lecturer` (`registration_id`,`lecturer_id`),
  KEY `idx_reviewers_assigned_by` (`assigned_by`),
  KEY `idx_reviewers_replaced_by` (`replaced_by`),
  CONSTRAINT `fk_reviewers_assigned_by` FOREIGN KEY (`assigned_by`) REFERENCES `user_accounts` (`id`),
  CONSTRAINT `fk_reviewers_lecturer` FOREIGN KEY (`lecturer_id`) REFERENCES `user_accounts` (`id`),
  CONSTRAINT `fk_reviewers_registration_status` FOREIGN KEY (`registration_id`, `registration_status`) REFERENCES `registrations` (`id`, `status`),
  CONSTRAINT `fk_reviewers_replaced_by` FOREIGN KEY (`replaced_by`) REFERENCES `user_accounts` (`id`),
  CONSTRAINT `chk_reviewer_assignment_status` CHECK ((`registration_status` = _utf8mb4'ACCEPTED')),
  CONSTRAINT `chk_reviewers_status` CHECK ((((`status` = _utf8mb4'LEGACY_UNVERIFIED') and (`deadline_date` is null) and (`assigned_by` is null) and (`assigned_at` is null) and (`submitted_at` is null) and (`replaced_by` is null) and (`replaced_at` is null)) or ((`status` = _utf8mb4'ASSIGNED') and (`deadline_date` is not null) and (`assigned_by` is not null) and (`assigned_at` is not null) and (`submitted_at` is null) and (`replaced_by` is null) and (`replaced_at` is null)) or ((`status` = _utf8mb4'SUBMITTED') and (`deadline_date` is not null) and (`assigned_by` is not null) and (`assigned_at` is not null) and (`submitted_at` is not null) and (`replaced_by` is null) and (`replaced_at` is null)) or ((`status` = _utf8mb4'REPLACED') and (`deadline_date` is not null) and (`assigned_by` is not null) and (`assigned_at` is not null) and (`submitted_at` is null) and (`replaced_by` is not null) and (`replaced_at` is not null)))),
  CONSTRAINT `chk_reviewers_version` CHECK ((`version` >= 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Phân công giảng viên phản biện';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `reviewer_evaluations` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `assignment_id` bigint NOT NULL,
  `registration_id` bigint NOT NULL,
  `student_id` bigint NOT NULL,
  `score` decimal(12,4) NOT NULL,
  `feedback` text,
  `status` varchar(32) NOT NULL DEFAULT 'DRAFT',
  `submitted_at` timestamp(6) NULL DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `created_at` timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `updated_at` timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_reviewer_evaluations_assignment_student` (`assignment_id`,`student_id`),
  KEY `idx_reviewer_evaluations_assignment_registration` (`assignment_id`,`registration_id`),
  KEY `idx_reviewer_evaluations_registration_student` (`registration_id`,`student_id`),
  CONSTRAINT `fk_reviewer_evaluations_assignment_registration` FOREIGN KEY (`assignment_id`, `registration_id`) REFERENCES `reviewer_assignments` (`id`, `registration_id`),
  CONSTRAINT `fk_reviewer_evaluations_member` FOREIGN KEY (`registration_id`, `student_id`) REFERENCES `registration_members` (`registration_id`, `student_id`),
  CONSTRAINT `chk_reviewer_evaluations_score` CHECK (((`score` between 0.00 and 10.00) and (`score` = round(`score`,2)))),
  CONSTRAINT `chk_reviewer_evaluations_submission` CHECK ((((`status` = _utf8mb4'DRAFT') and (`submitted_at` is null)) or ((`status` = _utf8mb4'SUBMITTED') and (`submitted_at` is not null)))),
  CONSTRAINT `chk_reviewer_evaluations_version` CHECK ((`version` >= 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Điểm phản biện riêng của từng sinh viên trong đăng ký';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `reviewer_reports` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `assignment_id` bigint NOT NULL,
  `file_name` varchar(500) NOT NULL,
  `storage_key` varchar(1000) NOT NULL,
  `content_type` varchar(255) NOT NULL,
  `size_bytes` bigint NOT NULL,
  `submitted_by` bigint NOT NULL,
  `submitted_at` timestamp(6) NOT NULL,
  `created_at` timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `updated_at` timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_reviewer_reports_assignment` (`assignment_id`),
  KEY `idx_reviewer_reports_submitter` (`submitted_by`),
  CONSTRAINT `fk_reviewer_reports_assignment` FOREIGN KEY (`assignment_id`) REFERENCES `reviewer_assignments` (`id`),
  CONSTRAINT `fk_reviewer_reports_submitter` FOREIGN KEY (`submitted_by`) REFERENCES `user_accounts` (`id`),
  CONSTRAINT `chk_reviewer_reports_content_type` CHECK ((`content_type` = _utf8mb4'application/pdf')),
  CONSTRAINT `chk_reviewer_reports_file_name` CHECK ((trim(`file_name`) <> _utf8mb4'')),
  CONSTRAINT `chk_reviewer_reports_size` CHECK ((`size_bytes` between 1 and 20971520)),
  CONSTRAINT `chk_reviewer_reports_storage_key` CHECK ((trim(`storage_key`) <> _utf8mb4''))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Metadata báo cáo PDF do giảng viên phản biện nộp';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `roles` (
  `code` varchar(32) NOT NULL,
  `name` varchar(100) NOT NULL,
  `created_at` timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `updated_at` timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Danh mục vai trò tài khoản';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `score_components` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `period_id` bigint NOT NULL,
  `name` varchar(255) NOT NULL,
  `minimum_score` decimal(12,4) DEFAULT NULL,
  `maximum_score` decimal(12,4) DEFAULT NULL,
  `created_at` timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `updated_at` timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_score_components_id_period` (`id`,`period_id`),
  UNIQUE KEY `uq_score_components_period_name` (`period_id`,`name`),
  CONSTRAINT `fk_score_components_period` FOREIGN KEY (`period_id`) REFERENCES `registration_periods` (`id`),
  CONSTRAINT `chk_score_components_name` CHECK ((trim(`name`) <> _utf8mb4'')),
  CONSTRAINT `chk_score_components_range` CHECK (((`minimum_score` is null) or (`maximum_score` is null) or (`minimum_score` < `maximum_score`)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Định nghĩa điểm thành phần theo đợt';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `student_groups` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `period_id` bigint NOT NULL,
  `leader_student_id` bigint NOT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `dissolved_at` timestamp(6) NULL DEFAULT NULL,
  `created_at` timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `updated_at` timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_student_groups_id_period` (`id`,`period_id`),
  KEY `idx_groups_period` (`period_id`),
  KEY `idx_groups_leader` (`leader_student_id`),
  KEY `idx_student_groups_dissolved_at` (`dissolved_at`),
  CONSTRAINT `fk_groups_leader_account` FOREIGN KEY (`leader_student_id`) REFERENCES `user_accounts` (`id`),
  CONSTRAINT `fk_groups_period` FOREIGN KEY (`period_id`) REFERENCES `registration_periods` (`id`),
  CONSTRAINT `chk_student_groups_version` CHECK ((`version` >= 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Nhóm sinh viên; trigger tự tạo membership cho trưởng nhóm';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!50003 SET @saved_cs_client      = @@character_set_client */ ;
/*!50003 SET @saved_cs_results     = @@character_set_results */ ;
/*!50003 SET @saved_col_connection = @@collation_connection */ ;
/*!50003 SET character_set_client  = utf8mb4 */ ;
/*!50003 SET character_set_results = utf8mb4 */ ;
/*!50003 SET collation_connection  = utf8mb4_0900_ai_ci */ ;
/*!50003 SET @saved_sql_mode       = @@sql_mode */ ;
/*!50003 SET sql_mode              = 'ONLY_FULL_GROUP_BY,STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION' */ ;
DELIMITER ;;
/*!50003 CREATE*/ /*!50003 TRIGGER `trg_student_groups_add_leader` AFTER INSERT ON `student_groups` FOR EACH ROW BEGIN
    INSERT INTO group_memberships(group_id, period_id, student_id, slot)
    VALUES (NEW.id, NEW.period_id, NEW.leader_student_id, 1);
END */;;
DELIMITER ;
/*!50003 SET sql_mode              = @saved_sql_mode */ ;
/*!50003 SET character_set_client  = @saved_cs_client */ ;
/*!50003 SET character_set_results = @saved_cs_results */ ;
/*!50003 SET collation_connection  = @saved_col_connection */ ;
/*!50003 SET @saved_cs_client      = @@character_set_client */ ;
/*!50003 SET @saved_cs_results     = @@character_set_results */ ;
/*!50003 SET @saved_col_connection = @@collation_connection */ ;
/*!50003 SET character_set_client  = utf8mb4 */ ;
/*!50003 SET character_set_results = utf8mb4 */ ;
/*!50003 SET collation_connection  = utf8mb4_0900_ai_ci */ ;
/*!50003 SET @saved_sql_mode       = @@sql_mode */ ;
/*!50003 SET sql_mode              = 'ONLY_FULL_GROUP_BY,STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION' */ ;
DELIMITER ;;
/*!50003 CREATE*/ /*!50003 TRIGGER `trg_student_groups_validate_leader` BEFORE UPDATE ON `student_groups` FOR EACH ROW BEGIN
    IF NEW.leader_student_id <> OLD.leader_student_id
       AND NOT EXISTS (
           SELECT 1 FROM group_memberships gm
           WHERE gm.group_id = OLD.id AND gm.student_id = NEW.leader_student_id
       ) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Group leader must already be a member';
    END IF;
END */;;
DELIMITER ;
/*!50003 SET sql_mode              = @saved_sql_mode */ ;
/*!50003 SET character_set_client  = @saved_cs_client */ ;
/*!50003 SET character_set_results = @saved_cs_results */ ;
/*!50003 SET collation_connection  = @saved_col_connection */ ;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `student_profiles` (
  `user_id` bigint NOT NULL,
  `student_code` varchar(32) NOT NULL,
  `class_name` varchar(64) NOT NULL,
  `cohort_code` varchar(16) NOT NULL,
  `created_at` timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `updated_at` timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`user_id`),
  UNIQUE KEY `uq_student_profiles_student_code` (`student_code`),
  CONSTRAINT `fk_student_profiles_user` FOREIGN KEY (`user_id`) REFERENCES `user_accounts` (`id`) ON DELETE CASCADE,
  CONSTRAINT `chk_student_profiles_class_name` CHECK ((trim(`class_name`) <> _utf8mb4'')),
  CONSTRAINT `chk_student_profiles_cohort_code` CHECK (regexp_like(`cohort_code`,_utf8mb4'^K[0-9]{4}$')),
  CONSTRAINT `chk_student_profiles_student_code` CHECK ((trim(`student_code`) <> _utf8mb4''))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Hồ sơ nhận diện chỉ dành cho tài khoản sinh viên';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `topic_supervisors` (
  `topic_id` bigint NOT NULL,
  `lecturer_id` bigint NOT NULL,
  `slot` smallint NOT NULL,
  `created_at` timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `updated_at` timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`topic_id`,`lecturer_id`),
  UNIQUE KEY `uq_topic_supervisors_slot` (`topic_id`,`slot`),
  KEY `idx_supervisors_lecturer` (`lecturer_id`),
  CONSTRAINT `fk_supervisors_lecturer` FOREIGN KEY (`lecturer_id`) REFERENCES `user_accounts` (`id`),
  CONSTRAINT `fk_supervisors_topic` FOREIGN KEY (`topic_id`) REFERENCES `topics` (`id`),
  CONSTRAINT `chk_topic_supervisors_slot` CHECK ((`slot` between 1 and 2))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Giảng viên hướng dẫn, tối đa hai người';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `topics` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `period_id` bigint NOT NULL,
  `department_id` bigint NOT NULL,
  `author_id` bigint NOT NULL,
  `title` varchar(500) DEFAULT NULL,
  `description` text,
  `objectives` text,
  `student_requirements` text,
  `expected_results` text,
  `status` varchar(32) NOT NULL DEFAULT 'DRAFT',
  `max_group_count` int NOT NULL DEFAULT '1',
  `hidden_at` timestamp(6) NULL DEFAULT NULL,
  `hidden_by` bigint DEFAULT NULL,
  `hidden_reason` varchar(1000) DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `created_at` timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `updated_at` timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_topics_id_period` (`id`,`period_id`),
  KEY `idx_topics_period_status` (`period_id`,`status`),
  KEY `idx_topics_author` (`author_id`),
  KEY `idx_topics_hidden_by` (`hidden_by`),
  KEY `idx_topics_department` (`department_id`),
  CONSTRAINT `fk_topics_author` FOREIGN KEY (`author_id`) REFERENCES `user_accounts` (`id`),
  CONSTRAINT `fk_topics_department_required` FOREIGN KEY (`department_id`) REFERENCES `departments` (`id`),
  CONSTRAINT `fk_topics_hidden_by` FOREIGN KEY (`hidden_by`) REFERENCES `user_accounts` (`id`),
  CONSTRAINT `fk_topics_period` FOREIGN KEY (`period_id`) REFERENCES `registration_periods` (`id`),
  CONSTRAINT `chk_topics_hidden_metadata` CHECK ((((`hidden_at` is null) and (`hidden_by` is null) and (`hidden_reason` is null)) or ((`hidden_at` is not null) and (`hidden_by` is not null) and (coalesce(trim(`hidden_reason`),_utf8mb4'') <> _utf8mb4'')))),
  CONSTRAINT `chk_topics_max_group_count` CHECK ((`max_group_count` between 1 and 5)),
  CONSTRAINT `chk_topics_status` CHECK ((`status` in (_utf8mb4'DRAFT',_utf8mb4'PENDING_APPROVAL',_utf8mb4'APPROVED',_utf8mb4'PUBLISHED'))),
  CONSTRAINT `chk_topics_submitted_content` CHECK (((`status` = _utf8mb4'DRAFT') or ((coalesce(trim(`title`),_utf8mb4'') <> _utf8mb4'') and (coalesce(trim(`description`),_utf8mb4'') <> _utf8mb4'') and (coalesce(trim(`objectives`),_utf8mb4'') <> _utf8mb4'') and (coalesce(trim(`student_requirements`),_utf8mb4'') <> _utf8mb4'') and (coalesce(trim(`expected_results`),_utf8mb4'') <> _utf8mb4'')))),
  CONSTRAINT `chk_topics_version` CHECK ((`version` >= 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Đề tài thuộc một đợt đăng ký';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `user_accounts` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `full_name` varchar(255) DEFAULT NULL,
  `email` varchar(254) DEFAULT NULL,
  `enabled` boolean NOT NULL DEFAULT TRUE,
  `username` varchar(191) NOT NULL,
  `password_hash` varchar(255) NOT NULL,
  `must_change_password` boolean NOT NULL DEFAULT FALSE,
  `security_version` bigint NOT NULL DEFAULT '0',
  `faculty_id` bigint DEFAULT NULL,
  `department_id` bigint DEFAULT NULL,
  `created_at` timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `updated_at` timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_user_accounts_username` (`username`),
  UNIQUE KEY `uq_user_accounts_email` (`email`),
  KEY `idx_accounts_faculty` (`faculty_id`),
  KEY `idx_accounts_department` (`department_id`),
  CONSTRAINT `fk_accounts_department` FOREIGN KEY (`department_id`) REFERENCES `departments` (`id`),
  CONSTRAINT `fk_accounts_faculty` FOREIGN KEY (`faculty_id`) REFERENCES `faculties` (`id`),
  CONSTRAINT `chk_accounts_password_hash` CHECK ((trim(`password_hash`) <> _utf8mb4'')),
  CONSTRAINT `chk_accounts_username` CHECK ((trim(`username`) <> _utf8mb4'')),
  CONSTRAINT `chk_user_accounts_email` CHECK (((`email` is null) or (trim(`email`) <> _utf8mb4''))),
  CONSTRAINT `chk_user_accounts_security_version` CHECK ((`security_version` >= 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Tài khoản ứng dụng, mật khẩu đã hash';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `user_notifications` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `event_id` bigint NOT NULL,
  `recipient_id` bigint NOT NULL,
  `delivered_at` timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `read_at` timestamp(6) NULL DEFAULT NULL,
  `created_at` timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `updated_at` timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_user_notifications_event_recipient` (`event_id`,`recipient_id`),
  KEY `idx_user_notifications_recipient_read_time` (`recipient_id`,`read_at`,`delivered_at`,`id`),
  KEY `idx_user_notifications_recipient_time` (`recipient_id`,`delivered_at`,`id`),
  CONSTRAINT `fk_user_notifications_event` FOREIGN KEY (`event_id`) REFERENCES `notification_events` (`id`),
  CONSTRAINT `fk_user_notifications_recipient` FOREIGN KEY (`recipient_id`) REFERENCES `user_accounts` (`id`),
  CONSTRAINT `chk_user_notifications_read_time` CHECK (((`read_at` is null) or (`read_at` >= `delivered_at`)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Bản nhận và trạng thái đọc riêng của từng tài khoản';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `user_roles` (
  `user_id` bigint NOT NULL,
  `role_code` varchar(32) NOT NULL,
  `created_at` timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `updated_at` timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`user_id`,`role_code`),
  KEY `idx_user_roles_role` (`role_code`),
  CONSTRAINT `fk_user_roles_role` FOREIGN KEY (`role_code`) REFERENCES `roles` (`code`),
  CONSTRAINT `fk_user_roles_user` FOREIGN KEY (`user_id`) REFERENCES `user_accounts` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Vai trò được cấp cho tài khoản';
/*!40101 SET character_set_client = @saved_cs_client */;

-- Minimal local bootstrap data. Keep authorization catalogs complete while
-- provisioning exactly one account with the existing ADMIN-only scope.
INSERT INTO `roles` (`code`, `name`) VALUES
  ('ADMIN', 'Quản trị viên'),
  ('STUDENT', 'Sinh viên'),
  ('LECTURER', 'Giảng viên'),
  ('DEAN', 'Trưởng khoa');

INSERT INTO `faculties` (`name`)
VALUES ('Khoa Công nghệ Thông tin');

INSERT INTO `user_accounts` (
  `full_name`, `email`, `enabled`, `username`, `password_hash`,
  `must_change_password`, `security_version`, `faculty_id`, `department_id`
) VALUES (
  'Quản trị viên', NULL, TRUE, 'admin',
  '$2y$10$avj.894ahmSVEV63hfaFtenXcf3KViGeM7Vwl8HwMeo912U/7jlZC',
  FALSE, 0, NULL, NULL
);

INSERT INTO `user_roles` (`user_id`, `role_code`)
SELECT `id`, 'ADMIN' FROM `user_accounts` WHERE `username` = 'admin';
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
