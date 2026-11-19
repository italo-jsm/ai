--liquibase formatted sql
--changeset codex:005-finance-links
CREATE TABLE finance_link (
    user_id UUID PRIMARY KEY REFERENCES app_user(id) ON DELETE CASCADE,
    issuer VARCHAR(512) NOT NULL,
    subject VARCHAR(255) NOT NULL,
    access_token TEXT NOT NULL,
    refresh_token TEXT NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE finance_link_request (
    user_id UUID PRIMARY KEY REFERENCES app_user(id) ON DELETE CASCADE,
    state_hash VARCHAR(43) NOT NULL UNIQUE,
    browser_hash VARCHAR(43),
    verifier VARCHAR(43) NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL
);
