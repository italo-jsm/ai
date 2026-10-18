CREATE TABLE memory (
    id UUID PRIMARY KEY,
    user_id VARCHAR(255) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    type VARCHAR(32) NOT NULL,
    subject TEXT NOT NULL,
    predicate TEXT NOT NULL,
    memory_value TEXT NOT NULL,
    reason TEXT,
    CONSTRAINT ck_memory_type CHECK (type IN ('IDENTITY', 'PREFERENCE', 'FACT', 'CONTEXT'))
);

