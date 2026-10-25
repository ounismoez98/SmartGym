CREATE TABLE ai_jobs (
    id UUID PRIMARY KEY,
    member_id UUID NOT NULL,
    coach_id UUID NOT NULL,
    created_by_subject VARCHAR(64) NOT NULL,
    request_json TEXT NOT NULL,
    candidate_exercises_json TEXT NOT NULL,
    status VARCHAR(16) NOT NULL CHECK (status IN ('QUEUED', 'COMPLETED', 'FAILED')),
    programme_id UUID REFERENCES training_programmes(id),
    failure_reason VARCHAR(1000),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX ai_jobs_member_created_idx ON ai_jobs (member_id, created_at DESC);
CREATE INDEX ai_jobs_status_created_idx ON ai_jobs (status, created_at);

CREATE TABLE training_outbox (
    id UUID PRIMARY KEY,
    message_id UUID NOT NULL UNIQUE,
    routing_key VARCHAR(160) NOT NULL,
    payload_json TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    published_at TIMESTAMP WITH TIME ZONE,
    attempt_count INTEGER NOT NULL DEFAULT 0 CHECK (attempt_count >= 0),
    last_error VARCHAR(1000)
);

CREATE INDEX training_outbox_pending_idx ON training_outbox (published_at, created_at);
