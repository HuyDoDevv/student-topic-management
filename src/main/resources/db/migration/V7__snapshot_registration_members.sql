CREATE TABLE registration_members (
    registration_id BIGINT NOT NULL,
    student_id BIGINT NOT NULL,
    slot SMALLINT NOT NULL,
    leader_at_submission BOOLEAN NOT NULL,
    leader_guard TINYINT GENERATED ALWAYS AS (
        CASE WHEN leader_at_submission = TRUE THEN 1 ELSE NULL END
    ) STORED,
    PRIMARY KEY (registration_id, student_id),
    UNIQUE KEY uq_registration_members_slot (registration_id, slot),
    UNIQUE KEY uq_registration_members_leader (registration_id, leader_guard),
    KEY idx_registration_members_student (student_id),
    CONSTRAINT chk_registration_members_slot CHECK (slot BETWEEN 1 AND 3),
    CONSTRAINT fk_registration_members_registration
        FOREIGN KEY (registration_id) REFERENCES registrations(id),
    CONSTRAINT fk_registration_members_student
        FOREIGN KEY (student_id) REFERENCES user_accounts(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Ảnh chụp bất biến thành viên và trưởng nhóm khi gửi đăng ký';

-- Existing history predates this snapshot. Preserve the best data still available at migration
-- time; future inserts are captured atomically by the trigger below.
INSERT INTO registration_members(
    registration_id, student_id, slot, leader_at_submission)
SELECT
    registration.id,
    membership.student_id,
    membership.slot,
    membership.student_id = student_group.leader_student_id
FROM registrations registration
JOIN student_groups student_group ON student_group.id = registration.group_id
JOIN group_memberships membership ON membership.group_id = student_group.id;

-- A dissolved legacy group no longer has memberships. Its full old roster cannot be
-- reconstructed, but retaining the stored leader keeps the historical row attributable.
INSERT INTO registration_members(
    registration_id, student_id, slot, leader_at_submission)
SELECT registration.id, student_group.leader_student_id, 1, TRUE
FROM registrations registration
JOIN student_groups student_group ON student_group.id = registration.group_id
WHERE NOT EXISTS (
    SELECT 1
    FROM registration_members member
    WHERE member.registration_id = registration.id
);

DELIMITER $$
CREATE TRIGGER trg_registrations_snapshot_members
AFTER INSERT ON registrations
FOR EACH ROW
BEGIN
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
END$$

CREATE TRIGGER trg_registration_members_immutable_update
BEFORE UPDATE ON registration_members
FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'Registration member snapshot is immutable';
END$$

CREATE TRIGGER trg_registration_members_immutable_delete
BEFORE DELETE ON registration_members
FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'Registration member snapshot is immutable';
END$$
DELIMITER ;
