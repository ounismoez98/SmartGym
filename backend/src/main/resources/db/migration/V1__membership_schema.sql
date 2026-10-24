CREATE TABLE IF NOT EXISTS business_profiles (
    keycloak_subject VARCHAR(64) PRIMARY KEY,
    username VARCHAR(100) NOT NULL,
    display_name VARCHAR(200) NOT NULL,
    member_profile_id UUID,
    employee_profile_id UUID
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_business_profiles_member_profile_id
    ON business_profiles (member_profile_id)
    WHERE member_profile_id IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_business_profiles_employee_profile_id
    ON business_profiles (employee_profile_id)
    WHERE employee_profile_id IS NOT NULL;

CREATE TABLE member (
    id UUID PRIMARY KEY,
    keycloak_user_id VARCHAR(64) NOT NULL UNIQUE,
    username VARCHAR(100) NOT NULL,
    display_name VARCHAR(200) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE'
        CHECK (status IN ('ACTIVE', 'INACTIVE')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE employee (
    id UUID PRIMARY KEY,
    keycloak_user_id VARCHAR(64) NOT NULL UNIQUE,
    username VARCHAR(100) NOT NULL,
    display_name VARCHAR(200) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE'
        CHECK (status IN ('ACTIVE', 'INACTIVE')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO member (id, keycloak_user_id, username, display_name)
SELECT member_profile_id, keycloak_subject, username, display_name
FROM business_profiles
WHERE member_profile_id IS NOT NULL
ON CONFLICT (keycloak_user_id) DO NOTHING;

INSERT INTO employee (id, keycloak_user_id, username, display_name)
SELECT employee_profile_id, keycloak_subject, username, display_name
FROM business_profiles
WHERE employee_profile_id IS NOT NULL
ON CONFLICT (keycloak_user_id) DO NOTHING;

CREATE TABLE IF NOT EXISTS coach_assignments (
    id UUID PRIMARY KEY,
    coach_subject VARCHAR(64) NOT NULL,
    member_subject VARCHAR(64) NOT NULL,
    CONSTRAINT uq_coach_assignments_subjects UNIQUE (coach_subject, member_subject)
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_coach_assignments_subjects
    ON coach_assignments (coach_subject, member_subject);

CREATE INDEX IF NOT EXISTS ix_coach_assignments_coach_subject
    ON coach_assignments (coach_subject);

CREATE TABLE subscription_plan (
    id UUID PRIMARY KEY,
    code VARCHAR(60) NOT NULL UNIQUE,
    name VARCHAR(120) NOT NULL,
    description TEXT NOT NULL,
    duration_days INTEGER NOT NULL CHECK (duration_days > 0),
    price_cents BIGINT NOT NULL CHECK (price_cents >= 0),
    currency CHAR(3) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE subscription (
    id UUID PRIMARY KEY,
    member_id UUID NOT NULL REFERENCES member (id),
    plan_id UUID NOT NULL REFERENCES subscription_plan (id),
    starts_at TIMESTAMPTZ NOT NULL,
    ends_at TIMESTAMPTZ NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE'
        CHECK (status IN ('ACTIVE', 'EXPIRED', 'CANCELLED')),
    cancelled_at TIMESTAMPTZ,
    cancel_reason TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_subscription_dates CHECK (ends_at > starts_at),
    CONSTRAINT ck_subscription_cancellation CHECK (
        (status = 'CANCELLED'
            AND cancelled_at IS NOT NULL
            AND cancel_reason IS NOT NULL
            AND LENGTH(BTRIM(cancel_reason)) > 0)
        OR status <> 'CANCELLED'
    )
);

CREATE INDEX ix_subscription_member_id ON subscription (member_id);
CREATE INDEX ix_subscription_status_ends_at ON subscription (status, ends_at);

CREATE TABLE payment (
    id UUID PRIMARY KEY,
    subscription_id UUID NOT NULL REFERENCES subscription (id),
    amount_cents BIGINT NOT NULL CHECK (amount_cents >= 0),
    currency CHAR(3) NOT NULL,
    method VARCHAR(32) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'RECORDED'
        CHECK (status IN ('RECORDED', 'CORRECTED')),
    corrects_payment_id UUID REFERENCES payment (id),
    reason TEXT,
    created_by VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_payment_correction CHECK (
        (corrects_payment_id IS NULL AND reason IS NULL)
        OR (corrects_payment_id IS NOT NULL
            AND reason IS NOT NULL
            AND LENGTH(BTRIM(reason)) > 0)
    )
);

CREATE INDEX ix_payment_subscription_id ON payment (subscription_id);
CREATE INDEX ix_payment_corrects_payment_id ON payment (corrects_payment_id);

CREATE TABLE audit_log (
    id UUID PRIMARY KEY,
    actor_user_id VARCHAR(64) NOT NULL,
    action VARCHAR(100) NOT NULL,
    entity_type VARCHAR(80) NOT NULL,
    entity_id UUID NOT NULL,
    details JSONB NOT NULL DEFAULT '{}'::JSONB,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX ix_audit_log_entity ON audit_log (entity_type, entity_id);
CREATE INDEX ix_audit_log_occurred_at ON audit_log (occurred_at);

CREATE TABLE gym_policy (
    id UUID PRIMARY KEY,
    policy_key VARCHAR(80) NOT NULL UNIQUE,
    title VARCHAR(160) NOT NULL,
    content TEXT NOT NULL,
    validated BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE reminder_draft (
    id UUID PRIMARY KEY,
    member_id UUID NOT NULL REFERENCES member (id),
    subscription_id UUID REFERENCES subscription (id),
    subject VARCHAR(255) NOT NULL,
    body TEXT NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT'
        CHECK (status IN ('DRAFT', 'CONFIRMED', 'REJECTED')),
    created_by VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    confirmed_at TIMESTAMPTZ,
    rejected_at TIMESTAMPTZ
);

CREATE INDEX ix_reminder_draft_member_id ON reminder_draft (member_id);
CREATE INDEX ix_reminder_draft_status ON reminder_draft (status);

CREATE FUNCTION reject_immutable_record_delete() RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'DELETE is not allowed on table %', TG_TABLE_NAME
        USING ERRCODE = '55000';
END;
$$;

CREATE TRIGGER payment_no_delete
BEFORE DELETE ON payment
FOR EACH ROW EXECUTE FUNCTION reject_immutable_record_delete();

CREATE TRIGGER subscription_no_delete
BEFORE DELETE ON subscription
FOR EACH ROW EXECUTE FUNCTION reject_immutable_record_delete();

CREATE TRIGGER audit_log_no_delete
BEFORE DELETE ON audit_log
FOR EACH ROW EXECUTE FUNCTION reject_immutable_record_delete();
