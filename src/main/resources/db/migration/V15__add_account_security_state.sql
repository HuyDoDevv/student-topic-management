ALTER TABLE user_accounts
    ADD COLUMN must_change_password BOOLEAN NOT NULL DEFAULT FALSE AFTER password_hash,
    ADD COLUMN security_version BIGINT NOT NULL DEFAULT 0 AFTER must_change_password,
    ADD CONSTRAINT chk_user_accounts_security_version
        CHECK (security_version >= 0);
