ALTER TABLE "member"
    ADD COLUMN first_name VARCHAR(100),
    ADD COLUMN last_name VARCHAR(100),
    ADD COLUMN email VARCHAR(254),
    ADD COLUMN phone VARCHAR(40),
    ADD COLUMN birth_date DATE;

CREATE UNIQUE INDEX uq_member_email_ci
    ON "member" (LOWER(email))
    WHERE email IS NOT NULL;

CREATE INDEX ix_member_status_created_at
    ON "member" (status, created_at DESC);
