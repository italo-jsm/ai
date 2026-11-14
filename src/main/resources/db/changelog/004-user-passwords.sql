--liquibase formatted sql
--changeset codex:004-user-passwords
ALTER TABLE app_user ADD COLUMN password_hash VARCHAR(60);
ALTER TABLE app_user ADD COLUMN login_failures INTEGER DEFAULT 0 NOT NULL;
ALTER TABLE app_user ADD COLUMN login_locked_until TIMESTAMP WITH TIME ZONE;
ALTER TABLE telegram_registration ADD COLUMN password_hash VARCHAR(60);
