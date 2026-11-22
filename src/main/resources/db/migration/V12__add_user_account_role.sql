-- Assign USER as the default role for existing and new accounts.

ALTER TABLE user_account
    ADD COLUMN role VARCHAR(20) NOT NULL DEFAULT 'USER';

ALTER TABLE user_account
    ADD CONSTRAINT chk_user_account_role
    CHECK (role IN ('USER', 'ADMIN'));