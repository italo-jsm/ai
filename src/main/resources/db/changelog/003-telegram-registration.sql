--liquibase formatted sql
--changeset codex:003-telegram-registration
ALTER TABLE app_user ADD COLUMN full_name VARCHAR(255);
ALTER TABLE app_user ADD COLUMN email VARCHAR(255);
ALTER TABLE app_user ADD COLUMN email_verified_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE app_user ADD COLUMN created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL;
ALTER TABLE app_user ADD CONSTRAINT uk_app_user_email UNIQUE (email);
CREATE TABLE telegram_registration (
    telegram_user_id BIGINT PRIMARY KEY,
    step VARCHAR(32) NOT NULL,
    full_name VARCHAR(255),
    username VARCHAR(255),
    email VARCHAR(255),
    code_hash VARCHAR(64),
    expires_at TIMESTAMP WITH TIME ZONE,
    last_sent_at TIMESTAMP WITH TIME ZONE,
    attempts INTEGER NOT NULL,
    last_update_id BIGINT
);
