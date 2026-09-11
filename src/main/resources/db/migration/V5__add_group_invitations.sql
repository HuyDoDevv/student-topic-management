ALTER TABLE student_groups
    ADD COLUMN dissolved_at TIMESTAMP(6) NULL AFTER version,
    ADD KEY idx_student_groups_dissolved_at (dissolved_at);

-- A dissolved group remains as history, but its final leader membership may be removed.
DROP TRIGGER IF EXISTS trg_group_memberships_keep_leader;

DELIMITER $$
CREATE TRIGGER trg_group_memberships_keep_leader
BEFORE DELETE ON group_memberships
FOR EACH ROW
BEGIN
    IF EXISTS (
        SELECT 1 FROM student_groups sg
        WHERE sg.id = OLD.group_id
          AND sg.leader_student_id = OLD.student_id
          AND sg.dissolved_at IS NULL
    ) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Transfer leadership before removing the leader';
    END IF;
END$$
DELIMITER ;

CREATE TABLE group_invitations (
    id BIGINT NOT NULL AUTO_INCREMENT,
    group_id BIGINT NOT NULL,
    period_id BIGINT NOT NULL,
    student_id BIGINT NOT NULL,
    invited_by BIGINT NOT NULL,
    slot SMALLINT NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    responded_at TIMESTAMP(6) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    pending_student_guard BIGINT GENERATED ALWAYS AS (
        CASE WHEN status = 'PENDING' THEN student_id ELSE NULL END
    ) STORED,
    pending_slot_guard SMALLINT GENERATED ALWAYS AS (
        CASE WHEN status = 'PENDING' THEN slot ELSE NULL END
    ) STORED,
    PRIMARY KEY (id),
    UNIQUE KEY uq_group_invitations_pending_student (period_id, pending_student_guard),
    UNIQUE KEY uq_group_invitations_pending_slot (group_id, pending_slot_guard),
    KEY idx_group_invitations_group_status (group_id, status),
    KEY idx_group_invitations_student_status (student_id, status),
    KEY idx_group_invitations_invited_by (invited_by),
    CONSTRAINT chk_group_invitations_slot CHECK (slot BETWEEN 1 AND 3),
    CONSTRAINT chk_group_invitations_status CHECK (
        status IN ('PENDING', 'ACCEPTED', 'REJECTED', 'CANCELLED')
    ),
    CONSTRAINT chk_group_invitations_response_time CHECK (
        (status = 'PENDING' AND responded_at IS NULL)
        OR (status <> 'PENDING' AND responded_at IS NOT NULL)
    ),
    CONSTRAINT chk_group_invitations_version CHECK (version >= 0),
    CONSTRAINT fk_group_invitations_group_period
        FOREIGN KEY (group_id, period_id) REFERENCES student_groups(id, period_id),
    CONSTRAINT fk_group_invitations_student
        FOREIGN KEY (student_id) REFERENCES user_accounts(id),
    CONSTRAINT fk_group_invitations_invited_by
        FOREIGN KEY (invited_by) REFERENCES user_accounts(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Lời mời vào nhóm sinh viên và lịch sử phản hồi';
