ALTER TABLE employee
    ADD COLUMN first_name VARCHAR(100),
    ADD COLUMN last_name VARCHAR(100),
    ADD COLUMN email VARCHAR(254),
    ADD COLUMN job_title VARCHAR(120),
    ADD COLUMN hire_date DATE;

CREATE UNIQUE INDEX uq_employee_email_ci
    ON employee (LOWER(email))
    WHERE email IS NOT NULL;

ALTER TABLE coach_assignments
    ADD COLUMN created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN ended_at TIMESTAMPTZ;

ALTER TABLE coach_assignments
    DROP CONSTRAINT uq_coach_assignments_subjects;

DROP INDEX IF EXISTS uq_coach_assignments_subjects;

CREATE UNIQUE INDEX uq_coach_assignments_active_subjects
    ON coach_assignments (LOWER(coach_subject), LOWER(member_subject))
    WHERE ended_at IS NULL;
